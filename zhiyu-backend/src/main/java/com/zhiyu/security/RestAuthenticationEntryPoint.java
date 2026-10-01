package com.zhiyu.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.result.Result;
import com.zhiyu.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证出口：返回 <b>HTTP 401 + body {@code Result(1002)}</b>。
 *
 * <p>触发场景：未带 token、token 非法、token 过期、token 签名不匹配访问受保护接口。
 * 默认的 {@code Http403ForbiddenEntryPoint} 会返回空体，不符合契约，故整个替换掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("未认证访问被拒：{} {}（{}）",
                request.getMethod(), request.getRequestURI(), authException.getMessage());
        SecurityResponseWriter.write(response, objectMapper,
                HttpStatus.UNAUTHORIZED.value(), Result.error(ResultCode.UNAUTHORIZED));
    }
}
