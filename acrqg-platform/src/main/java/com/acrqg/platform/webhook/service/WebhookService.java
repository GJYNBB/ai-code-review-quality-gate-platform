package com.acrqg.platform.webhook.service;

import com.acrqg.platform.repository.domain.Provider;
import com.acrqg.platform.webhook.dto.WebhookHandleResult;
import org.springframework.http.HttpHeaders;

/**
 * Webhook 处理服务（B3-B.3 / R7）。
 *
 * <p>对齐 design.md §6.3：
 * <pre>
 * public interface WebhookService {
 *     WebhookHandleResultDTO handle(String provider, HttpHeaders headers, String rawBody);
 * }
 * </pre>
 *
 * <p>本接口与设计文档相比把 {@code provider} 参数提升为强类型 {@link Provider}
 * 枚举（控制器在路由阶段已经识别），把返回类型重命名为
 * {@link WebhookHandleResult}（去掉冗余的 {@code DTO} 后缀）。
 *
 * <p>处理流程（实现见 {@code WebhookServiceImpl}）：
 * <ol>
 *   <li>{@code WebhookEventParser.parse} → {@link com.acrqg.platform.webhook.parser.ParsedEvent
 *       ParsedEvent}</li>
 *   <li>{@code eventType ∈ {PING, OTHER}} → 直接返回 {@link WebhookHandleResult#ignored}（R7.5）</li>
 *   <li>按 {@code repoUrl} 查 {@code RepositoryBinding}；不存在抛
 *       {@code BusinessException(VALIDATION_ERROR, "repository not bound")}</li>
 *   <li>解密 webhook secret，调用对应平台的 {@code SignatureVerifier.verify}；
 *       失败抛 {@code BusinessException(WEBHOOK_SIGNATURE_INVALID)}（R7.2）</li>
 *   <li>计算幂等键 {@code idem:webhook:{provider}:{repositoryId}:{eventId}} →
 *       {@code IdempotencyStore.putIfAbsent} TTL 24h（R7.4）</li>
 *   <li>{@code putIfAbsent=false} → 查询已有任务直接返回（命中即返回）</li>
 *   <li>{@code putIfAbsent=true} → 调
 *       {@code ReviewTaskService.create(req, null, TriggerType.WEBHOOK)} 创建任务，
 *       并把 taskId 回写到幂等键 value</li>
 *   <li>整体流程必须在 3s 内返回（R7.6）；耗时操作（实际任务执行）由 Stream
 *       异步消费</li>
 * </ol>
 *
 * <p>所有路径都会发布 {@code AuditEvent}：
 * <ul>
 *   <li>受理 → {@code WEBHOOK_RECEIVED}</li>
 *   <li>签名失败 → {@code WEBHOOK_REJECTED}（detail.reason="signature invalid"）</li>
 * </ul>
 *
 * <p>Covers: R7.1, R7.2, R7.3, R7.4, R7.5, R7.6。
 */
public interface WebhookService {

    /**
     * 处理一次入站 webhook。
     *
     * @param provider 由控制器根据请求头识别的代码托管平台
     * @param headers  HTTP 请求头集合
     * @param rawBody  HTTP 原始请求体（必须保持字节级别原样，因 GitHub 计算 HMAC 依赖）
     * @return 处理结果；忽略时 {@link WebhookHandleResult#ignored} 为 {@code true}
     */
    WebhookHandleResult handle(Provider provider, HttpHeaders headers, byte[] rawBody);
}
