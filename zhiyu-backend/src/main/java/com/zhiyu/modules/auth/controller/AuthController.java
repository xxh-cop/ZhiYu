package com.zhiyu.modules.auth.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.auth.dto.LoginDTO;
import com.zhiyu.modules.auth.dto.RegisterDTO;
import com.zhiyu.modules.auth.service.AuthService;
import com.zhiyu.modules.auth.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账户与认证接口，对应 {@code openapi/openapi.yaml} 的 {@code /api/auth/**}。
 *
 * <p>T0.1c 只验证 Controller → Service → Result 链路；
 * {@code GET /api/auth/me} 依赖 JWT，等 T0.3 再加。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册，对应 {@code POST /api/auth/register}。 */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return Result.success();
    }

    /** 登录，对应 {@code POST /api/auth/login}。 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }
}
