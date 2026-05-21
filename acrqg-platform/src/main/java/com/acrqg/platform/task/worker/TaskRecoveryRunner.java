package com.acrqg.platform.task.worker;

import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.log.TaskLogger;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.acrqg.platform.task.service.ReviewTaskService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Worker 启动期断点恢复（design.md §16.3 策略 A）。
 *
 * <p>仅在 {@code worker} profile 下生效。Spring Boot 启动结束后调用
 * {@link #run(ApplicationArguments)}：扫描所有处于中间执行态的任务
 * （{@link ReviewTaskStatus#IN_FLIGHT}）→ 通过
 * {@link ReviewTaskService#transitTo} 将其置为
 * {@link ReviewTaskStatus#EXECUTION_FAILED}；并通过
 * {@link TaskLogger#warn} 记录"任务被 worker 重启中断"。
 *
 * <p>这种策略避免了任务永久卡在中间态——之前进程崩溃时持有的状态会被新进程
 * 显式标记为失败，由人工或自动 retry 重启。这与 design.md §16.3 的描述一致：
 * "宁可让用户重试，也不愿继续推进可能基于错误中间状态的任务"。
 *
 * <p>注意：本 Runner 仅处理那些"在 DB 中处于中间态"但"消息已被 ACK 或丢失"的
 * 孤儿任务；对于"消息仍在 PEL 中"的情况，应由 Stream 的 XCLAIM 机制处理
 * （超出 B3-A 范围）。两条路径互补，不会重复处理同一任务（CAS 会保证 only-one
 * 语义）。
 *
 * <p>Covers: R24.4。
 */
@Component
@Profile("worker")
public class TaskRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskRecoveryRunner.class);

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewTaskService reviewTaskService;
    private final TaskLogger taskLogger;

    public TaskRecoveryRunner(ReviewTaskMapper reviewTaskMapper,
                              ReviewTaskService reviewTaskService,
                              TaskLogger taskLogger) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewTaskService = reviewTaskService;
        this.taskLogger = taskLogger;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ReviewTask> stuck;
        try {
            stuck = reviewTaskMapper.findStuckTasks();
        } catch (RuntimeException ex) {
            // DB 连接异常 / 表不存在等：仅记 ERROR，让 Worker 仍然启动消费新任务
            log.error("TaskRecoveryRunner.findStuckTasks failed: err={}", ex.toString(), ex);
            return;
        }
        if (stuck == null || stuck.isEmpty()) {
            log.info("TaskRecoveryRunner: no stuck tasks");
            return;
        }
        int recovered = 0;
        for (ReviewTask t : stuck) {
            String currentStage = t.getStatus();
            try {
                reviewTaskService.transitTo(t.getId(), ReviewTaskStatus.EXECUTION_FAILED);
                taskLogger.warn(t.getId(), currentStage,
                        "task interrupted by worker restart");
                // 终态：写 finished_at（恢复路径不更新 score / aiRiskScore）
                reviewTaskMapper.setFinishedAt(t.getId(), null, null, null);
                recovered++;
            } catch (RuntimeException ex) {
                // 单条失败不影响其他任务恢复
                log.warn("TaskRecoveryRunner: failed to recover taskId={} status={} err={}",
                        t.getId(), currentStage, ex.toString());
            }
        }
        log.info("TaskRecoveryRunner: recovered {}/{} stuck tasks", recovered, stuck.size());
    }
}
