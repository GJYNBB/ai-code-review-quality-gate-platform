package com.acrqg.platform.gate.domain;

/**
 * 门禁判定结果状态字典（design.md §7.4 / R14.3 / R14.4 / R15.3）。
 *
 * <p>对应 {@code gate_result.status} 列的 CHECK 约束取值：
 * <ul>
 *   <li>{@link #PENDING} —— 过渡态，B3-F GateRuleEngine 不直接落库；预留给后续
 *       异步评估或 B4-E 豁免审批中暂未确认的中间状态。</li>
 *   <li>{@link #PASSED}  —— 全部 BLOCKER 规则通过（R14.4）。</li>
 *   <li>{@link #FAILED}  —— 任一 BLOCKER 规则失败（R14.3）。</li>
 *   <li>{@link #WAIVED}  —— 由 B4-E 豁免审批写入（R15.3）。</li>
 * </ul>
 *
 * <p>本任务（B3-F）只产生 {@link #PASSED} / {@link #FAILED} 两种结果。
 *
 * <p>Covers: R14.3, R14.4, R15.3。
 */
public enum GateResultStatus {
    PENDING,
    PASSED,
    FAILED,
    WAIVED
}
