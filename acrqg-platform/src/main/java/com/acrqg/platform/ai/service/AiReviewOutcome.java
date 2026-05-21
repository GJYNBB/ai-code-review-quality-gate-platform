package com.acrqg.platform.ai.service;

/**
 * AI 评审单次执行结果（{@link AiReviewService#execute(Long)} 返回值）。
 *
 * <p>承载本次 AI 调用的最终结论，供 worker 阶段写 {@code task_log.detail} 与
 * 后续 {@code GateEngine} 评估。三种典型路径：
 * <ul>
 *   <li><b>成功</b>：{@code aiAvailable=true}, {@code issuesPersisted >= 0},
 *       {@code aiRiskScore = clamp(...)}（design.md §12.5）；</li>
 *   <li><b>无可用模型</b>：{@code aiAvailable=false},
 *       {@code issuesPersisted=0}, {@code aiRiskScore=0}（系统未配置 enabled model）；</li>
 *   <li><b>降级</b>：{@code aiAvailable=false}, {@code issuesPersisted=0},
 *       {@code aiRiskScore=0}（5xx / 超时 / 敏感过滤失败 / Schema 校验失败）。</li>
 * </ul>
 *
 * @param aiAvailable     AI 服务在本任务内是否可用
 * @param issuesPersisted 已持久化到 {@code code_issue} 表的 AI 问题数
 * @param aiRiskScore     [0, 100] 风险分；不可用时固定 0
 */
public record AiReviewOutcome(
        boolean aiAvailable,
        int issuesPersisted,
        int aiRiskScore) {

    /** 不可用 / 降级路径下的兜底结果。 */
    public static AiReviewOutcome unavailable() {
        return new AiReviewOutcome(false, 0, 0);
    }
}
