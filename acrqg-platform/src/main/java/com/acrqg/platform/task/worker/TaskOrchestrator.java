package com.acrqg.platform.task.worker;

import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.log.TaskLogger;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.acrqg.platform.task.service.ReviewTaskService;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 评审任务编排器（design.md §6.3.1）。
 *
 * <p>从 PENDING 起逐阶段驱动评审任务直到终态：
 * <ol>
 *   <li>读取当前任务；若已为终态则立即返回；</li>
 *   <li>查表得到当前状态对应的 {@link TaskStage}；表里没有则视为不可识别状态，
 *       直接转 {@link ReviewTaskStatus#EXECUTION_FAILED}；</li>
 *   <li>若是首次进入执行阶段（即原状态为 {@link ReviewTaskStatus#PENDING} 且本次将
 *       推进到非 PENDING 状态），通过
 *       {@link ReviewTaskMapper#setStartedAtIfNull} 写 {@code started_at}；</li>
 *   <li>调用 {@code stage.next(ctx)}：
 *     <ul>
 *       <li>正常返回 → 用 {@link ReviewTaskService#transitTo} 做合法性 + CAS 更新；</li>
 *       <li>抛异常 / 状态机非法 → 写 ERROR 级 task_log，转 EXECUTION_FAILED；</li>
 *     </ul>
 *   </li>
 *   <li>到达终态后写 {@code finished_at}（{@link ReviewTaskMapper#setFinishedAt}）。</li>
 * </ol>
 *
 * <p><b>注意</b>：Orchestrator 是 worker profile 下的核心组件，但本身不绑定
 * {@code @Profile("worker")}：api profile 下也能注入 Spring 容器，但因 4 个
 * Stage 实现都标记为 {@code @Profile("worker")}，分发表会为空，导致 Orchestrator
 * 在该 profile 下被调用时立即把任务置为 EXECUTION_FAILED。这是有意为之——
 * 防止 api 进程被错误地用于消费 Stream。
 *
 * <p>Covers: R9.1, R9.2, R9.7, R24.5。
 */
@Component
public class TaskOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TaskOrchestrator.class);

    /** 单任务最多迭代步数（防御性兜底，避免 stage 实现错误返回死循环）。 */
    private static final int MAX_STEPS = 16;

    /** 写入 task_log 的 ERROR 消息长度上限。 */
    private static final int ERROR_MSG_MAX_LEN = 1000;

    /** {@code task_log.stage} 字面量（系统级阶段，区别于 4 个业务阶段）。 */
    private static final String SYSTEM_STAGE = "SYSTEM";

    private final List<TaskStage> stages;
    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewTaskService reviewTaskService;
    private final TaskLogger taskLogger;

    /** 在 {@link #init()} 中按 {@link TaskStage#stage()} 建立分发表。 */
    private Map<ReviewTaskStatus, TaskStage> stageByStatus;

    public TaskOrchestrator(List<TaskStage> stages,
                            ReviewTaskMapper reviewTaskMapper,
                            ReviewTaskService reviewTaskService,
                            TaskLogger taskLogger) {
        this.stages = stages;
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewTaskService = reviewTaskService;
        this.taskLogger = taskLogger;
    }

    @PostConstruct
    void init() {
        Map<ReviewTaskStatus, TaskStage> map = new EnumMap<>(ReviewTaskStatus.class);
        if (stages != null) {
            for (TaskStage s : stages) {
                ReviewTaskStatus key = s.stage();
                if (key == null) {
                    log.warn("TaskOrchestrator: stage bean {} returns null stage(), ignored",
                            s.getClass().getName());
                    continue;
                }
                TaskStage prev = map.put(key, s);
                if (prev != null) {
                    log.warn("TaskOrchestrator: multiple TaskStage beans for {}; later one wins ({} -> {})",
                            key, prev.getClass().getName(), s.getClass().getName());
                }
            }
        }
        this.stageByStatus = map;
        log.info("TaskOrchestrator initialised with stages: {}", stageByStatus.keySet());
    }

    /**
     * 串行驱动指定任务直到终态。
     *
     * <p>每一步迭代都重新读取任务（保证 attempt / status 与持久态一致），
     * 避免 stage 实现内部修改了 task 字段（例如 ai_available）后被覆盖。
     */
    public void run(long taskId) {
        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("TaskOrchestrator.run: task not found, skipped. taskId={}", taskId);
            return;
        }

        ReviewTaskStatus current = parseStatusOrFail(task.getStatus(), taskId);
        if (current.isTerminal()) {
            log.debug("TaskOrchestrator.run: already terminal, skipped. taskId={} status={}",
                    taskId, current);
            return;
        }

        String workerId = WorkerIdentity.current();

        for (int step = 0; step < MAX_STEPS; step++) {
            // 重新读最新状态，避免分布式 / 多线程并发下使用过期值
            task = reviewTaskMapper.selectById(taskId);
            if (task == null) {
                log.warn("TaskOrchestrator.run: task disappeared mid-flight. taskId={}", taskId);
                return;
            }
            current = parseStatusOrFail(task.getStatus(), taskId);
            if (current.isTerminal()) {
                writeFinishedAtSafely(taskId);
                return;
            }

            TaskStage stage = stageByStatus.get(current);
            if (stage == null) {
                String msg = "no TaskStage bean for status " + current
                        + " (worker may be running in api profile)";
                log.warn("TaskOrchestrator.run: {} taskId={}", msg, taskId);
                taskLogger.error(taskId, SYSTEM_STAGE, msg);
                forceFail(taskId, current);
                writeFinishedAtSafely(taskId);
                return;
            }

            // 首次离开 PENDING：记录 started_at
            if (current == ReviewTaskStatus.PENDING) {
                try {
                    reviewTaskMapper.setStartedAtIfNull(taskId);
                } catch (RuntimeException ex) {
                    // 仅记 WARN：started_at 缺失不影响主流程
                    log.warn("setStartedAtIfNull failed: taskId={} err={}", taskId, ex.toString());
                }
            }

            StageContext ctx = new StageContext(
                    taskId,
                    task.getProjectId(),
                    task.getAttempt() == null ? 1 : task.getAttempt(),
                    workerId);

            ReviewTaskStatus next;
            try {
                next = stage.next(ctx);
            } catch (RuntimeException ex) {
                String summary = summariseError(ex);
                log.error("TaskOrchestrator: stage {} threw exception. taskId={} err={}",
                        current, taskId, summary, ex);
                taskLogger.error(taskId, current.name(),
                        "stage " + current + " failed: " + summary, ex);
                forceFail(taskId, current);
                writeFinishedAtSafely(taskId);
                return;
            }

            if (next == null) {
                String msg = "stage " + current + " returned null";
                log.error("TaskOrchestrator: {} taskId={}", msg, taskId);
                taskLogger.error(taskId, current.name(), msg);
                forceFail(taskId, current);
                writeFinishedAtSafely(taskId);
                return;
            }

            // 正常迁移：先合法性 + CAS
            try {
                reviewTaskService.transitTo(taskId, next);
            } catch (BusinessException ex) {
                String summary = summariseError(ex);
                log.error("TaskOrchestrator: illegal transition {} -> {} for taskId={} err={}",
                        current, next, taskId, summary);
                taskLogger.error(taskId, current.name(),
                        "transit " + current + " -> " + next + " failed: " + summary, ex);
                forceFail(taskId, current);
                writeFinishedAtSafely(taskId);
                return;
            }

            // 终态退出
            if (next.isTerminal()) {
                writeFinishedAtSafely(taskId);
                return;
            }
        }

        // 兜底：超过迭代上限说明 stage 表配置异常或返回值异常循环
        log.error("TaskOrchestrator: exceeded MAX_STEPS={} for taskId={}", MAX_STEPS, taskId);
        taskLogger.error(taskId, SYSTEM_STAGE,
                "exceeded MAX_STEPS=" + MAX_STEPS + ", forcing EXECUTION_FAILED");
        forceFail(taskId, current);
        writeFinishedAtSafely(taskId);
    }

    /**
     * 把任务从 {@code from} 强制转为 {@code EXECUTION_FAILED}（R9.2）。
     *
     * <p>使用 Mapper 的 CAS 接口，避免与并发 retry 冲突；当 CAS 失败时仅记 WARN
     * 不再重试。
     */
    private void forceFail(long taskId, ReviewTaskStatus from) {
        if (from.isTerminal()) {
            return;
        }
        int affected = reviewTaskMapper.updateStatusOnlyIfFrom(
                taskId, from.name(), ReviewTaskStatus.EXECUTION_FAILED.name());
        if (affected == 0) {
            log.warn("forceFail: CAS failed (concurrent?). taskId={} from={}", taskId, from);
        }
    }

    private void writeFinishedAtSafely(long taskId) {
        try {
            reviewTaskMapper.setFinishedAt(taskId, null, null, null);
        } catch (RuntimeException ex) {
            log.warn("setFinishedAt failed: taskId={} err={}", taskId, ex.toString());
        }
    }

    private static ReviewTaskStatus parseStatusOrFail(String status, long taskId) {
        if (status == null) {
            throw new IllegalStateException("task status is null, taskId=" + taskId);
        }
        try {
            return ReviewTaskStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("unknown task status: " + status + ", taskId=" + taskId, ex);
        }
    }

    private static String summariseError(Throwable ex) {
        String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        if (msg.length() > ERROR_MSG_MAX_LEN) {
            return msg.substring(0, ERROR_MSG_MAX_LEN);
        }
        return msg;
    }
}
