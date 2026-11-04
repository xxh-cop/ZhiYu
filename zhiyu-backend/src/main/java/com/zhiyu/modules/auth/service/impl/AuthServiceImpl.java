package com.zhiyu.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.common.util.PasswordUtil;
import com.zhiyu.modules.auth.dto.LoginDTO;
import com.zhiyu.modules.auth.dto.PasswordForgotDTO;
import com.zhiyu.modules.auth.dto.PasswordResetDTO;
import com.zhiyu.modules.auth.dto.RegisterDTO;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.enums.RoleEnum;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.auth.service.AuthService;
import com.zhiyu.modules.auth.service.LoginAttemptStore;
import com.zhiyu.modules.auth.service.PasswordResetChannel;
import com.zhiyu.modules.auth.service.PasswordResetTokenStore;
import com.zhiyu.modules.auth.vo.LoginVO;
import com.zhiyu.modules.auth.vo.UserVO;
import com.zhiyu.security.JwtUtil;
import com.zhiyu.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * {@link AuthService} 的实现：真实数据库 + BCrypt 校验 + 无状态 JWT 签发。
 *
 * <p>T0.3 起登录返回的 token 由 {@link JwtUtil} 签发（HS256，有效期见
 * {@code zhiyu.jwt.expire-hours}）；T0.2b 的 mock token 已删除。
 *
 * <p><b>隐私</b>：token 的 payload 是客户端可读的（Base64，非加密），
 * 故只放 userId 与 role，绝不放学校、手机号等个人信息。
 *
 * <p><b>账号安全（2026-11-03）</b>：登录连续失败锁定（按账号维度，{@link LoginAttemptStore}）；
 * 忘记密码走一次性重置令牌（{@link PasswordResetTokenStore} 只存哈希，
 * {@link PasswordResetChannel} 可配置，未配置时明确返回「该功能暂未开放」）。
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** 锁定提示：沿用 2001 错误码，不新造错误码，也不泄露账号是否存在。 */
    private static final String LOCKED_MESSAGE = "尝试次数过多，请稍后再试";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final LoginAttemptStore loginAttemptStore;
    private final PasswordResetTokenStore passwordResetTokenStore;
    @Nullable
    private final PasswordResetChannel passwordResetChannel;
    private final long resetTokenExpireSeconds;

    public AuthServiceImpl(UserMapper userMapper, JwtUtil jwtUtil,
                           LoginAttemptStore loginAttemptStore,
                           PasswordResetTokenStore passwordResetTokenStore,
                           @Nullable PasswordResetChannel passwordResetChannel,
                           @Value("${zhiyu.auth.password-reset.token-expire-seconds:1800}") long resetTokenExpireSeconds) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.loginAttemptStore = loginAttemptStore;
        this.passwordResetTokenStore = passwordResetTokenStore;
        this.passwordResetChannel = passwordResetChannel;
        this.resetTokenExpireSeconds = resetTokenExpireSeconds;
    }

    @Override
    public void register(RegisterDTO dto) {
        Long count = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.USER_EXISTS);
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setSchool(dto.getSchool());
        user.setRole(RoleEnum.STUDENT.getCode());
        user.setStatus(1);
        // AGENTS.md §5：注册默认 can_contact=0，联系用户必须由用户单独授权
        user.setCanContact(0);
        // 隐私政策默认未同意，需用户主动同意（M3-B1）
        user.setPrivacyAgreed(0);

        // 单表插入，依赖 MyBatis-Plus 自身的事务语义，暂不加 @Transactional；
        // 若后续扩展为多表写入（如同时建 group_member），再补事务注解。
        userMapper.insert(user);

        log.info("注册成功 userId={}, username={}", user.getId(), user.getUsername());
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        // 连续失败锁定按账号维度判定，先于凭据校验；
        // 不存在的账号同样计数、同样锁定，避免通过锁定差异探测账号是否注册。
        if (loginAttemptStore.lockedRemainingSeconds(dto.getUsername()) > 0) {
            throw lockedException();
        }

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername()));

        // 刻意不区分「用户不存在」与「密码错误」，统一返回 2001，避免暴露用户是否存在的旁路信息。
        if (user == null || !PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            loginAttemptStore.recordFailure(dto.getUsername());
            if (loginAttemptStore.lockedRemainingSeconds(dto.getUsername()) > 0) {
                throw lockedException();
            }
            throw new BusinessException(ResultCode.BAD_CREDENTIAL);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 登录成功：清零失败计数与锁定
        loginAttemptStore.reset(dto.getUsername());

        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.createToken(user.getId(), user.getRole()));
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        // 角色码数值（Integer），与 sql/schema.sql 的 user.role 一致
        vo.setRole(user.getRole());

        log.info("登录成功 userId={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole());
        return vo;
    }

    @Override
    public void requestPasswordReset(PasswordForgotDTO dto) {
        if (passwordResetChannel == null) {
            // 未配置发送通道：明确拒绝，不静默成功。不新造错误码，沿用 3001 业务失败。
            throw new BusinessException(ResultCode.BAD_STATE.getCode(), "忘记密码功能暂未开放");
        }

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername()));
        if (user == null) {
            // 不泄露账号是否存在：与成功路径行为一致（不发令牌、相同响应）
            log.info("密码重置申请：账号不存在，按受理路径返回（不发放令牌）");
            return;
        }

        String token = generateToken();
        // 只存哈希，明文只交给发送通道
        passwordResetTokenStore.save(PasswordResetTokenStore.sha256Hex(token),
                user.getId(), resetTokenExpireSeconds);
        passwordResetChannel.sendResetToken(user.getUsername(), token);
        log.info("密码重置令牌已签发 userId={}，{} 秒有效", user.getId(), resetTokenExpireSeconds);
    }

    @Override
    public void resetPassword(PasswordResetDTO dto) {
        Long userId = passwordResetTokenStore.consume(PasswordResetTokenStore.sha256Hex(dto.getToken()));
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            // 令牌无效 / 过期 / 已使用 / 对应账号已注销：同一提示，不区分细节
            throw new BusinessException(ResultCode.BAD_STATE.getCode(), "重置令牌无效或已过期，请重新申请");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 旧密码立即失效（哈希整体替换）
        user.setPassword(PasswordUtil.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        // 重置成功后清零该账号的失败计数与锁定，保证新密码立即可登录
        loginAttemptStore.reset(user.getUsername());
        log.info("密码重置成功 userId={}", user.getId());
    }

    @Override
    public UserVO currentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // 正常流程下 /api/auth/me 已由 SecurityConfig 要求认证，走不到这里；
            // 能走到说明鉴权链被绕过（例如该路径被误放行），宁可直接拒绝也不返回未鉴权数据。
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 按 userId 回库查询：token 里的字段只是「谁在请求」，不是「用户现在是什么样子」。
        // 逻辑删除的用户查不到（@TableLogic 自动附加 deleted=0），按未登录处理。
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("token 有效但用户不存在或已注销 userId={}", userId);
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        return toUserVO(user);
    }

    private static BusinessException lockedException() {
        return new BusinessException(ResultCode.BAD_CREDENTIAL.getCode(), LOCKED_MESSAGE);
    }

    /** 生成 192 位随机令牌（48 个十六进制字符）；明文只出现在发送通道与本方法调用链内。 */
    private static String generateToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setSchool(user.getSchool());
        vo.setIntro(user.getIntro());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPrivacyAgreed(user.getPrivacyAgreed());
        vo.setRole(user.getRole());
        return vo;
    }
}
