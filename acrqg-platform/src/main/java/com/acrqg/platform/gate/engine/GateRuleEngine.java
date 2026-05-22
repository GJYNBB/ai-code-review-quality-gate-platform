package com.acrqg.platform.gate.engine;

import com.acrqg.platform.gate.dto.GateResultDTO;

/**
 * 质量门禁规则引擎（design.md §10）。
 *
 * <p>组合 {@link com.acrqg.platform.gate.collector.MetricCollectorRegistry} +
 * {@link OperatorEvaluator}，在 {@code GATE_EVALUATING} 阶段被
 * {@link com.acrqg.platform.task.worker.GateEvaluatingStage} 调用。
 *
 * <p>实现职责：
 * <ol>
 *   <li>读取项目当前 enabled 的 {@code QualityGate}；</li>
 *   <li>遍历所有 {@code enabled=true} 的 {@code GateRule}：采集 actual → 比较 → 收集 RuleEval；</li>
 *   <li>聚合状态：任一 BLOCKER 失败 → {@code FAILED}，否则 {@code PASSED}；
 *       无任何 enabled 规则时 → {@code PASSED} 并写一条 WARN task_log；</li>
 *   <li>计算 score；</li>
 *   <li>持久化 {@code gate_result}（upsert）；</li>
 *   <li>同步 {@code review_task.ai_risk_score / ai_available} 字段。</li>
 * </ol>
 *
 * <p>所有未预期异常向上抛出，由 {@link com.acrqg.platform.task.worker.TaskOrchestrator}
 * 统一捕获并把任务转 EXECUTION_FAILED。
 *
 * <p>Covers: R13.6, R14.1, R14.2, R14.3, R14.4, R14.5, R14.8, R12.6, R17.6。
 */
public interface GateRuleEngine {

    /**
     * 评估指定任务的门禁判定结果，并将结果写入 {@code gate_result} 表。
     *
     * @param taskId 任务主键
     * @return 当前评估的 {@link GateResultDTO}（已包含 summary 与 score）
     */
    GateResultDTO evaluate(Long taskId);
}
