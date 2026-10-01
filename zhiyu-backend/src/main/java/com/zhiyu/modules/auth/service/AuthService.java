package com.zhiyu.modules.auth.service;

import com.zhiyu.modules.auth.dto.LoginDTO;
import com.zhiyu.modules.auth.dto.RegisterDTO;
import com.zhiyu.modules.auth.vo.LoginVO;

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
     * <p>T0.1c 固定返回 mock 值；T0.2 接数据库后，凭据不匹配应抛
     * {@code BusinessException}(2001)。
     */
    LoginVO login(LoginDTO dto);
}
