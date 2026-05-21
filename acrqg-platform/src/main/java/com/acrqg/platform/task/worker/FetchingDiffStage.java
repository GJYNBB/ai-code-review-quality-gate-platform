package com.acrqg.platform.task.worker;

import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.log.TaskLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * FETCHING_DIFF 阶段占位实现。
 *
 * <p><b>TODO B3-C</b>：本类将由 {@code feat/m04-diff} 分支替换为真正的
 * {@code DiffParserImpl + ProviderClient.fetchDiff} 串联实现，处理 PR/MR diff
 * 拉取与解析（R10）。
 *
 * <p>当前 NOOP 实现仅写一条 INFO 日志后直接返回 STATIC_SCANNING，使整条任务链
 * 能在 B3-A 阶段端到端跑通（PENDING → FETCHING_DIFF → STATIC_SCANNING → ...）。
 *
 * <p>仅在 {@code worker} profile 下注册 bean：api 进程无 stage 实现，
 * Orchestrator 调度时会立即走 EXECUTION_FAILED 兜底。
 *
 * <p>Covers: R9.1, R10（占位）。
 */
@Component
@Profile("worker")
public class FetchingDiffStage implements TaskStage {

    private static final Logger log = LoggerFactory.getLogger(FetchingDiffStage.class);

    private final TaskLogger taskLogger;

    public FetchingDiffStage(TaskLogger taskLogger) {
        this.taskLogger = taskLogger;
    }

    @Override
    public ReviewTaskStatus stage() {
        return ReviewTaskStatus.FETCHING_DIFF;
    }

    @Override
    public ReviewTaskStatus next(StageContext ctx) {
        log.debug("FetchingDiffStage(NOOP): taskId={} attempt={}", ctx.taskId(), ctx.attempt());
        taskLogger.info(ctx.taskId(), stage().name(),
                "FETCHING_DIFF placeholder (B3-C will implement diff fetching)");
        return ReviewTaskStatus.STATIC_SCANNING;
    }
}
