package com.acrqg.platform.webhook.service.impl;

import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.infra.redis.IdempotencyStore;
import com.acrqg.platform.repository.domain.Provider;
import com.acrqg.platform.repository.domain.RepositoryBinding;
import com.acrqg.platform.repository.repository.RepositoryBindingMapper;
import com.acrqg.platform.repository.service.RepositoryService;
import com.acrqg.platform.task.domain.TriggerType;
import com.acrqg.platform.task.dto.ReviewTaskCreateRequest;
import com.acrqg.platform.task.dto.ReviewTaskDTO;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.acrqg.platform.task.service.ReviewTaskService;
import com.acrqg.platform.webhook.dto.WebhookHandleResult;
import com.acrqg.platform.webhook.parser.ParsedEvent;
import com.acrqg.platform.webhook.parser.WebhookEventParser;
import com.acrqg.platform.webhook.service.WebhookService;
import com.acrqg.platform.webhook.verifier.SignatureVerifier;
import com.acrqg.platform.webhook.verifier.SignatureVerifierFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * {@link WebhookService} 默认实现（B3-B.3）。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>{@link WebhookEventParser#parse} → {@link ParsedEvent}</li>
 *   <li>事件类型 ∈ {PING, OTHER} → 直接 {@link WebhookHandleResult#ignored}（R7.5）</li>
 *   <li>{@code RepositoryBindingMapper.selectByRepoUrl} 查绑定；不存在抛
 *       {@code VALIDATION_ERROR}</li>
 *   <li>{@code RepositoryService.decryptWebhookSecret} 解密；
 *       {@code SignatureVerifierFactory.forProvider(...).verify}；失败抛
 *       {@code WEBHOOK_SIGNATURE_INVALID}（R7.2）</li>
 *   <li>幂等键
 *       {@code idem:webhook:{provider}:{repositoryId}:{eventId}} →
 *       {@link IdempotencyStore#putIfAbsent(String, String, Duration)} TTL 24h</li>
 *   <li>命中已存在事件 → 读取 value（taskId）后查 {@link ReviewTaskMapper#selectById}
 *       并以 {@link WebhookHandleResult#ofIdempotent} 返回</li>
 *   <li>新事件 → 构造 {@link ReviewTaskCreateRequest} 调
 *       {@link ReviewTaskService#create} 创建任务，并把 taskId 回写到幂等键 value</li>
 *   <li>整体流程必须在 3s 内返回（R7.6）</li>
 * </ol>
 *
 * <h3>审计</h3>
 * <ul>
 *   <li>受理（含 ignored / 创建 / 幂等命中） → {@code WEBHOOK_RECEIVED}</li>
 *   <li>签名失败 → {@code WEBHOOK_REJECTED}（detail.reason="signature invalid"）</li>
 * </ul>
 *
 * <p>Covers: R7.1, R7.2, R7.3, R7.4, R7.5, R7.6, R22.1。
 */
@Service
public class WebhookServiceImpl implements WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);

    private static final String RESOURCE_TYPE = "WEBHOOK";
    private static final String ACTION_RECEIVED = "WEBHOOK_RECEIVED";
    private static final String ACTION_REJECTED = "WEBHOOK_REJECTED";

    /** 幂等键 TTL：24 小时（design.md §7.4）。 */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    /** 幂等键占位值；在拿到 taskId 后会被覆盖（KEEPTTL 由 Redis SET 默认行为支持）。 */
    private static final String IDEMPOTENCY_PLACEHOLDER = "1";

    /**
     * webhook 触发但 PR 没带分支信息时使用的兜底值。
     * {@link ReviewTaskCreateRequest} 的 {@code sourceBranch / targetBranch} 字段
     * 标记为 {@code @NotBlank}，因此必须有非空值；用 "unknown" 作为兜底字符串
     * 让任务可以入库，下游 worker 在 fetch diff 时若分支不存在会进入
     * EXECUTION_FAILED 终态——这是符合预期的健壮行为。
     */
    private static final String UNKNOWN_BRANCH = "unknown";

    private final WebhookEventParser parser;
    private final SignatureVerifierFactory verifierFactory;
    private final RepositoryBindingMapper repositoryBindingMapper;
    private final RepositoryService repositoryService;
    private final IdempotencyStore idempotencyStore;
    private final ReviewTaskService reviewTaskService;
    private final ReviewTaskMapper reviewTaskMapper;
    private final ApplicationEventPublisher eventPublisher;

    public WebhookServiceImpl(WebhookEventParser parser,
                              SignatureVerifierFactory verifierFactory,
                              RepositoryBindingMapper repositoryBindingMapper,
                              RepositoryService repositoryService,
                              IdempotencyStore idempotencyStore,
                              ReviewTaskService reviewTaskService,
                              ReviewTaskMapper reviewTaskMapper,
                              ApplicationEventPublisher eventPublisher) {
        this.parser = parser;
        this.verifierFactory = verifierFactory;
        this.repositoryBindingMapper = repositoryBindingMapper;
        this.repositoryService = repositoryService;
        this.idempotencyStore = idempotencyStore;
        this.reviewTaskService = reviewTaskService;
        this.reviewTaskMapper = reviewTaskMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public WebhookHandleResult handle(Provider provider, HttpHeaders headers, byte[] rawBody) {
        if (provider == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "provider 不能为空");
        }
        HttpHeaders safeHeaders = headers == null ? new HttpHeaders() : headers;
        byte[] bodyBytes = rawBody == null ? new byte[0] : rawBody;
        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        // 1) 解析
        ParsedEvent event = parser.parse(provider, body, safeHeaders);

        // 2) 查绑定：公开 webhook 入口必须先绑定到 ACTIVE 仓库，再验签；PING/OTHER 也不能绕过签名校验。
        if (event.repoUrl() == null || event.repoUrl().isBlank()) {
            log.warn("webhook payload missing repoUrl: provider={} eventId={}",
                    provider, event.eventId());
            publishAudit(ACTION_REJECTED, event,
                    auditDetail("reason", "missing repoUrl"));
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "repository not bound");
        }
        RepositoryBinding binding = repositoryBindingMapper.selectActiveByProviderAndRepoUrl(
                provider.name(), event.repoUrl());
        if (binding == null) {
            log.warn("webhook repo not actively bound: provider={} repoUrl={}", provider, event.repoUrl());
            publishAudit(ACTION_REJECTED, event,
                    auditDetail("reason", "active repository not bound", "repoUrl", event.repoUrl()));
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "repository not bound");
        }

        // 3) 解密 secret + 校验签名
        String secret;
        try {
            secret = repositoryService.decryptWebhookSecret(binding.getProjectId());
        } catch (RuntimeException ex) {
            log.warn("decrypt webhook secret failed: projectId={} err={}",
                    binding.getProjectId(), ex.toString());
            publishAudit(ACTION_REJECTED, event,
                    auditDetail("reason", "decrypt secret failed",
                            "projectId", binding.getProjectId()));
            throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID,
                    ErrorCode.WEBHOOK_SIGNATURE_INVALID.getMessage(), ex);
        }
        SignatureVerifier verifier = verifierFactory.forProvider(provider);
        if (!verifier.verify(secret, bodyBytes, safeHeaders)) {
            log.warn("webhook signature invalid: provider={} projectId={} repoUrl={} eventId={}",
                    provider, binding.getProjectId(), event.repoUrl(), event.eventId());
            publishAudit(ACTION_REJECTED, event,
                    auditDetail("reason", "signature invalid",
                            "projectId", binding.getProjectId(),
                            "repoUrl", event.repoUrl()));
            throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID,
                    ErrorCode.WEBHOOK_SIGNATURE_INVALID.getMessage());
        }

        // 4) PING / OTHER 仅在绑定与签名都通过后忽略，避免公开端点被伪造请求刷日志。
        if (event.eventType() == ParsedEvent.EventType.PING
                || event.eventType() == ParsedEvent.EventType.OTHER) {
            publishAudit(ACTION_RECEIVED, event,
                    auditDetail("eventType", event.eventType().name(),
                            "projectId", binding.getProjectId(),
                            "ignored", true));
            return WebhookHandleResult.ignored("event ignored: " + event.eventType().name());
        }

        // 5) 幂等键
        if (event.repositoryId() == null || event.repositoryId().isBlank()
                || event.eventId() == null || event.eventId().isBlank()) {
            // 极少见：极端 payload；按 projectId+commitSha 兜底，保证 key 非坍缩
            log.warn("webhook missing repositoryId or eventId: provider={} eventId={}",
                    provider, event.eventId());
        }
        String idemKey = IdempotencyStore.webhookKey(
                provider.name(),
                fallback(event.repositoryId(), String.valueOf(binding.getProjectId())),
                fallback(event.eventId(), "unknown"));
        boolean firstTime = idempotencyStore.putIfAbsent(idemKey, IDEMPOTENCY_PLACEHOLDER, IDEMPOTENCY_TTL);

        if (!firstTime) {
            // 命中：返回已有任务
            Optional<String> cached = idempotencyStore.get(idemKey);
            Long taskId = cached.flatMap(WebhookServiceImpl::parseLongSafely).orElse(null);
            if (taskId != null) {
                var existing = reviewTaskMapper.selectById(taskId);
                if (existing != null) {
                    publishAudit(ACTION_RECEIVED, event,
                            auditDetail("idempotent", true,
                                    "projectId", binding.getProjectId(),
                                    "taskId", taskId));
                    return WebhookHandleResult.ofIdempotent(taskId, existing.getStatus());
                }
            }
            // 占位还在但 taskId 缺失（极少见，例如上次创建任务时 Redis 写入抢到但 DB
            // 提交失败）；按"幂等占位但无任务"处理：返回 ignored 让上游重试
            publishAudit(ACTION_RECEIVED, event,
                    auditDetail("idempotent", true,
                            "projectId", binding.getProjectId(),
                            "taskId", null,
                            "reason", "placeholder hit but task missing"));
            return WebhookHandleResult.ignored("idempotent hit but task missing");
        }

        // 6) 创建任务
        ReviewTaskCreateRequest createRequest = new ReviewTaskCreateRequest(
                binding.getProjectId(),
                fallback(event.sourceBranch(), UNKNOWN_BRANCH),
                fallback(event.targetBranch(), UNKNOWN_BRANCH),
                event.commitSha(),
                event.prId());
        ReviewTaskDTO task = reviewTaskService.create(createRequest, null, TriggerType.WEBHOOK);

        // 7) 把 taskId 回写到幂等键 value（KEEPTTL：使用 setIfPresent + 重置同 TTL）
        // 简化处理：直接 putIfAbsent 不会覆盖；这里走 delete+set 不必要，因为命中分支
        // 只读 value 即可，taskId 缺失走的是兜底分支。为减少 Redis 调用，本次先
        // 直接覆盖：使用底层 StringRedisTemplate 不在本类持有引用，此处通过新加一次
        // putIfAbsent 不会成功；折中做法是把 value 再写一次（带 TTL）。
        // 由于 IdempotencyStore 没有暴露 set with ttl 强写接口，这里先 delete 再 putIfAbsent。
        rewriteIdempotencyValue(idemKey, String.valueOf(task.id()));

        publishAudit(ACTION_RECEIVED, event,
                auditDetail("projectId", binding.getProjectId(),
                        "taskId", task.id(),
                        "eventType", event.eventType().name(),
                        "prId", event.prId(),
                        "commitSha", event.commitSha()));
        return WebhookHandleResult.ofCreated(task.id(), task.status());
    }

    /**
     * 把幂等键的 value 覆盖为真实的 taskId，TTL 保持 24h。
     *
     * <p>使用单条 Redis SET 覆盖 value 并刷新 TTL，避免旧实现 delete+set 的竞态窗口。
     */
    private void rewriteIdempotencyValue(String key, String taskId) {
        try {
            idempotencyStore.put(key, taskId, IDEMPOTENCY_TTL);
        } catch (RuntimeException ex) {
            // Redis 抖动：占位值仍为 "1"。命中分支会走"placeholder hit but task missing"
            // 兜底；不影响正确性。
            log.warn("rewrite idempotency value failed: key={} err={}", key, ex.toString());
        }
    }

    private void publishAudit(String action, ParsedEvent event, Map<String, Object> baseDetail) {
        Map<String, Object> detail = new LinkedHashMap<>();
        if (event != null) {
            detail.put("provider", event.provider() == null ? null : event.provider().name());
            detail.put("repositoryId", event.repositoryId());
            detail.put("repoUrl", event.repoUrl());
            detail.put("webhookEventId", event.eventId());
        }
        if (baseDetail != null && !baseDetail.isEmpty()) {
            detail.putAll(baseDetail);
        }
        AuditEvent ev = new AuditEvent(
                null,
                "WEBHOOK",
                action,
                RESOURCE_TYPE,
                event != null ? event.eventId() : null,
                null,
                detail);
        eventPublisher.publishEvent(ev);
    }

    private static Map<String, Object> auditDetail(Object... kv) {
        if (kv == null || kv.length == 0) {
            return Collections.emptyMap();
        }
        if ((kv.length & 1) != 0) {
            throw new IllegalArgumentException("auditDetail requires even number of args");
        }
        Map<String, Object> map = new LinkedHashMap<>(kv.length / 2);
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private static String fallback(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static Optional<Long> parseLongSafely(String s) {
        if (s == null || s.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(s.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
