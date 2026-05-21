package com.acrqg.platform.notification.event;

import com.acrqg.platform.task.event.TaskFinishedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 站内通知事件发布工具（骨架）。
 *
 * <p>把 {@link ApplicationEventPublisher#publishEvent(Object)} 收敛到一个具名 bean，
 * 便于业务模块（task / gate / issue）以"领域语义"调用，而不是直接依赖 Spring API。
 *
 * <p>当前仅暴露 {@link #publishTaskFinished(TaskFinishedEvent)}；随着 B4-E
 * 豁免审批与 B4-A 问题指派接入，可继续在此处补充
 * {@code publishWaiverRequest} / {@code publishIssueAssigned} 等方法。
 *
 * <p>Covers: R19.1。
 */
@Component
public class NotificationEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public NotificationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 发布评审任务终态事件，由 {@link NotificationEventListener} 异步消费并转为通知。
     *
     * <p>非终态事件请勿在此发布；监听器仍会在收到非终态时静默忽略，但发布方应
     * 提前过滤以避免无效广播。
     */
    public void publishTaskFinished(TaskFinishedEvent event) {
        if (event == null) {
            return;
        }
        applicationEventPublisher.publishEvent(event);
    }
}
