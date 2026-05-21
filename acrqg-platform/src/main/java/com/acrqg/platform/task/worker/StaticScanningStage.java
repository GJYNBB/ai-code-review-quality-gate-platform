package com.acrqg.platform.task.worker;

import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.log.TaskLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * STATIC_SCANNING 阶段占位实现。
 *
 * <p><b>TODO B3-D</b>：本类将由 {@code feat/m05-scanner} 分支替换为
 * {@code ScannerOrchestrator} 真实实现，处理多语言静态扫描（R11）。
 *
 * <p>当前 NOOP 实现仅写一条 INFO 日志后返回 AI_REVIEWING。
 *
 * <p>Covers: R9.1, R11（占位）。
 */
@Component
@Profile("worker")
public class StaticScanningStage implements TaskStage {

    private static final Logger log = LoggerFactory.getLogger(StaticScanningStage.class);

    private final TaskLogger taskLogger;

    public StaticScanningStage(TaskLogger taskLogger) {
        this.taskLogger = taskLogger;
    }

    @Override
    public ReviewTaskStatus stage() {
        return ReviewTaskStatus.STATIC_SCANNING;
    }

    @Override
    public ReviewTaskStatus next(StageContext ctx) {
        log.debug("StaticScanningStage(NOOP): taskId={} attempt={}", ctx.taskId(), ctx.attempt());
        taskLogger.info(ctx.taskId(), stage().name(),
                "STATIC_SCANNING placeholder (B3-D will implement scanner orchestration)");
        return ReviewTaskStatus.AI_REVIEWING;
    }
}
