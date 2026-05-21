package com.acrqg.platform.task.worker;

import com.acrqg.platform.task.domain.ReviewTaskStatus;

/**
 * 评审任务阶段 SPI（design.md §6.3.1 自实现轻量 State Pattern）。
 *
 * <p>每个状态对应一个 {@link TaskStage} bean：
 * <ul>
 *   <li>{@link FetchingDiffStage}  → {@link ReviewTaskStatus#FETCHING_DIFF}</li>
 *   <li>{@link StaticScanningStage} → {@link ReviewTaskStatus#STATIC_SCANNING}</li>
 *   <li>{@link AiReviewingStage}   → {@link ReviewTaskStatus#AI_REVIEWING}</li>
 *   <li>{@link GateEvaluatingStage} → {@link ReviewTaskStatus#GATE_EVALUATING}</li>
 * </ul>
 *
 * <p>{@link TaskOrchestrator} 在 {@code @PostConstruct} 中构建
 * {@code Map<ReviewTaskStatus, TaskStage>}：以 {@link #stage()} 为键，bean 实例为值。
 * 运行时按当前任务状态查表分发；终态（{@link ReviewTaskStatus#PASSED} /
 * {@link ReviewTaskStatus#FAILED_GATE} / {@link ReviewTaskStatus#EXECUTION_FAILED}）
 * 不需要 stage 实现，由 Orchestrator 直接终止。
 *
 * <p><b>错误处理约定</b>：
 * <ul>
 *   <li>{@link #next(StageContext)} 返回值表示"下一步状态"，不必与 {@link #stage()}
 *       下一个相邻状态严格对应（例如 AI 阶段在降级路径仍然返回 GATE_EVALUATING）。</li>
 *   <li>实现可以抛出任意 {@link RuntimeException}；Orchestrator 会捕获并把任务置为
 *       {@link ReviewTaskStatus#EXECUTION_FAILED}（R9.2）。</li>
 *   <li>实现应在内部对子任务超时做防护；本接口的 {@link #timeoutSeconds()}
 *       仅作为参考值（默认 120 s），Orchestrator 在 B3-A 阶段不强制中断。</li>
 * </ul>
 *
 * <p>Covers: R9.1, R9.2, R10, R11, R12, R14。
 */
public interface TaskStage {

    /**
     * 该实现关联的状态。Orchestrator 用此值建立分发表。
     */
    ReviewTaskStatus stage();

    /**
     * 执行该阶段并返回下一步要进入的状态。
     *
     * <p>返回值必须是合法的迁移目标（参见
     * {@link com.acrqg.platform.task.domain.StateMachine#tryTransit(ReviewTaskStatus, ReviewTaskStatus)}），
     * 否则 Orchestrator 调用 {@code transitTo} 时会抛
     * {@code BusinessException(VALIDATION_ERROR)}，并被统一转为 EXECUTION_FAILED。
     *
     * @param ctx 阶段执行上下文
     * @return 下一步状态
     */
    ReviewTaskStatus next(StageContext ctx);

    /**
     * 阶段超时秒数（默认 120 s）。
     *
     * <p>子类可覆盖以读取 {@code system_param} 中的更细粒度配置（例如
     * {@code diff.fetch.timeout.seconds}、{@code ai.review.timeout.seconds}）。
     */
    default long timeoutSeconds() {
        return 120L;
    }
}
