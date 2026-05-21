package com.acrqg.platform.report.dto;

/**
 * 代码问题二维聚合元素（design.md §6.8 / R16.1）。
 *
 * <p>{@link ReviewReportDTO#issueCounts()} 的元素：按
 * {@code (severity, source)} 聚合的问题数。Service 层执行
 * <pre>
 * SELECT severity, source, COUNT(*) AS cnt
 *   FROM code_issue
 *  WHERE task_id = ?
 *    AND status &lt;&gt; 'FALSE_POSITIVE'
 *  GROUP BY severity, source
 * </pre>
 * 后逐行映射为本 record。FALSE_POSITIVE 在统计中被排除，与
 * {@code MetricCollector} 计算 {@code critical_issue_count} 的语义保持一致
 * （R17.6）。
 *
 * <p>{@code count} 在 SQL 端由 {@code COUNT(*)} 产生，永远 {@code &gt;= 0}；
 * 不存在时该 (severity, source) 组合不会出现在返回列表中（前端需自行处理"该组合
 * 计数为 0"的展示）。
 *
 * <p>Covers: R16.1。
 *
 * @param severity 严重等级（CRITICAL / HIGH / MEDIUM / LOW / INFO）
 * @param source   来源（SAST / AI / MANUAL）
 * @param count    问题数（已排除 FALSE_POSITIVE）
 */
public record IssueCountAggDTO(
        String severity,
        String source,
        long count
) {
}
