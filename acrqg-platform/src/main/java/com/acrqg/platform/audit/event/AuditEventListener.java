package com.acrqg.platform.audit.event;

import com.acrqg.platform.audit.config.AuditAsyncConfig;
import com.acrqg.platform.audit.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 审计事件订阅者。
 *
 * <p>由各业务模块通过
 * {@link org.springframework.context.ApplicationEventPublisher#publishEvent(Object)
 * publishEvent(AuditEvent)} 发布事件，本监听器异步消费并落库。
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@link Async @Async("auditTaskExecutor")} 让 Spring 在
 *       {@link AuditAsyncConfig#EXECUTOR_BEAN_NAME 同名线程池} 中执行；
 *       默认同步事件机制会把异常传播给发布方，这里改为异步，业务发布方
 *       永远只会拿到 void 结果，不会感知审计落库的失败。</li>
 *   <li>{@link EventListener} 自动按事件类型过滤，无需在方法体内做 {@code instanceof}。</li>
 *   <li>{@link AuditService#record} 内部已经实现了"掩码 + 序列化 + 入库"的全部
 *       逻辑，监听器只负责转发。</li>
 * </ul>
 *
 * <p>Covers: R22.1, R22.5。
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditService auditService;

    public AuditEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 异步消费 {@link AuditEvent}。
     *
     * <p>本方法<b>必须</b>是 public 才能被 Spring AOP 代理；返回 {@code void}
     * 即可，不需要返回 {@link java.util.concurrent.Future}（无人订阅结果）。
     *
     * @param event 审计事件
     */
    @Async(AuditAsyncConfig.EXECUTOR_BEAN_NAME)
    @EventListener
    public void onAuditEvent(AuditEvent event) {
        if (event == null) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("audit event received: action={} resource={}/{} operatorId={}",
                    event.action(), event.resourceType(), event.resourceId(), event.operatorId());
        }
        auditService.record(event);
    }
}
