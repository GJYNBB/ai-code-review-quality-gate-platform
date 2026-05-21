package com.acrqg.platform.gate.domain;

/**
 * 门禁豁免申请状态字典（design.md §7.4 / R15）。
 *
 * <p>对应 {@code gate_waiver.status} 列的 CHECK 约束取值，与 V51 迁移文件保持一致：
 * <ul>
 *   <li>{@link #PENDING}  —— 已申请，等待审批；同一 task 同一时刻至多一条
 *       （由部分唯一索引 {@code uk_gate_waiver_task_pending} 约束）。</li>
 *   <li>{@link #APPROVED} —— 已审批通过；触发 GateResult 转 WAIVED 与 task 转 PASSED
 *       （R15.3 / R15.4）。</li>
 *   <li>{@link #REJECTED} —— 已拒绝；保留历史记录，允许后续重新提交。</li>
 * </ul>
 *
 * <p>与 design.md §7.2 的 {@code EXPIRED} 状态相比做了简化：本批次（B4-E）仅
 * 实现"申请 → 审批 → 永久生效"三步语义，未引入 {@code expire_at} 字段与定时
 * 过期任务，因此本枚举不包含 {@code EXPIRED}。
 *
 * <p>Covers: R15.1, R15.3, R15.6。
 */
public enum GateWaiverStatus {

    /** 已申请，等待审批。 */
    PENDING,

    /** 已审批通过。 */
    APPROVED,

    /** 已拒绝。 */
    REJECTED
}
