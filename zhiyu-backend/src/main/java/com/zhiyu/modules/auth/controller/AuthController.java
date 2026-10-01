package com.zhiyu.modules.auth.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.auth.dto.LoginDTO;
import com.zhiyu.modules.auth.dto.RegisterDTO;
import com.zhiyu.modules.auth.service.AuthService;
import com.zhiyu.modules.auth.vo.LoginVO;
import com.zhiyu.modules.auth.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账户与认证接口，对应 {@code openapi/openapi.yaml} 的 {@code /api/auth/**}。
 *
 * <p>{@code /register} 与 {@code /login} 在 {@code SecurityConfig} 中显式放行；
 * {@code /me} 需要携带 {@code Authorization: Bearer <token>}，
 * 未登录/非法 token 由安全链直接返回 HTTP 401 + {@code Result(1002)}
 * （不经过本类，也不经过 GlobalExceptionHandler）。
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

    /** 当前登录用户，对应 {@code GET /api/auth/me}。 */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.currentUser());
    }
}
