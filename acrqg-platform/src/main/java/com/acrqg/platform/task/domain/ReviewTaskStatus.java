package com.acrqg.platform.task.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * 评审任务状态字典（design.md §6.3.1 / §7.4 / R9.1）。
 *
 * <p>共 8 种状态：
 * <ol>
 *   <li>{@link #PENDING} —— 已入队，尚未开始执行。</li>
 *   <li>{@link #FETCHING_DIFF} —— 正在拉取 / 解析 diff（R10）。</li>
 *   <li>{@link #STATIC_SCANNING} —— 正在执行静态扫描（R11）。</li>
 *   <li>{@link #AI_REVIEWING} —— 正在调用 AI 评审（R12，含降级）。</li>
 *   <li>{@link #GATE_EVALUATING} —— 正在评估质量门禁（R14）。</li>
 *   <li>{@link #PASSED} —— 终态：门禁通过（R14.4）。</li>
 *   <li>{@link #FAILED_GATE} —— 终态：门禁失败（R14.3）。</li>
 *   <li>{@link #EXECUTION_FAILED} —— 终态：执行异常 / 用户取消（R9.2 / R9.6）。</li>
 * </ol>
 *
 * <p>Covers: R9.1, R9.3。
 */
public enum ReviewTaskStatus {

    PENDING,
    FETCHING_DIFF,
    STATIC_SCANNING,
    AI_REVIEWING,
    GATE_EVALUATING,
    PASSED,
    FAILED_GATE,
    EXECUTION_FAILED;

    /** 三种终态的集合。 */
    public static final Set<ReviewTaskStatus> TERMINAL =
            EnumSet.of(PASSED, FAILED_GATE, EXECUTION_FAILED);

    /** 中间执行态（受 {@code TaskRecoveryRunner} 启动期扫描）。 */
    public static final Set<ReviewTaskStatus> IN_FLIGHT =
            EnumSet.of(FETCHING_DIFF, STATIC_SCANNING, AI_REVIEWING, GATE_EVALUATING);

    /** 是否为终态。 */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** 是否为中间执行态。 */
    public boolean isInFlight() {
        return IN_FLIGHT.contains(this);
    }
}
