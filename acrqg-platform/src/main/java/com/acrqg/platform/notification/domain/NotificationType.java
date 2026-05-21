package com.acrqg.platform.notification.domain;

/**
 * 站内通知类型枚举（design.md §6.6 / §8.4 / R19）。
 *
 * <p>取值与 {@code notification.type} 的字符串字段一一对应；表层是
 * {@code VARCHAR(32)} + 应用层枚举校验，新增类型不需要 schema 演进。
 *
 * <ul>
 *   <li>{@link #TASK_FINISHED} —— 评审任务进入 {@code PASSED} 终态（R19.1）。</li>
 *   <li>{@link #GATE_FAILED} —— 评审任务进入 {@code FAILED_GATE} 终态（R19.1）。</li>
 *   <li>{@link #ISSUE_ASSIGNED} —— 问题被指派给当前用户（R17 配套通知）。</li>
 *   <li>{@link #WAIVER_REQUEST} —— 收到豁免申请待审批（R19.2）。</li>
 *   <li>{@link #WAIVER_APPROVED} —— 豁免申请已通过（R19.2 配套）。</li>
 *   <li>{@link #WAIVER_REJECTED} —— 豁免申请被驳回（R19.2 配套）。</li>
 * </ul>
 *
 * <p>Covers: R19.1, R19.2, R19.4。
 */
public enum NotificationType {

    /** 评审任务通过门禁。 */
    TASK_FINISHED,

    /** 评审任务门禁判定失败。 */
    GATE_FAILED,

    /** 问题被指派给收件人。 */
    ISSUE_ASSIGNED,

    /** 收到豁免审批请求。 */
    WAIVER_REQUEST,

    /** 豁免审批通过。 */
    WAIVER_APPROVED,

    /** 豁免审批驳回。 */
    WAIVER_REJECTED
}
