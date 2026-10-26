package com.zhiyu.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析 {@code Authorization: Bearer <token>}，通过则写入
 * {@link SecurityContextHolder} 与 {@link UserContext}。
 *
 * <p><b>本类刻意不是 Spring Bean</b>：Spring Boot 会把容器里所有 {@code Filter} 类型的 Bean
 * 额外注册到 Servlet 容器，导致它在 Security 链之外再执行一遍。
 * 因此由 {@code SecurityConfig} 手工 {@code new} 出来，只挂在 Security 链上。
 *
 * <p><b>失败时不在这里写响应</b>：token 缺失或非法只是「不认证」，随后由
 * {@code AuthorizationFilter} 判定该请求需要认证，交给
 * {@link RestAuthenticationEntryPoint} 统一输出 401 + {@code Result(1002)}。
 * 这样做的好处是：带着过期 token 去调 {@code /api/auth/login} 仍能正常登录，
 * 不会被一个陈旧的请求头误伤。
 */
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /** Spring Security 的角色前缀约定；角色码直接拼在后面（role=0 → ROLE_0）。 */
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtUtil jwtUtil;
    private final com.zhiyu.modules.auth.mapper.UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                String token = header.substring(BEARER_PREFIX.length()).trim();
                LoginUser loginUser = jwtUtil.parseToken(token);
                if (loginUser != null) {
                    var current = userMapper.selectById(loginUser.getUserId());
                    if (current != null && Integer.valueOf(1).equals(current.getStatus())) {
                        authenticate(new LoginUser(current.getId(), current.getRole()));
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 必须清理：Tomcat 复用线程，残留会把上一个请求的身份带给下一个请求
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(LoginUser loginUser) {
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + loginUser.getRole()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserContext.set(loginUser);
    }
}
