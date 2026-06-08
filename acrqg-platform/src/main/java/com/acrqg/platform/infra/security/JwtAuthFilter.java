package com.acrqg.platform.infra.security;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.util.JsonUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器（请求入口）。
 *
 * <p>职责：
 * <ol>
 *   <li>白名单豁免：登录、刷新、Webhook、健康检查、Actuator、OpenAPI / Swagger UI、
 *       静态错误资源等无需鉴权；CORS preflight ({@code OPTIONS}) 同样放行。</li>
 *   <li>从 {@code Authorization: Bearer <token>} 中提取 token，调用
 *       {@link JwtTokenProvider#tryParse(String)} 校验签名 / 过期。</li>
 *   <li>校验 {@code tokenType=ACCESS}（拒绝把 refresh token 当 access 使用）。</li>
 *   <li>可选：通过 {@code Predicate<String>}（B0-A.6 提供 bean 名为
 *       {@code jwtJtiBlacklist}）查询 jti 黑名单（禁用用户即时失效；R3.2）。</li>
 *   <li>设置 {@link SecurityContextHolder} 与 {@link CurrentUserHolder}；请求结束后
 *       在 {@code finally} 中清理两者，避免线程池复用串扰。</li>
 *   <li>任何鉴权失败：写 401 + 标准 {@link ApiResponse} JSON 体（错误码
 *       {@link ErrorCode#AUTH_INVALID_TOKEN}），不调用下游链路。</li>
 * </ol>
 *
 * <p>白名单 path 列表与 {@code SecurityConfig} 中的 {@code permitAll()} 列表保持一致；
 * 若任一处缺失或新增，请同步修改另一处以避免行为漂移。
 *
 * <p>Covers: R1.4, R7.1（Webhook 路径放行后由 WebhookController 自行验签），
 * R23.1（强制鉴权与统一错误响应）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    /** 白名单：与 {@code SecurityConfig.permitAll()} 一一对应。 */
    private static final List<RequestMatcher> WHITELIST = List.of(
            new AntPathRequestMatcher("/api/v1/auth/login"),
            new AntPathRequestMatcher("/api/v1/auth/refresh"),
            new AntPathRequestMatcher("/api/v1/auth/csrf"),
            new AntPathRequestMatcher("/api/v1/webhooks/**"),
            new AntPathRequestMatcher("/health"),
            new AntPathRequestMatcher("/metrics"),
            new AntPathRequestMatcher("/actuator/**"),
            new AntPathRequestMatcher("/v3/api-docs/**"),
            new AntPathRequestMatcher("/swagger-ui/**"),
            new AntPathRequestMatcher("/swagger-ui.html"),
            new AntPathRequestMatcher("/error"),
            new AntPathRequestMatcher("/favicon.ico")
    );

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider provider;
    /**
     * 可选 jti 黑名单 SPI。B0-A.6 将以 {@code @Bean Predicate<String> jwtJtiBlacklist}
     * 暴露真实实现；本任务通过 {@link ObjectProvider} 软引用以保持自洽。
     *
     * <p>TODO B0-A.6: JwtBlacklist 实现落地后通过 bean 名 {@code jwtJtiBlacklist} 注入。
     */
    private final ObjectProvider<Predicate<String>> blacklistProvider;

    public JwtAuthFilter(JwtTokenProvider provider,
                        ObjectProvider<Predicate<String>> blacklistProvider) {
        this.provider = provider;
        this.blacklistProvider = blacklistProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 1) 白名单 / CORS preflight 直接放行
        if (isWhitelisted(request) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // 2) 提取 Authorization 头
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, ErrorCode.AUTH_INVALID_TOKEN);
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, ErrorCode.AUTH_INVALID_TOKEN);
            return;
        }

        // 3) 解析 + 类型校验
        Optional<Claims> parsed = provider.tryParse(token);
        if (parsed.isEmpty()) {
            writeUnauthorized(response, ErrorCode.AUTH_INVALID_TOKEN);
            return;
        }
        Claims claims = parsed.get();
        String tokenType = provider.extractTokenType(claims);
        if (!JwtTokenProvider.TOKEN_TYPE_ACCESS.equals(tokenType)) {
            writeUnauthorized(response, ErrorCode.AUTH_INVALID_TOKEN);
            return;
        }

        // 4) jti 黑名单（可选；B0-A.6 才会真正提供 bean）
        String jti = provider.extractJti(claims);
        Predicate<String> blacklist = blacklistProvider.getIfAvailable();
        if (blacklist != null && jti != null && blacklist.test(jti)) {
            writeUnauthorized(response, ErrorCode.AUTH_INVALID_TOKEN);
            return;
        }

        // 5) 设置安全上下文，调用下游
        try {
            Authentication auth = provider.toAuthentication(claims);
            SecurityContextHolder.getContext().setAuthentication(auth);
            if (auth.getPrincipal() instanceof AuthenticatedUser au) {
                CurrentUserHolder.set(au);
            }
            chain.doFilter(request, response);
        } finally {
            // 防止线程池复用导致的上下文串扰
            SecurityContextHolder.clearContext();
            CurrentUserHolder.clear();
        }
    }

    private boolean isWhitelisted(HttpServletRequest request) {
        for (RequestMatcher m : WHITELIST) {
            if (m.matches(request)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 以标准 {@link ApiResponse} JSON 体写出 401 / 403 响应。
     *
     * <p>HTTP 状态来自 {@link ErrorCode#getHttpStatus()}；不写 {@code WWW-Authenticate}
     * 头（前端通过 code 字段识别 token 失效并触发刷新）。
     */
    private void writeUnauthorized(HttpServletResponse response, ErrorCode code) throws IOException {
        if (response.isCommitted()) {
            log.debug("response already committed, skip writing unauthorized body");
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
