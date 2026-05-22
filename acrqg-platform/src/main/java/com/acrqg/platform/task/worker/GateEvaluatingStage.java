package com.acrqg.platform.task.worker;

import com.acrqg.platform.gate.domain.GateResultStatus;
import com.acrqg.platform.gate.dto.GateResultDTO;
import com.acrqg.platform.gate.engine.GateRuleEngine;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.log.TaskLogger;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * GATE_EVALUATING 阶段实现（B3-F.5）。
 *
 * <p>调用 {@link GateRuleEngine#evaluate(Long)} 完成质量门禁判定 + 写库 +
 * 同步 {@code review_task.ai_risk_score / ai_available}；之后：
 * <ul>
 *   <li>{@link GateResultStatus#FAILED} → 阶段返回 {@link ReviewTaskStatus#FAILED_GATE}；</li>
 *   <li>{@link GateResultStatus#PASSED} → 阶段返回 {@link ReviewTaskStatus#PASSED}；</li>
 *   <li>其他（{@code PENDING} / {@code WAIVED}）→ 视为非预期，转 PASSED 并写 WARN
 *       级 task_log（{@code WAIVED} 由 B4-E 单独驱动，不应在 B3-F 路径出现）。</li>
 * </ul>
 *
 * <p>同时把 {@link GateResultDTO#score()} 写回 {@code review_task.score} 字段，
 * 便于报告页直接展示（design.md §6.7 / R14.5）。score 通过 {@link ReviewTaskMapper#updateById}
 * 仅更新 {@code score} 列，不触碰其他字段。{@code finished_at} 由
 * {@link TaskOrchestrator} 在终态切换后统一写入。
 *
 * <p><b>状态机</b>：本阶段只<i>返回</i>下一步状态，<b>不主动调用</b>
 * {@code ReviewTaskService.transitTo}——状态迁移由 {@link TaskOrchestrator} 在
 * 拿到 next() 返回值后做合法性校验 + CAS 更新，避免双重迁移导致 CAS 失败。
 *
 * <p>失败语义：{@link GateRuleEngine} 内部抛出的 {@link RuntimeException} 不被本类
 * 捕获——交由 Orchestrator 统一转为 EXECUTION_FAILED。
 *
 * <p>仅在 {@code worker} profile 下注册 bean。
 *
 * <p>Covers: R9.1, R14.3, R14.4, R14.5, R14.8。
 */
@Component
@Profile("worker")
public class GateEvaluatingStage implements TaskStage {

    private static final Logger log = LoggerFactory.getLogger(GateEvaluatingStage.class);

    /** B3-F 阶段超时秒数（按任务交付清单要求 30s）。 */
    private static final long TIMEOUT_SECONDS = 30L;

    private final GateRuleEngine gateRuleEngine;
    private final ReviewTaskMapper reviewTaskMapper;
    private final TaskLogger taskLogger;

    public GateEvaluatingStage(GateRuleEngine gateRuleEngine,
                                ReviewTaskMapper reviewTaskMapper,
                                TaskLogger taskLogger) {
        this.gateRuleEngine = gateRuleEngine;
        this.reviewTaskMapper = reviewTaskMapper;
        this.taskLogger = taskLogger;
    }

    @Override
    public ReviewTaskStatus stage() {
        return ReviewTaskStatus.GATE_EVALUATING;
    }

    @Override
    public ReviewTaskStatus next(StageContext ctx) {
        long taskId = ctx.taskId();
        GateResultDTO result = gateRuleEngine.evaluate(taskId);
        if (result == null) {
            // 极端情况下 engine 未生成结果：写 ERROR 让 Orchestrator 转 EXECUTION_FAILED
            taskLogger.error(taskId, stage().name(),
                    "gate engine returned null result");
            throw new IllegalStateException("gate engine returned null result, taskId=" + taskId);
        }

        // 1) 把 score 回填到 review_task（仅更新 score 列）
        if (result.score() != null) {
            try {
                ReviewTask patch = new ReviewTask();
                patch.setId(taskId);
                patch.setScore(result.score());
                reviewTaskMapper.updateById(patch);
            } catch (RuntimeException ex) {
                // 该写入失败不阻塞状态迁移；gate_result.score 仍是权威来源
                log.warn("write review_task.score failed: taskId={} err={}",
                        taskId, ex.toString(), ex);
            }
        }

        // 2) 根据 status 决定下一步状态
        GateResultStatus s = parseGateResultStatus(result.status());
        ReviewTaskStatus next = switch (s) {
            case FAILED -> ReviewTaskStatus.FAILED_GATE;
            case PASSED -> ReviewTaskStatus.PASSED;
            case PENDING, WAIVED -> {
                taskLogger.warn(taskId, stage().name(),
                        "unexpected gate result status=" + s + ", default to PASSED");
                yield ReviewTaskStatus.PASSED;
            }
        };

        if (log.isDebugEnabled()) {
            log.debug("GateEvaluatingStage: taskId={} attempt={} gateStatus={} score={} next={}",
                    taskId, ctx.attempt(), s, result.score(), next);
        }
        return next;
    }

    @Override
    public long timeoutSeconds() {
        return TIMEOUT_SECONDS;
    }

    private static GateResultStatus parseGateResultStatus(String s) {
        if (s == null) {
            return GateResultStatus.PASSED;
        }
        try {
            return GateResultStatus.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return GateResultStatus.PASSED;
        }
    }
}
