package com.acrqg.platform.report.dto;

import java.util.Collections;
import java.util.List;

/**
 * 评审报告聚合视图（design.md §6.8 / R16.1）。
 *
 * <p>{@code GET /api/v1/review-tasks/{id}/report} 的返回负载，包含 R16.1 要求的
 * 四个核心字段：
 * <ul>
 *   <li>{@link #taskOverview} —— 任务概览（taskNo / PR / commit / status / score）；</li>
 *   <li>{@link #gateResultSummary} —— 门禁判定结果摘要；任务尚未走完 GATE_EVALUATING
 *       阶段时为 {@code null}（不阻断报告查询）；</li>
 *   <li>{@link #issueCounts} —— 按 {@code (severity, source)} 二维聚合的问题数
 *       （已排除 FALSE_POSITIVE）；</li>
 *   <li>{@link #aiAvailability} —— AI 服务在本任务内是否可用，来源优先于
 *       {@code gate_result.ai_available}，回退到 {@code review_task.ai_available}。</li>
 * </ul>
 *
 * <p>Covers: R16.1。
 *
 * @param taskOverview      任务概览，非空
 * @param gateResultSummary 门禁判定结果摘要；尚未生成时为 {@code null}
 * @param issueCounts       问题二维聚合列表；空列表表示没有有效问题
 * @param aiAvailability    AI 服务可用性快照
 */
public record ReviewReportDTO(
        TaskOverviewDTO taskOverview,
        GateResultSummaryDTO gateResultSummary,
        List<IssueCountAggDTO> issueCounts,
        boolean aiAvailability
) {

    public ReviewReportDTO {
        issueCounts = (issueCounts == null) ? Collections.emptyList() : List.copyOf(issueCounts);
    }
}
