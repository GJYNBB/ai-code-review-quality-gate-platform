package com.acrqg.platform.report.dto;

import com.acrqg.platform.gate.dto.RuleEvalDTO;
import java.util.Collections;
import java.util.List;

/**
 * 报告页门禁判定结果摘要（design.md §6.8 / R16.1 / R14.8）。
 *
 * <p>{@link ReviewReportDTO#gateResultSummary()} 字段的承载体；从
 * {@link com.acrqg.platform.gate.dto.GateResultDTO} 抽取报告页关心的字段：
 * <ul>
 *   <li>{@code status} —— PASSED / FAILED / WAIVED / PENDING；</li>
 *   <li>{@code score} —— 质量评分 [0,100]（可空）；</li>
 *   <li>{@code aiRiskScore} —— AI 风险分快照（可空）；</li>
 *   <li>{@code aiAvailable} —— AI 服务可用性快照；</li>
 *   <li>{@code failedRules} / {@code passedRules} —— 规则明细列表（来自
 *       {@link com.acrqg.platform.gate.dto.GateResultSummary}）。</li>
 * </ul>
 *
 * <p>该 DTO 与 {@code GateResultDTO} 的差异：
 * <ul>
 *   <li>不再包装 {@code GateResultSummary}（嵌套对象），而是把规则列表平铺，
 *       便于前端列表渲染；</li>
 *   <li>不携带 {@code id} / {@code taskId} / {@code createdAt} / {@code updatedAt}
 *       这类持久层元数据，避免暴露非必要字段；</li>
 *   <li>当 {@code gate_result} 表中尚无记录时（任务未走完 GATE_EVALUATING 阶段），
 *       {@link ReviewReportDTO} 中本字段为 {@code null}，而非全 0/空集合的占位对象。</li>
 * </ul>
 *
 * <p>Covers: R14.8, R16.1。
 *
 * @param status      判定状态字符串
 * @param score       质量评分；可空
 * @param aiRiskScore AI 风险分；可空
 * @param aiAvailable AI 可用性快照
 * @param failedRules 失败规则明细；非空
 * @param passedRules 通过规则明细；非空
 */
public record GateResultSummaryDTO(
        String status,
        Integer score,
        Integer aiRiskScore,
        boolean aiAvailable,
        List<RuleEvalDTO> failedRules,
        List<RuleEvalDTO> passedRules
) {

    public GateResultSummaryDTO {
        failedRules = (failedRules == null) ? Collections.emptyList() : List.copyOf(failedRules);
        passedRules = (passedRules == null) ? Collections.emptyList() : List.copyOf(passedRules);
    }
}
