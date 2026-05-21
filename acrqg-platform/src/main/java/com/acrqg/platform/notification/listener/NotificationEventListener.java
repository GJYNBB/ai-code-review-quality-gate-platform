package com.acrqg.platform.notification.listener;

import com.acrqg.platform.notification.domain.NotificationType;
import com.acrqg.platform.notification.event.TaskFinishedEvent;
import com.acrqg.platform.notification.service.NotificationService;
import com.acrqg.platform.task.domain.ReviewTaskStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 站内通知领域事件订阅者（B4-D.4）。
 *
 * <p>本监听器仅承担"事件 → 通知"的转化，不修改任何业务模块源代码。
 * 当前接入：
 * <ul>
 *   <li>{@link TaskFinishedEvent} 终态分流：
 *     <ul>
 *       <li>{@code PASSED} → 触发用户收到 {@link NotificationType#TASK_FINISHED}；</li>
 *       <li>{@code FAILED_GATE} → 触发用户与创建者各收到一条
 *           {@link NotificationType#GATE_FAILED}（去重在 service 层完成）。</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>{@link EventListener} 默认同步消费——监听器内部仅做一次写库 + Redis pub
 * 的轻量逻辑，且 {@link NotificationService#notify} 自身会吞下 Redis 异常，
 * 在事件源所在事务提交后被调用时也不会回滚事件源。如未来需要进一步隔离
 * 失败，可以将本监听器改为 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * + {@code @Async}。当前阶段保持简单同步实现以减少出错面。
 *
 * <p>Covers: R19.1。
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    /** 业务关联资源类型：评审任务，与 {@code notification.related_type} 列对齐。 */
    static final String RELATED_TYPE_REVIEW_TASK = "review_task";

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 处理评审任务终态事件。
     *
     * <p>非终态事件被静默忽略；缺少 {@code taskId} / {@code status} 视为非法事件，
     * 仅 WARN 一行日志后返回。
     */
    @EventListener
    public void onTaskFinished(TaskFinishedEvent event) {
        if (event == null) {
            return;
        }
        if (event.taskId() == null || event.status() == null) {
            log.warn("invalid TaskFinishedEvent: taskId={} status={}", event.taskId(), event.status());
            return;
        }
        ReviewTaskStatus status = event.status();
        if (!status.isTerminal()) {
            // 监听器层兜底：非终态事件不应触发通知
            if (log.isDebugEnabled()) {
                log.debug("ignore non-terminal TaskFinishedEvent: taskId={} status={}",
                        event.taskId(), status);
            }
            return;
        }

        switch (status) {
            case PASSED -> notifyPassed(event);
            case FAILED_GATE -> notifyFailedGate(event);
            // EXECUTION_FAILED 暂不发通知（任务交付清单未要求）；
            // 如未来 R19 扩展可在此处补一条 TASK_EXEC_FAILED 类型。
            default -> {
                if (log.isDebugEnabled()) {
                    log.debug("no notification for status={} taskId={}", status, event.taskId());
                }
            }
        }
    }

    private void notifyPassed(TaskFinishedEvent event) {
        Long recipient = event.triggerUserId();
        if (recipient == null) {
            // webhook 触发的任务可能没有 triggerUserId，此时 PASSED 通知发给创建者
            recipient = event.creatorUserId();
        }
        if (recipient == null) {
            log.warn("TaskFinishedEvent[PASSED] without recipient: taskId={}", event.taskId());
            return;
        }
        notificationService.notify(
                recipient,
                NotificationType.TASK_FINISHED,
                "评审通过",
                buildTaskBody(event.taskId(), "已通过质量门禁判定"),
                buildTaskLink(event.taskId()),
                RELATED_TYPE_REVIEW_TASK,
                event.taskId());
    }

    private void notifyFailedGate(TaskFinishedEvent event) {
        Set<Long> recipients = new LinkedHashSet<>(2);
        if (event.triggerUserId() != null) {
            recipients.add(event.triggerUserId());
        }
        if (event.creatorUserId() != null) {
            recipients.add(event.creatorUserId());
        }
        if (recipients.isEmpty()) {
            log.warn("TaskFinishedEvent[FAILED_GATE] without recipients: taskId={}", event.taskId());
            return;
        }
        // distinct 已由 LinkedHashSet 保证；过滤 null 后传给 notifyAll
        List<Long> distinct = new ArrayList<>(recipients.size());
        for (Long uid : recipients) {
            if (Objects.nonNull(uid)) {
                distinct.add(uid);
            }
        }
        notificationService.notifyAll(
                distinct,
                NotificationType.GATE_FAILED,
                "门禁未通过",
                "任务 #" + event.taskId() + " 门禁判定为 FAILED",
                buildTaskLink(event.taskId()),
                RELATED_TYPE_REVIEW_TASK,
                event.taskId());
    }

    private static String buildTaskBody(Long taskId, String summary) {
        return "任务 #" + taskId + " " + summary;
    }

    private static String buildTaskLink(Long taskId) {
        return "/review-tasks/" + taskId;
    }
}
