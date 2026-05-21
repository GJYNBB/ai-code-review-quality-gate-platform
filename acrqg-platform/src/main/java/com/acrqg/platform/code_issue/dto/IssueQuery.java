package com.acrqg.platform.code_issue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 问题分页查询入参（design.md §8.4 / R16.2 / R16.3）。
 *
 * <p>所有过滤字段为 {@code null} / 空时表示不过滤。{@code page} 与 {@code pageSize}
 * 必填，与平台分页约定一致（page 从 1 起）。
 *
 * <p>Covers: R16.2, R16.3。
 *
 * @param severity 严重等级精确过滤（CRITICAL / HIGH / MEDIUM / LOW / INFO）
 * @param status   状态精确过滤
 * @param source   来源精确过滤（SAST / AI / MANUAL）
 * @param filePath 文件路径模糊匹配（{@code ILIKE %filePath%}）
 * @param page     页码，{@code >= 1}
 * @param pageSize 每页条数，{@code 1..100}
 */
public record IssueQuery(
        String severity,
        String status,
        String source,
        String filePath,
        @Min(1) int page,
        @Min(1) @Max(100) int pageSize
) {
}
