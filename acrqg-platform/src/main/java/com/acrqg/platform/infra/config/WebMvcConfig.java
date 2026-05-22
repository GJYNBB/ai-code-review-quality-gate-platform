package com.acrqg.platform.infra.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：CORS 白名单。
 *
 * <p>规则：
 * <ul>
 *   <li>{@code app.cors.allowed-origins} 为空、值为 {@code *} 或包含 {@code *} 时
 *       视为"全放开"模式（dev / 本地联调），使用
 *       {@link CorsRegistration#allowedOriginPatterns(String...)}（Spring 5+ API）
 *       并注册到 {@code /api/**} 与 {@code /webhooks/**}。这样既允许任意来源，
 *       又能与 {@code allowCredentials(true)} 共存（Spring Security 拒绝
 *       {@code allowedOrigins("*") + allowCredentials(true)} 组合）。</li>
 *   <li>否则按逗号切分、去空、修剪后注册到 {@code allowedOrigins(...)}，仅作用于
 *       {@code /api/**}（生产环境 Webhook 通常由代码平台直连，不需要受 CORS 控制；
 *       浏览器也不会 CORS 调用 Webhook）。</li>
 * </ul>
 *
 * <p>所有注册项统一允许的方法集为 {@code GET / POST / PUT / PATCH / DELETE / OPTIONS}，
 * 允许全部请求头，{@code allowCredentials=true}（前端通过 Authorization Bearer
 * 携带 JWT，无 cookie session，但保留以便未来可能的 cookie 联动），最大缓存 3600 秒。
 *
 * <p>Covers: R23.3（统一受控的跨域策略，避免敏感字段被未授权前端读取）。
 *
 * @see com.acrqg.platform.infra.security.SecurityConfig
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    private static final String[] HTTP_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"};
    private static final long MAX_AGE_SECONDS = 3600L;

    private final String allowedOrigins;

    public WebMvcConfig(@Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? "" : allowedOrigins.trim();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (isWildcard(allowedOrigins)) {
            // dev / 本地：允许任意来源（与 credentials 共存使用 allowedOriginPatterns）
            registerWildcard(registry, "/api/**");
            registerWildcard(registry, "/webhooks/**");
            log.info("CORS configured: wildcard origin patterns for /api/** and /webhooks/**");
            return;
        }

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (origins.isEmpty()) {
            // 极端情况：配置非空但全是逗号 / 空白，回退到 wildcard
            registerWildcard(registry, "/api/**");
            registerWildcard(registry, "/webhooks/**");
            log.warn("CORS allowed-origins='{}' parsed to empty list, falling back to wildcard", allowedOrigins);
            return;
        }

        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods(HTTP_METHODS)
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(MAX_AGE_SECONDS);
        log.info("CORS configured: explicit origins={} for /api/**", origins);
    }

    /**
     * 是否视为通配符模式：空字符串、纯 {@code *}、或包含 {@code *} 元素的逗号列表。
     */
    private static boolean isWildcard(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        for (String s : value.split(",")) {
            if ("*".equals(s.trim())) {
                return true;
            }
        }
        return false;
    }

    private static void registerWildcard(CorsRegistry registry, String pathPattern) {
        registry.addMapping(pathPattern)
                .allowedOriginPatterns("*")
                .allowedMethods(HTTP_METHODS)
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(MAX_AGE_SECONDS);
    }
}
