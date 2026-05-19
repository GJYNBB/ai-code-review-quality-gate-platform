package com.acrqg.platform.infra.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求级 TraceId 过滤器。
 *
 * <p>职责：
 * <ol>
 *   <li>在每个 HTTP 请求最早期阶段（在 {@code JwtAuthFilter} 之前）执行；</li>
 *   <li>读取 {@code X-Request-Id} 头作为本次请求的链路追踪 ID；缺失、空白、超长或包含
 *       不安全字符时一律生成一个新的 {@link UUID}；</li>
 *   <li>把 traceId 写入 SLF4J {@link MDC}（键 {@value #MDC_KEY}），供
 *       {@code logback-spring.xml} 的 JSON encoder 与
 *       {@code ApiResponse#requestId} 字段读取（B0-A.3 已在
 *       {@code ApiResponse.currentRequestId()} 内通过 {@code MDC.get("traceId")}
 *       回填）；</li>
 *   <li>把 traceId 回写到响应头 {@code X-Request-Id}，让下游 / 客户端能据此关联日志；</li>
 *   <li>在 {@code finally} 块中清理 MDC，避免线程池复用导致串扰。</li>
 * </ol>
 *
 * <p>校验策略：传入的 {@code X-Request-Id} 值必须匹配
 * {@code ^[A-Za-z0-9_\-]{1,64}$}（仅允许字母、数字、下划线与短横线，长度 1..64）。
 * 非法值不会记录警告日志（避免被恶意请求灌满日志），而是直接丢弃并生成新的 UUID。
 * 这是防御性的：客户端可能传入包含换行 / 引号的值进而破坏 JSON 日志结构，或让
 * 同一个 traceId 被滥用于聚合不同会话的日志。
 *
 * <p>过滤器顺序：{@link Order @Order(HIGHEST_PRECEDENCE + 5)} 严格小于
 * {@code JwtAuthFilter} 的 {@code HIGHEST_PRECEDENCE + 10}，确保 traceId 在认证
 * 失败的 401 响应里也能正确出现在日志与响应体中。
 *
 * <p>Covers: R23.3 (敏感 traceId 注入；不泄漏敏感字段), R24.5 (按 traceId 串联请求链路)。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 客户端 / 反向代理可携带的请求头名。响应头同名回写以便客户端复用。 */
    static final String HEADER = "X-Request-Id";

    /** SLF4J MDC 键名；与 logback-spring.xml / ApiResponse 中读取处保持一致。 */
    static final String MDC_KEY = "traceId";

    /** 合法 traceId 的字符集与长度约束。 */
    private static final Pattern VALID_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(HEADER));
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 解析或生成 traceId。
     *
     * <p>规则：{@code null} / 空白 / 长度超限 / 不匹配 {@link #VALID_PATTERN} 时
     * 一律生成新的 UUID 字符串；否则原样返回入参。
     */
    private static String resolveTraceId(String headerValue) {
        if (headerValue == null) {
            return UUID.randomUUID().toString();
        }
        String trimmed = headerValue.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64 || !VALID_PATTERN.matcher(trimmed).matches()) {
            return UUID.randomUUID().toString();
        }
        return trimmed;
    }
}
