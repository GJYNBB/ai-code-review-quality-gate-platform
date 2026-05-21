package com.acrqg.platform.task.worker;

/**
 * 阶段执行上下文。
 *
 * <p>由 {@link TaskOrchestrator} 在调用每个 {@link TaskStage#next(StageContext)}
 * 时构造并传入。承载执行该阶段所必需的最小元数据：
 * <ul>
 *   <li>{@code taskId} —— 当前评审任务主键，是后续所有查询 / 写入的根 key；</li>
 *   <li>{@code projectId} —— 所属项目，便于阶段实现取仓库绑定 / 模型配置等；</li>
 *   <li>{@code attempt}  —— 重试次数，{@code retry} 触发时 +1，可用于阶段内幂等
 *       与降级策略（例如第二次重试时缩短 AI 超时）；</li>
 *   <li>{@code workerId} —— 当前 Worker 进程身份（如
 *       {@code worker-${HOSTNAME}-${PID}}），写入 task_log 的 detail 中便于
 *       多副本调度场景下的故障定位（R24.5）。</li>
 * </ul>
 *
 * <p>本类是不可变 {@code record}，可以在多线程间安全共享。
 *
 * <p>Covers: R9, R24.5。
 *
 * @param taskId    任务主键
 * @param projectId 项目主键
 * @param attempt   重试次数（首次为 1）
 * @param workerId  Worker 身份
 */
public record StageContext(
        long taskId,
        long projectId,
        int attempt,
        String workerId
) {
}
