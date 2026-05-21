package com.acrqg.platform.writeback.service;

/**
 * 状态回写服务（M09 / R14.6 / R20）。
 *
 * <p>对齐 design.md §6.9 / §9 SD-4：当 ReviewTask 进入 PASSED 或 FAILED_GATE
 * 终态后，把 commit status 通过 ProviderClient 回写到代码平台（GitHub/GitLab/Gitee）。
 *
 * <p>重试策略（R20.4）：仅在 5xx / 网络异常时按 1s → 2s → 4s 三次指数退避重试；
 * 4xx 立即失败（R20.3）。三次仍失败时写 ERROR 日志并发布
 * {@code WritebackFailedEvent}，不向上抛异常——避免触发评审任务流的状态回滚。
 *
 * <p>幂等：本服务由 {@link com.acrqg.platform.writeback.listener.WritebackTaskListener
 * WritebackTaskListener} 异步调用；同一 task 可能被多次触发回写（评审完成 +
 * 豁免审批 + 手动重试），ProviderClient.postCommitStatus 在 GitHub/GitLab/Gitee
 * 都是 idempotent 的（同一 sha + context/name 覆盖）。
 *
 * <p>Covers: R14.6, R14.7, R20.1, R20.2, R20.3, R20.4。
 */
public interface WritebackService {

    /**
     * 同步回写：取任务 → 取仓库 → 调 ProviderClient → 失败重试。
     *
     * <p>由"手动重试"接口（{@code POST /review-tasks/{id}/writeback/retry}）调用，
     * 调用方关注同步结果（成功 / 异常）。
     *
     * <p>三次重试仍失败时通过 {@code TaskLogger.error} 落 task_log + 发布
     * {@code WritebackFailedEvent}，<b>不</b>向上抛异常——避免影响外层事务回滚。
     * 调用方需要感知最终成败时应订阅事件或读取 task_log。
     *
     * @param taskId 任务主键
     */
    void writeback(Long taskId);

    /**
     * 异步回写：内部委托 {@link #writeback(Long)}，由 {@code @Async} 在专用线程池执行。
     *
     * <p>由 {@link com.acrqg.platform.writeback.listener.WritebackTaskListener
     * WritebackTaskListener} 在 {@code TaskFinishedEvent} 到达后调用。
     *
     * @param taskId 任务主键
     */
    void writebackAsync(Long taskId);
}
