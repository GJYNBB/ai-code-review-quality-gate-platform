package com.acrqg.platform.task.worker;

import com.acrqg.platform.admin.domain.SystemParam;
import com.acrqg.platform.admin.repository.SystemParamMapper;
import com.acrqg.platform.ai.service.AiReviewOutcome;
import com.acrqg.platform.ai.service.AiReviewService;
import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.log.TaskLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * AI_REVIEWING 阶段实现（B3-E.5）。
 *
 * <p>调用 {@link AiReviewService#execute(Long)} 完成 AI 评审 + 降级 + 结果写入。
 *
 * <p><b>错误处理约定</b>：
 * AI 阶段的所有异常（敏感过滤失败 / 5xx 超时 / Schema 校验失败 / 模型未配置）
 * 都已在 {@link AiReviewService} 内部转化为正常 {@link AiReviewOutcome} 返回，
 * 本类不再 try/catch。即便 service 抛出未预期的 RuntimeException，本类也<b>主动
 * 吞掉</b>并记 WARN 级 task_log，让任务继续推进至 GATE_EVALUATING（R12.5：AI
 * 失败不阻塞任务）。这样能保证状态机在 AI 故障下不会陷入 EXECUTION_FAILED。
 *
 * <p>{@link #timeoutSeconds()} 读取 {@code ai.review.timeout.seconds}，与
 * {@link AiReviewService} 内部使用的超时一致；越界 / 缺失退化为 60s 默认值。
 *
 * <p>仅在 {@code worker} profile 下注册 bean。
 *
 * <p>Covers: R9.1, R12.1, R12.5。
 */
@Component
@Profile("worker")
public class AiReviewingStage implements TaskStage {

    private static final Logger log = LoggerFactory.getLogger(AiReviewingStage.class);

    /** system_param 中的超时 key（与 AiReviewServiceImpl 保持一致字面量）。 */
    static final String PARAM_TIMEOUT = "ai.review.timeout.seconds";

    /** 默认超时（秒）。 */
    static final long DEFAULT_TIMEOUT_SECONDS = 60L;

    private final AiReviewService aiReviewService;
    private final TaskLogger taskLogger;
    private final SystemParamMapper systemParamMapper;

    public AiReviewingStage(AiReviewService aiReviewService,
                            TaskLogger taskLogger,
                            SystemParamMapper systemParamMapper) {
        this.aiReviewService = aiReviewService;
        this.taskLogger = taskLogger;
        this.systemParamMapper = systemParamMapper;
    }

    @Override
    public ReviewTaskStatus stage() {
        return ReviewTaskStatus.AI_REVIEWING;
    }

    @Override
    public ReviewTaskStatus next(StageContext ctx) {
        long taskId = ctx.taskId();
        try {
            AiReviewOutcome outcome = aiReviewService.execute(taskId);
            if (log.isDebugEnabled()) {
                log.debug("AiReviewingStage: taskId={} aiAvailable={} issues={} score={}",
                        taskId, outcome.aiAvailable(),
                        outcome.issuesPersisted(), outcome.aiRiskScore());
            }
        } catch (RuntimeException ex) {
            // R12.5：AI 失败不阻塞任务流转；仅记 WARN 级 task_log
            taskLogger.warn(taskId, stage().name(),
                    "ai review unexpected failure (non-blocking): "
                            + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()),
                    ex);
        }
        return ReviewTaskStatus.GATE_EVALUATING;
    }

    @Override
    public long timeoutSeconds() {
        return readTimeoutSecondsOrDefault();
    }

    private long readTimeoutSecondsOrDefault() {
        try {
            SystemParam sp = systemParamMapper.selectByKey(PARAM_TIMEOUT);
            if (sp == null || sp.getParamValue() == null) {
                return DEFAULT_TIMEOUT_SECONDS;
            }
            long v = Long.parseLong(sp.getParamValue().trim());
            if (v <= 0) {
                return DEFAULT_TIMEOUT_SECONDS;
            }
            return v;
        } catch (RuntimeException ex) {
            log.warn("AiReviewingStage timeoutSeconds fallback: {}", ex.toString());
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }
}
