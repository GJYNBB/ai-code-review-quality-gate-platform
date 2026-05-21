package com.acrqg.platform.gate.event;

/**
 * 门禁豁免审批完成事件（design.md §6.7 / §9 SD-5）。
 *
 * <p>由 {@link com.acrqg.platform.gate.service.GateWaiverService#approve
 * GateWaiverService.approve} 在审批落库后通过
 * {@link org.springframework.context.ApplicationEventPublisher#publishEvent}
 * 发布。两类订阅者：
 * <ul>
 *   <li>writeback 模块：批准时把 commit status 重新回写为 success（含"已豁免"
 *       描述），R15.4 / R20.6；</li>
 *   <li>notification 模块（B4-D）：给申请人发一条"审批结果"通知，R19.1。</li>
 * </ul>
 *
 * <p>事件本身不携带 waiver 实体，订阅方按 waiverId 自行查询，避免跨模块
 * 共享 entity 类型。
 *
 * <p>Covers: R15.3, R15.4, R19.1, R20.6。
 *
 * @param waiverId   豁免申请主键
 * @param taskId     关联任务主键（便于订阅方直接调 writeback / 通知不再二次查询）
 * @param approverId 审批人用户主键
 * @param approve    审批结论：{@code true}=APPROVED / {@code false}=REJECTED
 */
public record WaiverApprovedEvent(Long waiverId, Long taskId, Long approverId, boolean approve) {
}
