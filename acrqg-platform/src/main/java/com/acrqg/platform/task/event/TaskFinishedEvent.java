package com.acrqg.platform.task.event;

import com.acrqg.platform.task.domain.ReviewTaskStatus;

/**
 * 评审任务进入终态事件（design.md §6.3 / §9 SD-2 / R14.6 / R19.1）。
 *
 * <p>由 {@link com.acrqg.platform.task.service.ReviewTaskService#transitTo
 * ReviewTaskService.transitTo} 在状态机迁移到 {@link ReviewTaskStatus#PASSED} /
 * {@link ReviewTaskStatus#FAILED_GATE} / {@link ReviewTaskStatus#EXECUTION_FAILED}
 * 时通过 {@link org.springframework.context.ApplicationEventPublisher#publishEvent}
 * 发布。订阅者：
 * <ul>
 *   <li>writeback 模块（B4-E）：异步触发 commit status 回写；</li>
 *   <li>notification 模块（B4-D，未来合入 develop 后）：给任务发起人 + 项目
 *       PROJECT_ADMIN 发"评审完成"通知（R19.1）。</li>
 * </ul>
 *
 * <p><b>解耦语义</b>：本 record 只承载"identifier 三件套 + 状态 + 操作者快照"，
 * 不引用 {@code ReviewTask} entity 类型，避免 task 模块对 writeback /
 * notification 模块的反向依赖。订阅方按 {@link #taskId} 自行查询完整字段。
 *
 * <p><b>合并兼容</b>：B4-D worktree 也需要同名 record；两个分支合并时，本 record
 * 的字段集合保持稳定（{@code taskId / projectId / status / triggerUserId /
 * creatorUserId}）即可。
 *
 * <p>Covers: R9.1, R14.6, R19.1, R20.1。
 *
 * @param taskId        任务主键
 * @param projectId     任务所属项目主键
 * @param status        任务最终状态（仅 PASSED / FAILED_GATE / EXECUTION_FAILED）
 * @param triggerUserId 当前推动状态迁移的用户主键（系统自动迁移时为 {@code null}）
 * @param creatorUserId 任务创建者用户主键（webhook 触发时为 {@code null}）
 */
public record TaskFinishedEvent(
        Long taskId,
        Long projectId,
        ReviewTaskStatus status,
        Long triggerUserId,
        Long creatorUserId) {
}
