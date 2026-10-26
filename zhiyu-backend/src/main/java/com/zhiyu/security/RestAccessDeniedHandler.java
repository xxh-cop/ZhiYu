package com.zhiyu.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.result.Result;
import com.zhiyu.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 权限不足出口：返回 <b>HTTP 403 + body {@code Result(1003)}</b>。
 *
 * <p>触发场景：<b>已认证</b>但角色不够。例如学生（role=0）访问
 * {@code /api/admin/**}（默认仅 role=3），或咨询师（role=2）访问非关怀任务的管理端接口。
 *
 * <p>未认证（匿名）访问受保护接口走的是 {@link RestAuthenticationEntryPoint}（401），
 * 不会走到这里 —— 这是 {@code ExceptionTranslationFilter} 的标准行为。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("权限不足：{} {} role={}",
                request.getMethod(), request.getRequestURI(), UserContext.getRole());
        SecurityResponseWriter.write(response, objectMapper,
                HttpStatus.FORBIDDEN.value(), Result.error(ResultCode.FORBIDDEN));
    }
}
