package com.acrqg.platform.ai.exception;

/**
 * AI 评审服务不可用（5xx / 网络超时 / 连接失败）。
 *
 * <p>由 {@link com.acrqg.platform.ai.client.HttpAiReviewClient} 在以下场景抛出：
 * <ul>
 *   <li>HTTP 5xx 服务端错误；</li>
 *   <li>{@code SocketTimeoutException} / 读超时（受 {@code ai.review.timeout.seconds} 控制）；</li>
 *   <li>{@code UnknownHostException} / 无法连接 AI 网关。</li>
 * </ul>
 *
 * <p>语义上等价于"<b>暂时性</b>失败"。{@code AiReviewService} 捕获后：
 * <ol>
 *   <li>写 {@code task_log(level=WARN, stage=AI_REVIEWING)}；</li>
 *   <li>把任务的 {@code ai_available=false}；</li>
 *   <li>不抛出业务异常，让任务继续推进至 GATE_EVALUATING（R12.5 降级路径）。</li>
 * </ol>
 *
 * <p>4xx 客户端错误（鉴权失败、参数非法）<b>不</b>使用本异常；
 * 由调用层抛 {@code BusinessException(VALIDATION_ERROR)} 或同级业务异常。
 *
 * <p>Covers: R12.5。
 */
public class AiServiceUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AiServiceUnavailableException(String message) {
        super(message);
    }

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
