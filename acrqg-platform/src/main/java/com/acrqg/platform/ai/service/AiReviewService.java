package com.acrqg.platform.ai.service;

/**
 * AI 评审服务（design.md §6.6）。
 *
 * <p>外部入口仅一个方法 {@link #execute(Long)}：
 * <ol>
 *   <li>读取任务与 diff（按需过滤 oversized 文件，R10.5）；</li>
 *   <li>选取 enabled 的模型配置；无可用模型时直接返回 {@link AiReviewOutcome#unavailable()}；</li>
 *   <li>构造 {@link com.acrqg.platform.ai.filter.AiReviewPayload} →
 *       调用 {@link com.acrqg.platform.ai.filter.SensitiveFilter} 脱敏；</li>
 *   <li>构造 prompt → 调用 {@link com.acrqg.platform.ai.client.AiReviewClient}；</li>
 *   <li>持久化每条 AI Issue 为 {@code code_issue}（source=AI, status=NEW）；</li>
 *   <li>计算 {@code ai_risk_score} 并写入 {@code review_task}；</li>
 *   <li>发布 {@code AI_REVIEW_COMPLETED} 审计事件，detail 含 outcome 摘要。</li>
 * </ol>
 *
 * <p><b>降级路径</b>（设计 §12.4）：
 * <ul>
 *   <li>{@link com.acrqg.platform.ai.filter.SensitiveFilterFailureException}
 *       → {@code task_log(ERROR)} + {@code aiAvailable=false}；</li>
 *   <li>{@link com.acrqg.platform.ai.exception.AiServiceUnavailableException}
 *       → {@code task_log(WARN)} + {@code aiAvailable=false}；</li>
 *   <li>{@link com.acrqg.platform.ai.exception.SchemaValidationException}
 *       → {@code task_log(WARN)} + 丢弃响应（aiAvailable 仍为 true，issues=0）。</li>
 * </ul>
 *
 * <p>所有降级路径都返回正常 {@link AiReviewOutcome}（不抛异常），由
 * {@code AiReviewingStage} 直接进入 GATE_EVALUATING（R12.5 不阻塞）。
 *
 * <p>Covers: R12.1, R12.2, R12.3, R12.4, R12.5, R12.6。
 */
public interface AiReviewService {

    /**
     * 执行一次 AI 评审。
     *
     * @param taskId 任务主键
     * @return 执行结果（永不为 {@code null}）
     */
    AiReviewOutcome execute(Long taskId);
}
