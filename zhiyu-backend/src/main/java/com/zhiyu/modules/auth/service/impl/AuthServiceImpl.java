package com.zhiyu.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.common.result.ResultCode;
import com.zhiyu.common.util.PasswordUtil;
import com.zhiyu.modules.auth.dto.LoginDTO;
import com.zhiyu.modules.auth.dto.RegisterDTO;
import com.zhiyu.modules.auth.entity.User;
import com.zhiyu.modules.auth.enums.RoleEnum;
import com.zhiyu.modules.auth.mapper.UserMapper;
import com.zhiyu.modules.auth.service.AuthService;
import com.zhiyu.modules.auth.vo.LoginVO;
import com.zhiyu.modules.auth.vo.UserVO;
import com.zhiyu.security.JwtUtil;
import com.zhiyu.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@link AuthService} 的实现：真实数据库 + BCrypt 校验 + 无状态 JWT 签发。
 *
 * <p>T0.3 起登录返回的 token 由 {@link JwtUtil} 签发（HS256，有效期见
 * {@code zhiyu.jwt.expire-hours}）；T0.2b 的 mock token 已删除。
 *
 * <p><b>隐私</b>：token 的 payload 是客户端可读的（Base64，非加密），
 * 故只放 userId 与 role，绝不放学校、手机号等个人信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

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
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername()));

        // 刻意不区分「用户不存在」与「密码错误」，统一返回 2001，避免暴露用户是否存在的旁路信息。
        if (user == null || !PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_CREDENTIAL);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

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

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setSchool(user.getSchool());
        vo.setPrivacyAgreed(user.getPrivacyAgreed());
        vo.setRole(user.getRole());
        return vo;
    }
}
