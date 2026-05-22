package com.acrqg.platform.writeback.event;

/**
 * 状态回写最终失败事件（design.md §6.9 / R14.7 / R20.4）。
 *
 * <p>由 {@link com.acrqg.platform.writeback.service.WritebackService#writeback
 * WritebackService.writeback} 在指数退避重试三次仍失败后通过
 * {@link org.springframework.context.ApplicationEventPublisher#publishEvent}
 * 发布。订阅者：
 * <ul>
 *   <li>notification 模块（B4-D，未来）：可发"回写失败"通知给 PROJECT_ADMIN；</li>
 *   <li>监控指标（{@code acrqg_writeback_total{provider,result="failed"}}）。</li>
 * </ul>
 *
 * <p>事件本身不携带异常对象，只保留最后一次错误的字符串描述（避免跨异步线程
 * 传递 Throwable）。完整堆栈已通过 {@code TaskLogger.error} 落 task_log。
 *
 * <p>Covers: R14.7, R20.3, R20.4。
 *
 * @param taskId    任务主键
 * @param lastError 最后一次失败的简短描述（由 ProviderClient 抛出的 message）
 */
public record WritebackFailedEvent(Long taskId, String lastError) {
}
