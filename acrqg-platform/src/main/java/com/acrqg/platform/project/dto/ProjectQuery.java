package com.acrqg.platform.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 项目分页查询条件。
 *
 * <p>对应 {@code GET /api/v1/projects?keyword=&page=&pageSize=}。
 * 所有字段都允许 {@code null} / 0：
 * <ul>
 *   <li>{@code keyword}：模糊匹配 {@code name} 与 {@code description}（包含匹配）；
 *       为空时不过滤；</li>
 *   <li>{@code page} / {@code pageSize}：约束 1..100；通过
 *       {@link #safePage()} / {@link #safePageSize()} 提供兜底，
 *       让 Service 层无需重复判断 0 / 负值。</li>
 * </ul>
 *
 * <p>Covers: R4.3。
 *
 * @param keyword   关键字（可空）
 * @param page      页码（默认 1，最大无上限但 Service 受 maxLimit=1000 约束）
 * @param pageSize  每页条数（默认 20，范围 1..100）
 */
public record ProjectQuery(
        String keyword,
        @Min(value = 1, message = "page 必须 >= 1") int page,
        @Min(value = 1, message = "pageSize 必须 >= 1")
        @Max(value = 100, message = "pageSize 不能超过 100") int pageSize
) {

    /** 默认页码。 */
    public static final int DEFAULT_PAGE = 1;
    /** 默认页大小。 */
    public static final int DEFAULT_PAGE_SIZE = 20;
    /** 页大小上限（与 design.md §8.4 一致）。 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 兜底返回有效页码：当传入值 <= 0 时返回 {@link #DEFAULT_PAGE}。 */
    public int safePage() {
        return page <= 0 ? DEFAULT_PAGE : page;
    }

    /** 兜底返回有效页大小：当传入值 <= 0 时返回 {@link #DEFAULT_PAGE_SIZE}；超出上限按上限返回。 */
    public int safePageSize() {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
