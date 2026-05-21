package com.acrqg.platform.gate.domain;

import java.math.BigDecimal;

/**
 * 单条门禁规则评估结果。
 *
 * <p>由 {@code GateRuleEngine} 在 {@code evaluate(taskId)} 内部对每条 enabled 规则
 * 生成；之后聚合得到 {@link GateResultStatus} 与 score，并把 {@code RuleEval}
 * 列表序列化到 {@code gate_result.summary} 的 {@code failedRules} / {@code passedRules}
 * 字段（design §7.2 + R14.8）。
 *
 * <p>{@code actual} 为 MetricCollector 采集到的实际值（已转 {@link BigDecimal}）；
 * {@code threshold} 为门禁规则中配置的阈值字符串原样保留，便于前端按用户配置
 * 的形式展示（如 {@code "70"} / {@code "0.05"}）。
 *
 * <p>Covers: R14.2, R14.3, R14.4, R14.8。
 *
 * @param metric    指标名（{@code critical_issue_count} 等 6 选 1）
 * @param operator  比较运算符（6 选 1）
 * @param threshold 阈值字符串（原样）
 * @param severity  失败级别（{@code BLOCKER} / {@code WARN}）
 * @param actual    采集到的实际值
 * @param passed    {@code true} 表示规则通过；{@code false} 表示失败
 */
public record RuleEval(
        String metric,
        String operator,
        String threshold,
        String severity,
        BigDecimal actual,
        boolean passed
) {
}
