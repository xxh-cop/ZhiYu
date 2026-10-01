package com.zhiyu.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 过滤器链里的 JSON 出口。
 *
 * <p><b>为什么需要它</b>：401/403 发生在 DispatcherServlet <b>之前</b>，
 * 不会经过 {@code GlobalExceptionHandler}，所以必须在这里手写响应体；
 * 而契约（AGENTS.md §3）要求这两个状态也返回 {@code Result}，
 * 即 {@code {"code":1002,"message":"未登录或登录已过期","data":null}}，
 * <b>不能是空体</b>。
 *
 * <p>统一在这里设置编码：Content-Type 与 characterEncoding 都必须在
 * {@code getWriter()} 之前设置，否则中文提示会按 ISO-8859-1 输出成乱码。
 */
final class SecurityResponseWriter {

    private SecurityResponseWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, int status, Result<?> body)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
        response.getWriter().flush();
    }
}
