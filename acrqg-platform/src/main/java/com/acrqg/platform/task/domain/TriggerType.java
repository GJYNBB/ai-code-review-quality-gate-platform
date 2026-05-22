package com.acrqg.platform.task.domain;

/**
 * 评审任务触发来源。
 *
 * <p>对齐 design.md §6.3 / §7.2：{@code review_task.trigger_type} 列的
 * {@code CHECK} 约束允许 4 种取值。
 *
 * <ul>
 *   <li>{@link #WEBHOOK} —— 由代码平台 PR/MR/PUSH 事件触发（R7）。</li>
 *   <li>{@link #MANUAL}  —— 用户在控制台或 IDE 插件中手动触发（R8）。</li>
 *   <li>{@link #CI_CD}   —— 由 CI/CD 系统账号通过手动接口触发（R8 + R2.5）。</li>
 *   <li>{@link #RETRY}   —— 终态任务被重试触发（R9.4）；通过
 *                          {@link com.acrqg.platform.task.dto.RetryRequest} 提交。</li>
 * </ul>
 *
 * <p>Covers: R7.3, R8.1, R9.4。
 */
public enum TriggerType {

    /** 代码平台 Webhook 触发。 */
    WEBHOOK,

    /** 用户手动触发。 */
    MANUAL,

    /** CI/CD 系统账号触发。 */
    CI_CD,

    /** 重试触发（继承上一次的三元组）。 */
    RETRY
}
