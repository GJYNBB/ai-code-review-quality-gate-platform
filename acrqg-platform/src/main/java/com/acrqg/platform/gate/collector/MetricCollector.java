package com.acrqg.platform.gate.collector;

import java.math.BigDecimal;

/**
 * 度量采集器（design.md §10.2）。
 *
 * <p>每个实现负责一个 metric 的实际值采集，由
 * {@link com.acrqg.platform.gate.engine.GateRuleEngine} 在评估时按
 * {@link #metric()} 路由分发。统一返回 {@link BigDecimal}，避免 {@code double}
 * 精度误差并对齐 {@link com.acrqg.platform.gate.engine.OperatorEvaluator} 的
 * 比较精度。
 *
 * <p>实现规范：
 * <ul>
 *   <li>{@link #metric()} 返回的字面量必须与 {@code gate_rule.metric} CHECK 约束
 *       的 6 个枚举值之一保持<b>一致</b>（{@code critical_issue_count} /
 *       {@code security_issue_count} / {@code test_coverage} /
 *       {@code duplicate_rate} / {@code ai_risk_score} / {@code new_issue_count}）；</li>
 *   <li>{@link #collect(MetricContext)} 必须是只读的（无 side effect），
 *       便于 GateRuleEngine 在重新评估时幂等执行；</li>
 *   <li>异常应尽量内部捕获并退化为合理的兜底值（如 0），并通过 TaskLogger 写
 *       WARN 级 task_log——避免单 metric 故障阻塞整个门禁判定（R14 整体可用性）。</li>
 * </ul>
 *
 * <p>Covers: R14.1, R12.6, R17.6。
 */
public interface MetricCollector {

    /** 采集器对应的 metric 名（必须与 gate_rule.metric 取值一致）。 */
    String metric();

    /**
     * 采集 metric 的实际值。
     *
     * @param ctx 上下文（task / project）
     * @return 实际值；不应返回 {@code null}
     */
    BigDecimal collect(MetricContext ctx);
}
