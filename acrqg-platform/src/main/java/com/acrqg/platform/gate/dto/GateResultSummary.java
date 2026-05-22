package com.acrqg.platform.gate.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code gate_result.summary} JSON 列的对象映射。
 *
 * <p>序列化后落库为 JSONB，供报告页直读（R14.8）：
 * <pre>
 * {
 *   "failedRules":   [ { "metric": ..., "operator": ..., "threshold": ..., "severity": "BLOCKER", "actual": ..., "passed": false } ],
 *   "passedRules":   [ { ... "passed": true } ],
 *   "metricValues":  { "critical_issue_count": 0, "test_coverage": 80, ... },
 *   "aiAvailable":   true
 * }
 * </pre>
 *
 * <p>{@code metricValues} 把每个 metric 的实际采集值汇总，便于前端在不展开规则明细
 * 的概览卡片上直接展示数值（design §16.5 报告页设计）。键顺序按规则迭代顺序保留，
 * 因此使用 {@link LinkedHashMap}。
 *
 * <p>{@code @JsonInclude(NON_NULL)}：可选字段不出现在 JSON 中，减小 summary 长度。
 *
 * <p>Covers: R12.5, R14.5, R14.8。
 *
 * @param failedRules  失败规则列表（按聚合顺序）
 * @param passedRules  通过规则列表
 * @param metricValues metric → actual 值映射；可空
 * @param aiAvailable  AI 服务可用性快照
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GateResultSummary(
        List<RuleEvalDTO> failedRules,
        List<RuleEvalDTO> passedRules,
        Map<String, BigDecimal> metricValues,
        boolean aiAvailable
) {

    /** 工厂方法：构造一个空 summary（无规则配置时使用）。 */
    public static GateResultSummary empty(boolean aiAvailable) {
        return new GateResultSummary(List.of(), List.of(), new LinkedHashMap<>(), aiAvailable);
    }
}
