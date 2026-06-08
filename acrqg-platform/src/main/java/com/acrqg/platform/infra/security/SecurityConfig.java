package com.acrqg.platform.infra.security;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.util.JsonUtils;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security 配置（无状态 API 服务）。
 *
 * <p>核心策略：
 * <ul>
 *   <li>禁用 CSRF：纯 JSON API + JWT 鉴权，无 cookie session，CSRF 不适用。</li>
 *   <li>禁用默认表单登录 / HTTP Basic / 默认 logout：登录 / 登出走自有
 *       {@code /api/v1/auth/*} 控制器（B1-A 实现）。</li>
 *   <li>{@link SessionCreationPolicy#STATELESS}：不创建 HttpSession。</li>
 *   <li>白名单 path 与 {@link JwtAuthFilter#WHITELIST} 一致。</li>
 *   <li>{@link JwtAuthFilter} 注册在 {@link UsernamePasswordAuthenticationFilter}
 *       之前，覆盖所有非白名单请求。</li>
 *   <li>未登录访问受保护接口 → 401 + {@link ErrorCode#AUTH_INVALID_TOKEN}；
 *       已登录但权限不足 → 403 + {@link ErrorCode#PERMISSION_DENIED}。</li>
 *   <li>{@link #passwordEncoder()} 暴露 BCrypt（cost=10），供 B1-A 用户登录、用户
 *       创建处使用。</li>
 * </ul>
 *
 * <p>{@link EnableMethodSecurity}({@code prePostEnabled=true})：开启
 * {@code @PreAuthorize} / {@code @PostAuthorize} 方法级注解；与 B0-A.7 的
 * {@code @RequirePermission} 切面互补，前者基于 SpEL，后者基于自定义注解。
 *
 * <p>Covers: R1.1, R1.4, R23.1。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /** 与 {@code JwtAuthFilter.WHITELIST} 保持一致，避免行为漂移。 */
    private static final String[] PERMIT_ALL = new String[] {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/csrf",
            "/api/v1/webhooks/**",
            "/health",
            "/metrics",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error",
            "/favicon.ico"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .ignoringRequestMatchers(
                                new AntPathRequestMatcher("/api/v1/auth/login"),
                                new AntPathRequestMatcher("/api/v1/auth/csrf"),
                                new AntPathRequestMatcher("/api/v1/webhooks/**")))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_ALL).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler()))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookiePath("/");
        return repository;
    }

    /** BCrypt cost=10；与 design.md §3.1 选型一致。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * 未鉴权时（{@link AuthenticationException}）的统一响应：401 + AUTH_INVALID_TOKEN。
     *
     * <p>实际生产中绝大多数未鉴权请求会被 {@link JwtAuthFilter} 提前拒绝；该
     * EntryPoint 仅覆盖 anonymous principal 直接到达 authorization 阶段的边界场景
     * （例如 actuator 之外的少数白名单未覆盖路径）。
     */
    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, authException) -> writeStatus(response, ErrorCode.AUTH_INVALID_TOKEN);
    }

    /**
     * 已鉴权但权限不足（{@link AccessDeniedException}）的统一响应：403 + PERMISSION_DENIED。
     */
    private AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeStatus(response, ErrorCode.PERMISSION_DENIED);
    }

    /** 与 {@link JwtAuthFilter#writeUnauthorized} 行为一致；写出统一 ApiResponse JSON。 */
    private static void writeStatus(HttpServletResponse response, ErrorCode code) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.failure(code, code.getMessage(), null);
        response.getWriter().write(JsonUtils.toJson(body));
        response.getWriter().flush();
    }
}
