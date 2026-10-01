package com.zhiyu.modules.auth.service;

import com.zhiyu.modules.auth.dto.LoginDTO;
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
     */
    LoginVO login(LoginDTO dto);

    /**
     * 查询当前登录用户，对应 {@code GET /api/auth/me}。
     *
     * <p>用户身份取自 {@code UserContext}（由 JWT 过滤器写入），
     * 随后<b>按 userId 回库查询</b>，不信任 token 里的任何业务字段。
     * 用户已被删除（含逻辑删除）时按未登录处理，抛 1002。
     */
    UserVO currentUser();
}
