package com.acrqg.platform.code_issue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * 问题分页查询入参（design.md §8.4 / R16.2 / R16.3）。
 *
 * <p>所有过滤字段为 {@code null} / 空集合 / 空串时表示不过滤。{@code page} 与
 * {@code pageSize} 必填，与平台分页约定一致（page 从 1 起）。
 *
 * <p>多值字段 {@code severity} / {@code status} 通过查询字符串重复参数提交：
 * {@code ?severity=CRITICAL&severity=HIGH&status=NEW&status=CONFIRMED}。
 *
 * <p>Covers: R16.2, R16.3。
 *
 * @param severity 严重等级集合过滤（CRITICAL / HIGH / MEDIUM / LOW / INFO）
 * @param status   状态集合过滤
 * @param source   来源精确过滤（SAST / AI / MANUAL）
 * @param filePath 文件路径模糊匹配（{@code ILIKE %filePath%}）
 * @param keyword  关键字（在 description / rule_code / file_path 多列模糊匹配）
 * @param page     页码，{@code >= 1}
 * @param pageSize 每页条数，{@code 1..100}
 */
public record IssueQuery(
        List<String> severity,
        List<String> status,
        String source,
        String filePath,
        String keyword,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize
) {

    /** 安全的页码：null / &lt;1 退化为 1。 */
    public int safePage() {
        return (page == null || page < 1) ? 1 : page;
    }

    /** 安全的每页条数：null / &lt;1 退化为 20；&gt;100 截断到 100。 */
    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
