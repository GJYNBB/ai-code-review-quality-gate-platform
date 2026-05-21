package com.acrqg.platform.task.worker;

import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.log.TaskLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * AI_REVIEWING 阶段占位实现。
 *
 * <p><b>TODO B3-E</b>：本类将由 {@code feat/m06-ai} 分支替换为
 * {@code AiReviewService + SensitiveFilter + AiReviewClient} 实现，
 * 处理 AI 评审、Schema 校验、降级路径（R12.1~R12.6）。
 *
 * <p>当前 NOOP 实现仅写一条 INFO 日志后返回 GATE_EVALUATING。
 *
 * <p>Covers: R9.1, R12（占位）。
 */
@Component
@Profile("worker")
public class AiReviewingStage implements TaskStage {

    private static final Logger log = LoggerFactory.getLogger(AiReviewingStage.class);

    private final TaskLogger taskLogger;

    public AiReviewingStage(TaskLogger taskLogger) {
        this.taskLogger = taskLogger;
    }

    @Override
    public ReviewTaskStatus stage() {
        return ReviewTaskStatus.AI_REVIEWING;
    }

    @Override
    public ReviewTaskStatus next(StageContext ctx) {
        log.debug("AiReviewingStage(NOOP): taskId={} attempt={}", ctx.taskId(), ctx.attempt());
        taskLogger.info(ctx.taskId(), stage().name(),
                "AI_REVIEWING placeholder (B3-E will implement AI review with degradation)");
        return ReviewTaskStatus.GATE_EVALUATING;
    }
}
