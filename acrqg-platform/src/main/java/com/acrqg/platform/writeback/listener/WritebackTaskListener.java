package com.acrqg.platform.writeback.listener;

import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.event.TaskFinishedEvent;
import com.acrqg.platform.writeback.service.WritebackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 评审任务终态事件订阅者：触发 commit status 回写。
 *
 * <p>订阅 {@link TaskFinishedEvent}：
 * <ul>
 *   <li>当任务最终状态是 {@link ReviewTaskStatus#PASSED} 或
 *       {@link ReviewTaskStatus#FAILED_GATE} 时，调用
 *       {@link WritebackService#writebackAsync} 异步回写；</li>
 *   <li>{@link ReviewTaskStatus#EXECUTION_FAILED} 同样触发回写（state=ERROR）；
 *       这覆盖 design.md §6.9 中"系统侧回写代码平台"语义，让外部 PR 页面能看到
 *       "评审执行异常"，避免一直停在"PR 评审中"。</li>
 * </ul>
 *
 * <p>本监听器不直接注解 {@code @Async} —— 异步性由
 * {@link WritebackService#writebackAsync} 内部的 {@code @Async} 注解保证；
 * 这样能让本监听器在事件发布线程中快速完成，仅把"重活"丢到 writeback 线程池。
 *
 * <p>Covers: R14.6, R14.7, R20.1。
 */
@Component
public class WritebackTaskListener {

    private static final Logger log = LoggerFactory.getLogger(WritebackTaskListener.class);

    private final WritebackService writebackService;

    public WritebackTaskListener(WritebackService writebackService) {
        this.writebackService = writebackService;
    }

    /**
     * 监听任务终态事件并触发异步回写。
     *
     * <p>本方法<b>必须</b>是 public 才能被 Spring AOP 代理；返回 {@code void}
     * 即可，不需要返回 {@link java.util.concurrent.Future}（无人订阅结果）。
     */
    @EventListener
    public void onTaskFinished(TaskFinishedEvent event) {
        if (event == null || event.taskId() == null) {
            return;
        }
        ReviewTaskStatus status = event.status();
        if (status == null) {
            return;
        }
        // 仅终态触发回写；TaskFinishedEvent 的发布点已经做了过滤，但本监听器再做
        // 一次防御性判断，避免未来 task 模块演进改变发布语义。
        if (!status.isTerminal()) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("WritebackTaskListener received task-finished: taskId={} status={}",
                    event.taskId(), status);
        }
        try {
            writebackService.writebackAsync(event.taskId());
        } catch (RuntimeException ex) {
            // 异步代理调用本身极少抛异常；防御性记录
            log.warn("writebackAsync trigger failed for taskId={}: {}",
                    event.taskId(), ex.toString(), ex);
        }
    }
}
