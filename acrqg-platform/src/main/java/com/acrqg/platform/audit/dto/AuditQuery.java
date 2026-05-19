package com.acrqg.platform.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;

/**
 * 审计日志分页查询参数。
 *
 * <p>对齐 design.md §8.4：
 * <pre>
 * public record AuditQuery(String operator, String action,
 *                          OffsetDateTime startDate, OffsetDateTime endDate,
 *                          int page, int pageSize) {}
 * </pre>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code operator} —— 操作者用户名（精确匹配 {@code operator_username}）；可为 {@code null}。</li>
 *   <li>{@code action}   —— 动作名（精确匹配）；可为 {@code null}。</li>
 *   <li>{@code startDate} / {@code endDate} —— 创建时间区间（左闭右开）；任一可为 {@code null}。</li>
 *   <li>{@code page}     —— 页码，从 1 起。Bean Validation {@code @Min(1)}。</li>
 *   <li>{@code pageSize} —— 每页条数，{@code [1,100]}。</li>
 * </ul>
 *
 * <p>Spring MVC 通过 {@link org.springframework.web.bind.annotation.ModelAttribute}
 * 默认行为，用 {@code @RequestParam} 风格的 query string 绑定到 record。OffsetDateTime
 * 由 Spring 默认转换器解析 ISO-8601 字符串。
 *
 * <p>Covers: R22.2。
 */
@Schema(description = "审计日志分页查询参数（R22.2）")
public record AuditQuery(

        @Schema(description = "操作者用户名（精确匹配）", example = "admin")
        String operator,

        @Schema(description = "动作名（精确匹配）", example = "LOGIN_SUCCESS")
        String action,

        @Schema(description = "创建时间下限（含），ISO-8601 字符串", example = "2026-01-01T00:00:00+08:00")
        OffsetDateTime startDate,

        @Schema(description = "创建时间上限（不含），ISO-8601 字符串", example = "2026-12-31T23:59:59+08:00")
        OffsetDateTime endDate,

        @Schema(description = "页码，从 1 起", example = "1", defaultValue = "1")
        @Min(1) int page,

        @Schema(description = "每页条数 [1,100]", example = "20", defaultValue = "20")
        @Min(1) @Max(100) int pageSize
) {

    /** 兜底：当 page <= 0 时返回 1。 */
    public int safePage() {
        return page <= 0 ? 1 : page;
    }

    /** 兜底：当 pageSize <= 0 或 > 100 时返回 20。 */
    public int safePageSize() {
        if (pageSize <= 0 || pageSize > 100) {
            return 20;
        }
        return pageSize;
    }
}
