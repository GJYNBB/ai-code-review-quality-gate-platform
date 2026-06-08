package com.acrqg.platform.task.worker;

import com.acrqg.platform.infra.redis.RedisStreamPublisher;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.service.impl.ReviewTaskServiceImpl;
import com.acrqg.platform.task.log.TaskLogger;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.acrqg.platform.task.service.ReviewTaskService;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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

    private static final long PENDING_REENQUEUE_AFTER_SECONDS = 60L;
    private static final int PENDING_REENQUEUE_LIMIT = 200;
    private static final Duration RECOVERY_ENQUEUE_MARK_TTL = Duration.ofMinutes(10);

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewTaskService reviewTaskService;
    private final RedisStreamPublisher redisStreamPublisher;
    private final StringRedisTemplate stringRedisTemplate;
    private final TaskLogger taskLogger;

    public TaskRecoveryRunner(ReviewTaskMapper reviewTaskMapper,
                              ReviewTaskService reviewTaskService,
                              RedisStreamPublisher redisStreamPublisher,
                              StringRedisTemplate stringRedisTemplate,
                              TaskLogger taskLogger) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewTaskService = reviewTaskService;
        this.redisStreamPublisher = redisStreamPublisher;
        this.stringRedisTemplate = stringRedisTemplate;
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
            reenqueueOldPendingTasks();
            return;
        }
        if (stuck == null || stuck.isEmpty()) {
            log.info("TaskRecoveryRunner: no stuck tasks");
            reenqueueOldPendingTasks();
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
        reenqueueOldPendingTasks();
    }

    @Scheduled(initialDelayString = "${app.worker.pending-recovery.initial-delay-ms:30000}",
            fixedDelayString = "${app.worker.pending-recovery.fixed-delay-ms:60000}")
    void scheduledPendingRecovery() {
        reenqueueOldPendingTasks();
    }

    private void reenqueueOldPendingTasks() {
        List<ReviewTask> pending;
        try {
            pending = reviewTaskMapper.findOldPendingTasks(PENDING_REENQUEUE_AFTER_SECONDS,
                    PENDING_REENQUEUE_LIMIT);
        } catch (RuntimeException ex) {
            log.error("TaskRecoveryRunner.findOldPendingTasks failed: err={}", ex.toString(), ex);
            return;
        }
        if (pending == null || pending.isEmpty()) {
            log.info("TaskRecoveryRunner: no old PENDING tasks to re-enqueue");
            return;
        }
        int enqueued = 0;
        for (ReviewTask t : pending) {
            try {
                int attempt = t.getAttempt() == null ? 1 : t.getAttempt();
                String markKey = "task:recovery:enqueued:" + t.getId() + ":" + attempt;
                Boolean marked = stringRedisTemplate.opsForValue()
                        .setIfAbsent(markKey, "1", RECOVERY_ENQUEUE_MARK_TTL);
                if (!Boolean.TRUE.equals(marked)) {
                    continue;
                }
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("taskId", String.valueOf(t.getId()));
                fields.put("attempt", String.valueOf(attempt));
                redisStreamPublisher.enqueue(ReviewTaskServiceImpl.STREAM_KEY, fields);
                taskLogger.warn(t.getId(), ReviewTaskStatus.PENDING.name(),
                        "old PENDING task re-enqueued by recovery runner");
                enqueued++;
            } catch (RuntimeException ex) {
                log.warn("TaskRecoveryRunner: failed to re-enqueue pending taskId={} err={}",
                        t.getId(), ex.toString());
            }
        }
        log.info("TaskRecoveryRunner: re-enqueued {}/{} old PENDING tasks", enqueued, pending.size());
    }
}
