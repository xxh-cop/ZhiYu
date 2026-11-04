package com.zhiyu.modules.auth.service;

import com.zhiyu.modules.auth.dto.LoginDTO;
import com.zhiyu.modules.auth.dto.PasswordForgotDTO;
import com.zhiyu.modules.auth.dto.PasswordResetDTO;
import com.zhiyu.modules.auth.dto.RegisterDTO;
import com.zhiyu.modules.auth.vo.LoginVO;
import com.zhiyu.modules.auth.vo.UserVO;

/**
 * 账户与认证服务。
 *
 * <p>错误码取值见 {@code AGENTS.md} §3，不新造、不改数值。
 */
public interface AuthService {

    /**
     * 注册。
     *
     * <p>T0.1c 为硬编码空实现；T0.2 接数据库后，用户名重复应抛
     * {@code BusinessException}(2002)。
     */
    void register(RegisterDTO dto);

    /**
     * 登录。
     *
     * <p>凭据不匹配抛 {@code BusinessException}(2001)（不区分用户不存在与密码错误）；
     * 账号被禁用抛 1003。成功返回的 token 由 {@code JwtUtil} 签发。
     *
     * <p>连续失败锁定：同一账号连续失败达到阈值后锁定一段时间，
     * 锁定期间抛 2001（提示「尝试次数过多，请稍后再试」），不泄露账号是否存在。
     */
    LoginVO login(LoginDTO dto);

    /**
     * 申请密码重置令牌（忘记密码），对应 {@code POST /api/auth/password/forgot}。
     *
     * <p>无论账号是否存在都静默返回（不泄露注册状态）；
     * 未配置发送通道时抛 3001「该功能暂未开放」，不静默成功。
     * 令牌限时、一次性，服务端只存哈希。
     */
    void requestPasswordReset(PasswordForgotDTO dto);

    /**
     * 用重置令牌设置新密码，对应 {@code POST /api/auth/password/reset}。
     *
     * <p>令牌无效 / 过期 / 已使用抛 3001；账号不可用抛 1003。
     * 成功后旧密码立即失效，该账号登录失败计数与锁定清零。
     */
    void resetPassword(PasswordResetDTO dto);

    /**
     * 查询当前登录用户，对应 {@code GET /api/auth/me}。
     *
     * <p>用户身份取自 {@code UserContext}（由 JWT 过滤器写入），
     * 随后<b>按 userId 回库查询</b>，不信任 token 里的任何业务字段。
     * 用户已被删除（含逻辑删除）时按未登录处理，抛 1002。
     */
    UserVO currentUser();
}
