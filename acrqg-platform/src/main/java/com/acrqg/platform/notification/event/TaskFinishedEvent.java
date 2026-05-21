package com.acrqg.platform.notification.event;

import com.acrqg.platform.task.domain.ReviewTaskStatus;

/**
 * 评审任务终态事件（B4-D 占位）。
 *
 * <p>本任务（B4-D 站内通知）只承担"如果有人发布该事件就转化为通知"的职责，
 * 因此事件本身在通知模块内定义；真正的发布方在 B5 / 后续任务由
 * {@code GateEvaluatingStage} 或 {@code ReviewTaskService.transitTo} 接入。
 *
 * <p>保留 {@link com.acrqg.platform.task.domain.ReviewTaskStatus 任务状态} 类型化
 * 而非字符串，是为了让监听器可以严格区分 {@code PASSED} / {@code FAILED_GATE}
 * / {@code EXECUTION_FAILED}，避免拼写错误。
 *
 * <p>Covers: R19.1。
 *
 * @param taskId          任务主键，必填
 * @param projectId       项目主键，必填（用于未来扩展项目管理员收件人查询）
 * @param status          任务终态，必填；非终态事件由发布方过滤后再发出
 * @param triggerUserId   触发该任务的用户主键（手动触发的发起人）；webhook
 *                        触发时可为 {@code null}
 * @param creatorUserId   该任务的创建者用户主键；与 triggerUserId 不一定相同
 *                        （例如系统重试场景）；可为 {@code null}
 */
public record TaskFinishedEvent(
        Long taskId,
        Long projectId,
        ReviewTaskStatus status,
        Long triggerUserId,
        Long creatorUserId
) {
}
