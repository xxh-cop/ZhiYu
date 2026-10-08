package com.zhiyu.config;

import com.zhiyu.security.JwtAuthFilter;
import com.zhiyu.security.JwtUtil;
import com.zhiyu.security.RestAccessDeniedHandler;
import com.zhiyu.security.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置：无状态 JWT 鉴权 + CORS + 401/403 JSON 出口。
 *
 * <p>要点：
 * <ul>
 *   <li>关闭 csrf / formLogin / httpBasic / logout —— 纯 token 鉴权的前后端分离形态，
 *       没有 session，也就没有 CSRF 面。</li>
 *   <li>{@code STATELESS}：不建 HttpSession，每个请求都靠 token 自证。</li>
 *   <li>放行清单只有注册与登录；其余一律 {@code authenticated()}，
 *       新增公开接口必须在这里显式放行（默认拒绝，避免"忘了加鉴权"）。</li>
 *   <li>{@code /api/admin/**} 要求 role ∈ {1,2,3}（AGENTS.md §3.1 / B7 口径）；
 *       role=0 的学生 → 403 + {@code Result(1003)}。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * 本地开发的跨域白名单（通配模式）。
     *
     * <p>使用 {@code allowedOriginPatterns} 支持端口通配符 {@code *}<br>
     * 生产环境部署时必须收紧为具体域名，不得使用通配。
     */
    private static final List<String> ALLOWED_ORIGIN_PATTERNS = List.of(
            "http://localhost:*",
            "http://127.0.0.1:*");

    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    /** 允许访问管理端的角色码（AGENTS.md §3.1：1 传承人 / 2 咨询师 / 3 管理员）。 */
    private static final String[] ADMIN_ROLES = {"ROLE_1", "ROLE_2", "ROLE_3"};

    private final JwtUtil jwtUtil;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 手工构造而非注入 Bean：见 JwtAuthFilter 的类注释（避免被 Servlet 容器重复注册）
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtUtil);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // SSE 异步 dispatch 时 SecurityContext 已被清除，
                        // 原始请求已通过鉴权，这里放行 ASYNC dispatch 避免重复鉴权失败
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        // CORS 预检请求不带 Authorization，必须放行，否则浏览器直接拿到 401
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        // 静态资源（上传的图片/视频）：公开访问，不需要鉴权
                        .requestMatchers("/uploads/**").permitAll()
                        // 心理援助热线：公开接口，未登录也能访问（登录页/危机干预页展示）
                        .requestMatchers(HttpMethod.GET, "/api/crisis/hotlines").permitAll()
                        // 审核手作项目：仅管理员（role=3）可操作
                        .requestMatchers(HttpMethod.PUT, "/api/admin/projects/*/audit").hasAuthority("ROLE_3")
                        // 用户管理敏感操作：仅管理员（role=3）
                        .requestMatchers(HttpMethod.PUT, "/api/admin/users/*/status").hasAuthority("ROLE_3")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/users/*/role").hasAuthority("ROLE_3")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/users/*").hasAuthority("ROLE_3")
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/users/*").hasAuthority("ROLE_3")
                        // AI 审计日志详情（含解密内容）：仅管理员（role=3）
                        .requestMatchers(HttpMethod.GET, "/api/admin/audit-logs/*").hasAuthority("ROLE_3")
                        // 传承人敏感操作：仅管理员（role=3）
                        .requestMatchers(HttpMethod.PUT, "/api/admin/inheritors/*/verify").hasAuthority("ROLE_3")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/inheritors/*").hasAuthority("ROLE_3")
                        // 作品敏感操作：仅管理员（role=3）
                        .requestMatchers(HttpMethod.PUT, "/api/admin/works/*/status").hasAuthority("ROLE_3")
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/works/*").hasAuthority("ROLE_3")
                        // 小组敏感操作：仅管理员（role=3）
                        .requestMatchers(HttpMethod.PUT, "/api/admin/groups/*/status").hasAuthority("ROLE_3")
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/groups/*").hasAuthority("ROLE_3")
                        // 传承人端接口：仅传承人（role=1）可访问
                        .requestMatchers("/api/inheritor/**").hasAuthority("ROLE_1")
                        .requestMatchers("/api/admin/**").hasAnyAuthority(ADMIN_ROLES)
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 跨域配置。
     *
     * <p>使用 {@code allowedOriginPatterns} 而非 {@code allowedOrigins}，
     * 因为 {@code allowCredentials(true)} 时浏览器禁止 {@code Origin: *}，
     * 而 {@code allowedOriginPatterns} 支持端口通配符 {@code *}。
     * 生产环境必须收紧白名单为具体域名。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
        config.setAllowedOrigins(null);
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
