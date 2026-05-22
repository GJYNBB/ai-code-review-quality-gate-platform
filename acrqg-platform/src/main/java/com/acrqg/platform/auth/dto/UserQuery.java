package com.acrqg.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 用户分页查询参数（design.md §8.4）。
 *
 * <p>对应 {@code GET /api/v1/users}（仅 SYSTEM_ADMIN，R3.1）。
 *
 * <p>过滤策略：
 * <ul>
 *   <li>{@code keyword} —— 关键字模糊匹配 {@code username} 或 {@code email}
 *       （PostgreSQL {@code ILIKE %kw%}），任一命中即可。空 / null 不参与过滤。</li>
 *   <li>{@code status} —— 精确匹配 {@code "ENABLED"} / {@code "DISABLED"}（其它值忽略）。</li>
 *   <li>{@code role} —— 精确匹配某全局角色编码（如 {@code SYSTEM_ADMIN}）。
 *       通过 {@code user_role} JOIN {@code role} 实现。</li>
 *   <li>{@code page} / {@code pageSize} —— 范围 [1,100]；提供 {@link #safePage()} /
 *       {@link #safePageSize()} 兜底。</li>
 * </ul>
 *
 * <p>Covers: R3.1。
 */
@Schema(description = "用户分页查询参数（R3.1）")
public record UserQuery(

        @Schema(description = "关键字（模糊匹配 username/email）") String keyword,

        @Schema(description = "状态过滤：ENABLED / DISABLED；其它值忽略") String status,

        @Schema(description = "角色过滤（如 SYSTEM_ADMIN）") String role,

        @Schema(description = "页码，从 1 起", defaultValue = "1")
        @Min(value = 1, message = "page 必须 >= 1") int page,

        @Schema(description = "每页条数 [1,100]", defaultValue = "20")
        @Min(value = 1, message = "pageSize 必须 >= 1")
        @Max(value = 100, message = "pageSize 不能超过 100") int pageSize
) {

    /** 默认值常量。 */
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public int safePage() {
        return page <= 0 ? DEFAULT_PAGE : page;
    }

    public int safePageSize() {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
