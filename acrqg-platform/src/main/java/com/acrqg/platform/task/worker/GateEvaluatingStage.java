package com.acrqg.platform.task.worker;

import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.log.TaskLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * GATE_EVALUATING 阶段占位实现。
 *
 * <p><b>TODO B3-F</b>：本类将由 {@code feat/m07-gate-engine} 分支替换为
 * {@code GateRuleEngine + MetricCollector} 实现，处理质量门禁判定（R14）。
 * 真实实现需要根据规则评估结果返回 {@link ReviewTaskStatus#PASSED} 或
 * {@link ReviewTaskStatus#FAILED_GATE}。
 *
 * <p>当前 NOOP 实现固定返回 PASSED，便于在 B3-A 阶段端到端验证状态机贯通。
 *
 * <p>Covers: R9.1, R14（占位）。
 */
@Component
@Profile("worker")
public class GateEvaluatingStage implements TaskStage {

    private static final Logger log = LoggerFactory.getLogger(GateEvaluatingStage.class);

    private final TaskLogger taskLogger;

    public GateEvaluatingStage(TaskLogger taskLogger) {
        this.taskLogger = taskLogger;
    }

    @Override
    public ReviewTaskStatus stage() {
        return ReviewTaskStatus.GATE_EVALUATING;
    }

    @Override
    public ReviewTaskStatus next(StageContext ctx) {
        log.debug("GateEvaluatingStage(NOOP): taskId={} attempt={}", ctx.taskId(), ctx.attempt());
        taskLogger.info(ctx.taskId(), stage().name(),
                "GATE_EVALUATING placeholder (B3-F will implement quality gate evaluation)");
        // NOOP 占位：默认返回 PASSED；真实实现按规则评估返回 PASSED / FAILED_GATE
        return ReviewTaskStatus.PASSED;
    }
}
