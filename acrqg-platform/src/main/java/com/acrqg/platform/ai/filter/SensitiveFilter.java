package com.acrqg.platform.ai.filter;

/**
 * AI 评审敏感信息过滤器（design.md §12.3 / requirements R12.2 / R23.4）。
 *
 * <p>在把任意"变更文本"送入 AI 之前，必须先过滤可能包含的密钥、Token、密码等
 * 敏感信息。本接口的 {@link #filter(AiReviewPayload)} 实现"三道闸"语义：
 * <ol>
 *   <li>路径白名单：跳过整文件（如 {@code .env} / {@code *.pem}）；</li>
 *   <li>Token 正则替换：把已知敏感模式替换为 {@code "***REDACTED***"}；</li>
 *   <li>哈希前后比对：raw 命中过滤规则但脱敏后哈希未变化时，
 *       抛 {@link SensitiveFilterFailureException}，由调用方写
 *       {@code task_log(level=ERROR)} 并把任务的 {@code aiAvailable=false}
 *       （AI 调用必须中止，避免敏感数据外泄）。</li>
 * </ol>
 *
 * <p>实现类必须线程安全，因为同一 bean 会被 worker 线程池中多个任务并发调用。
 *
 * <p>Covers: R12.2, R23.4。
 */
public interface SensitiveFilter {

    /**
     * 过滤原始载荷。
     *
     * @param raw 由 {@code AiReviewService} 构造的原始载荷
     * @return 已脱敏的载荷快照（含 {@code wasFiltered} 标志）
     * @throws SensitiveFilterFailureException 当命中过滤规则但脱敏哈希未变化时抛出
     * @throws IllegalArgumentException        raw 为 {@code null}
     */
    FilteredPayload filter(AiReviewPayload raw);
}
