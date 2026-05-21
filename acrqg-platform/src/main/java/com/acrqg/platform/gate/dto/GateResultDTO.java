package com.acrqg.platform.gate.dto;

import java.time.OffsetDateTime;

/**
 * 门禁判定结果对外视图（design.md §8.4）。
 *
 * <p>由 {@code GateResultService.get(taskId)} / {@code GateRuleEngine.evaluate(taskId)}
 * 返回，通过 {@code ApiResponse<GateResultDTO>} 包装。
 *
 * <p>{@link #summary} 已经被反序列化为 {@link GateResultSummary} 结构体，前端可直接
 * 访问 {@code summary.failedRules} / {@code summary.passedRules}，无需再解 JSON。
 *
 * <p>Covers: R14.3, R14.4, R14.5, R14.8, R16.1。
 *
 * @param id           主键
 * @param taskId       关联评审任务主键
 * @param status       判定状态字符串（{@code PASSED} / {@code FAILED} / {@code WAIVED} / {@code PENDING}）
 * @param score        质量评分 [0,100]，可空
 * @param aiRiskScore  AI 风险分快照，可空
 * @param aiAvailable  AI 服务可用性
 * @param summary      规则明细 + metric 值
 * @param createdAt    首次写入时间
 * @param updatedAt    最近更新时间
 */
public record GateResultDTO(
        Long id,
        Long taskId,
        String status,
        Integer score,
        Integer aiRiskScore,
        boolean aiAvailable,
        GateResultSummary summary,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
