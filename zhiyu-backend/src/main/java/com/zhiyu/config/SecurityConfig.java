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
     * 本地开发的跨域白名单。
     *
     * <p>端口分配：5173 是学生端 UniApp H5（T0.5a）的 Vite 开发端口，
     * 5174 是管理端 Vue3（T0.6a）的 Vite 开发端口；8080 预留给后端自带页面的本地联调。
     */
    private static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:5173", "http://127.0.0.1:5173",
            "http://localhost:5174", "http://127.0.0.1:5174",
            "http://localhost:5175", "http://127.0.0.1:5175",
            "http://localhost:8080", "http://127.0.0.1:8080");

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
     * <p>{@code allowCredentials(true)} 时浏览器禁止 {@code Origin: *}，
     * 所以这里必须逐个列出合法来源，不能用通配符。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(ALLOWED_ORIGINS);
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
