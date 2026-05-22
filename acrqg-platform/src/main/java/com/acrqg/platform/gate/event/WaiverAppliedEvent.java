package com.acrqg.platform.gate.event;

/**
 * 门禁豁免申请已提交事件（design.md §6.7 / §9 SD-5 / R19.2）。
 *
 * <p>由 {@link com.acrqg.platform.gate.service.GateWaiverService#apply
 * GateWaiverService.apply} 在 INSERT 完成后通过
 * {@link org.springframework.context.ApplicationEventPublisher#publishEvent}
 * 发布。M09 通知模块（B4-D）的 {@code NotificationEventListener} 订阅本事件
 * 并发出"豁免审批通知"。
 *
 * <p>使用领域事件而非直接调用 {@code NotificationService} 是为了：
 * <ul>
 *   <li>保持 gate 模块与 notification 模块的<b>单向依赖</b>——gate 不知道
 *       通知模块的存在，只发布事件；</li>
 *   <li>便于多个订阅者并存（除了通知，还可能有审计补强、Webhook 转发等）；</li>
 *   <li>当 B4-D worktree 尚未合并时，本模块也能独立编译运行——订阅者缺席时
 *       事件被静默丢弃，不影响业务路径。</li>
 * </ul>
 *
 * <p>Covers: R15.1, R19.2。
 *
 * @param waiverId    豁免申请主键
 * @param projectId   任务所属项目主键，便于订阅方按项目筛选审批人
 * @param applicantId 申请人用户主键
 * @param taskId      关联任务主键
 */
public record WaiverAppliedEvent(Long waiverId, Long projectId, Long applicantId, Long taskId) {
}
