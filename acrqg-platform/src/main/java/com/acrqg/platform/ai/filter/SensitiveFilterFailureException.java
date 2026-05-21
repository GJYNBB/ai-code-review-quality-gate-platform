package com.acrqg.platform.ai.filter;

/**
 * SensitiveFilter 抛出的"无效过滤"异常。
 *
 * <p>触发条件（design.md §12.3 第 3 道闸）：原始载荷命中至少一条过滤规则
 * （路径白名单或 Token 正则），但执行替换后整体 SHA-256 哈希仍与脱敏前一致——
 * 这通常意味着实现存在缺陷或正则失效，<b>不能</b>把"未真正脱敏"的内容送入 AI。
 *
 * <p>由 {@code AiReviewService} 捕获后：
 * <ul>
 *   <li>写 {@code task_log(level=ERROR, stage=AI_REVIEWING)}；</li>
 *   <li>把任务的 {@code ai_available=false}；</li>
 *   <li>不抛出业务异常，让任务继续推进至 GATE_EVALUATING（R12.5 降级路径）。</li>
 * </ul>
 *
 * <p>Covers: R12.2, R23.4。
 */
public class SensitiveFilterFailureException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SensitiveFilterFailureException(String message) {
        super(message);
    }

    public SensitiveFilterFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
