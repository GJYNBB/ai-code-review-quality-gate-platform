package com.acrqg.platform.ai.client;

/**
 * AI 评审客户端 SPI（design.md §6.6 / §12.4）。
 *
 * <p>面向 OpenAI 兼容的 chat completions 协议；实现需要：
 * <ul>
 *   <li>使用 {@link AiReviewRequest#timeoutSeconds()} 控制读 / 连超时；</li>
 *   <li>把 5xx / 网络超时映射为
 *       {@link com.acrqg.platform.ai.exception.AiServiceUnavailableException}；</li>
 *   <li>把 4xx 映射为
 *       {@link com.acrqg.platform.common.exception.BusinessException}（错误码
 *       {@code VALIDATION_ERROR}），不进入降级路径；</li>
 *   <li>响应体已通过 JSON Schema 校验（实现可直接调用
 *       {@link com.acrqg.platform.ai.schema.AiReviewSchemaValidator}）。</li>
 * </ul>
 *
 * <p>实现必须线程安全；本 SPI 不做请求节流 / 重试，节流交由 service 层与系统
 * 参数控制。
 *
 * <p>Covers: R12.1, R12.3, R12.4, R12.5。
 */
public interface AiReviewClient {

    /**
     * 发起一次 AI 评审请求。
     *
     * @param req     请求载荷
     * @param apiKey  解密后的 API 明文（仅本次调用使用，不得日志输出）
     * @return AI 响应（已通过 Schema 校验）
     * @throws com.acrqg.platform.ai.exception.AiServiceUnavailableException
     *         5xx / 网络超时
     * @throws com.acrqg.platform.ai.exception.SchemaValidationException
     *         200 但响应不符合 JSON Schema
     * @throws com.acrqg.platform.common.exception.BusinessException
     *         4xx 客户端错误（{@code VALIDATION_ERROR}）
     */
    AiReviewResponse review(AiReviewRequest req, String apiKey);
}
