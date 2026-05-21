package com.acrqg.platform.gate.engine;

import java.math.BigDecimal;

/**
 * 运算符比较器（design.md §10.3）。
 *
 * <p>统一以 {@link BigDecimal} 入参以保证精度（避免 {@code double} 误差），由
 * {@link com.acrqg.platform.gate.engine.GateRuleEngine} 在评估每条规则时调用。
 *
 * <p>支持的 operator（取值与 {@code gate_rule.operator} CHECK 约束一致）：
 * <ul>
 *   <li>{@code <=} —— 实际值 ≤ 阈值时通过；</li>
 *   <li>{@code >=} —— 实际值 ≥ 阈值时通过；</li>
 *   <li>{@code <}  —— 实际值 &lt; 阈值时通过；</li>
 *   <li>{@code >}  —— 实际值 &gt; 阈值时通过；</li>
 *   <li>{@code ==} —— 实际值 = 阈值时通过；</li>
 *   <li>{@code !=} —— 实际值 ≠ 阈值时通过。</li>
 * </ul>
 *
 * <p>同时也接受语义等价的别名 {@code GT / GTE / LT / LTE / EQ / NEQ}，便于未来切换
 * 配置形式而无需改造引擎。
 *
 * <p>非法 operator 抛 {@link com.acrqg.platform.common.exception.BusinessException}
 * （{@link com.acrqg.platform.common.api.ErrorCode#GATE_RULE_INVALID}）。
 *
 * <p>Covers: R13.2, R14.2。
 */
public interface OperatorEvaluator {

    /**
     * 比较 {@code actual op threshold} 是否成立。
     *
     * @param actual    实际值，非空
     * @param operator  运算符
     * @param threshold 阈值，非空
     * @return 比较结果
     */
    boolean evaluate(BigDecimal actual, String operator, BigDecimal threshold);
}
