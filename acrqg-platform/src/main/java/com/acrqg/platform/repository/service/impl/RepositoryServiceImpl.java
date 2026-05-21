package com.acrqg.platform.repository.service.impl;

import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.common.util.MaskUtils;
import com.acrqg.platform.infra.crypto.TokenEncryptor;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.acrqg.platform.project.repository.ProjectMapper;
import com.acrqg.platform.repository.client.ProviderClient;
import com.acrqg.platform.repository.client.ProviderClientFactory;
import com.acrqg.platform.repository.domain.Provider;
import com.acrqg.platform.repository.domain.RepositoryBinding;
import com.acrqg.platform.repository.dto.ConnectivityResultDTO;
import com.acrqg.platform.repository.dto.RepositoryBindRequest;
import com.acrqg.platform.repository.dto.RepositoryBindingDTO;
import com.acrqg.platform.repository.dto.RepositoryTestRequest;
import com.acrqg.platform.repository.repository.RepositoryBindingMapper;
import com.acrqg.platform.repository.service.RepositoryService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link RepositoryService} 默认实现。
 *
 * <h3>关键策略</h3>
 *
 * <ol>
 *   <li><b>先验证后写</b>：{@link #bind} 在持久化前先调用 ping，避免把不可达
 *       的仓库写入 DB（R5.2）。</li>
 *   <li><b>加密落库</b>：{@code accessToken / webhookSecret} 由
 *       {@link TokenEncryptor} 加密后落库，DTO 永不返回明文（R5.3 / R23.3）。</li>
 *   <li><b>一项目一绑定</b>：依赖 DB 唯一约束 {@code uk_repository_binding_project}
 *       + {@link DuplicateKeyException} 捕获走 UPDATE 路径（R5.6）。Service 内部
 *       先 SELECT 一次拿到 PK，避免触发约束异常（约束仍是兜底）。</li>
 *   <li><b>webhookUrl 计算</b>：使用配置 {@code app.webhook.base-url}（默认
 *       {@code https://localhost:8080}）拼接固定路径 {@code /api/v1/webhooks/git}（R5.5）。</li>
 *   <li><b>审计</b>：发布 {@link AuditEvent} {@code REPOSITORY_BOUND} 或
 *       {@code REPOSITORY_UPDATED}；detail 中的 {@code accessToken /
 *       webhookSecret} 通过 {@link MaskUtils#FULL_MASK} 完全掩码（R22.5 / R23.3）。</li>
 *   <li><b>事务</b>：{@link #bind} 标记 {@code @Transactional}；事件订阅器异步消费，
 *       事务回滚时不会落审计。</li>
 * </ol>
 *
 * <p>Covers: R5.1, R5.2, R5.3, R5.4, R5.5, R5.6, R23.2, R23.3。
 */
@Service
public class RepositoryServiceImpl implements RepositoryService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryServiceImpl.class);

    /** 审计资源类型 / 动作字面量。 */
    private static final String RESOURCE_REPOSITORY = "REPOSITORY";
    private static final String ACTION_BOUND = "REPOSITORY_BOUND";
    private static final String ACTION_UPDATED = "REPOSITORY_UPDATED";

    /** Webhook 入站路径，与 B3-B 的 controller 保持一致。 */
    private static final String WEBHOOK_PATH = "/api/v1/webhooks/git";

    /** {@code repository_binding.status} 默认值，与 V20 的 DEFAULT 列保持一致。 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final RepositoryBindingMapper repositoryBindingMapper;
    private final ProjectMapper projectMapper;
    private final ProviderClientFactory providerClientFactory;
    private final TokenEncryptor tokenEncryptor;
    private final ApplicationEventPublisher eventPublisher;
    private final String webhookBaseUrl;

    public RepositoryServiceImpl(RepositoryBindingMapper repositoryBindingMapper,
                                 ProjectMapper projectMapper,
                                 ProviderClientFactory providerClientFactory,
                                 TokenEncryptor tokenEncryptor,
                                 ApplicationEventPublisher eventPublisher,
                                 @Value("${app.webhook.base-url:https://localhost:8080}") String webhookBaseUrl) {
        this.repositoryBindingMapper = repositoryBindingMapper;
        this.projectMapper = projectMapper;
        this.providerClientFactory = providerClientFactory;
        this.tokenEncryptor = tokenEncryptor;
        this.eventPublisher = eventPublisher;
        this.webhookBaseUrl = normalizeBaseUrl(webhookBaseUrl);
    }

    // ---------------------------------------------------------------------
    // RepositoryService 实现
    // ---------------------------------------------------------------------

    @Override
    public ConnectivityResultDTO test(Long projectId, RepositoryTestRequest request) {
        requireProjectExists(projectId);
        ProviderClient client = providerClientFactory.byProvider(request.provider());
        return client.ping(request);
    }

    @Override
    @Transactional
    public RepositoryBindingDTO bind(Long projectId, RepositoryBindRequest request) {
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();
        requireProjectExists(projectId);

        // 1) 先 ping，不可达直接抛业务异常（R5.2）
        Provider provider = request.provider();
        ProviderClient client = providerClientFactory.byProvider(provider);
        ConnectivityResultDTO ping = client.ping(
                new RepositoryTestRequest(provider, request.repoUrl(), request.accessToken()));
        if (!ping.reachable()) {
            String detail = ping.message() == null || ping.message().isBlank()
                    ? ErrorCode.REPOSITORY_UNREACHABLE.getMessage()
                    : ErrorCode.REPOSITORY_UNREACHABLE.getMessage() + ": " + ping.message();
            throw new BusinessException(ErrorCode.REPOSITORY_UNREACHABLE, detail);
        }

        // 2) 加密敏感字段（R5.3 / R23.2）
        String accessTokenCipher = tokenEncryptor.encrypt(request.accessToken());
        String webhookSecretCipher = tokenEncryptor.encrypt(request.webhookSecret());
        String webhookUrl = webhookBaseUrl + WEBHOOK_PATH;

        // 3) UPSERT：先查再插，DB 唯一约束兜底（R5.6）
        RepositoryBinding existing = repositoryBindingMapper.selectByProjectId(projectId);
        boolean isNew;
        RepositoryBinding entity;
        if (existing == null) {
            entity = new RepositoryBinding();
            entity.setProjectId(projectId);
            entity.setProvider(provider.name());
            entity.setRepoUrl(request.repoUrl());
            entity.setAccessTokenEncrypted(accessTokenCipher);
            entity.setWebhookSecretEncrypted(webhookSecretCipher);
            entity.setWebhookUrl(webhookUrl);
            entity.setStatus(STATUS_ACTIVE);
            entity.setLastCheckedAt(OffsetDateTime.now());
            try {
                repositoryBindingMapper.insert(entity);
                isNew = true;
            } catch (DuplicateKeyException ex) {
                // 并发下另一个事务先插入；回退到 UPDATE 路径
                RepositoryBinding refreshed = repositoryBindingMapper.selectByProjectId(projectId);
                if (refreshed == null) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "仓库绑定写入冲突", ex);
                }
                entity = applyUpdate(refreshed, provider, request,
                        accessTokenCipher, webhookSecretCipher, webhookUrl);
                repositoryBindingMapper.updateById(entity);
                isNew = false;
            }
        } else {
            entity = applyUpdate(existing, provider, request,
                    accessTokenCipher, webhookSecretCipher, webhookUrl);
            repositoryBindingMapper.updateById(entity);
            isNew = false;
        }

        publishAudit(caller, isNew ? ACTION_BOUND : ACTION_UPDATED, entity);

        return toDTO(entity);
    }

    @Override
    public RepositoryBindingDTO get(Long projectId) {
        RepositoryBinding entity = requireBinding(projectId);
        return toDTO(entity);
    }

    @Override
    public String decryptAccessToken(Long projectId) {
        RepositoryBinding entity = requireBinding(projectId);
        return tokenEncryptor.decrypt(entity.getAccessTokenEncrypted());
    }

    @Override
    public String decryptWebhookSecret(Long projectId) {
        RepositoryBinding entity = requireBinding(projectId);
        return tokenEncryptor.decrypt(entity.getWebhookSecretEncrypted());
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private void requireProjectExists(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目不存在");
        }
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目不存在");
        }
    }

    private RepositoryBinding requireBinding(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仓库未绑定");
        }
        RepositoryBinding entity = repositoryBindingMapper.selectByProjectId(projectId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仓库未绑定");
        }
        return entity;
    }

    private static RepositoryBinding applyUpdate(RepositoryBinding existing,
                                                 Provider provider,
                                                 RepositoryBindRequest request,
                                                 String accessTokenCipher,
                                                 String webhookSecretCipher,
                                                 String webhookUrl) {
        existing.setProvider(provider.name());
        existing.setRepoUrl(request.repoUrl());
        existing.setAccessTokenEncrypted(accessTokenCipher);
        existing.setWebhookSecretEncrypted(webhookSecretCipher);
        existing.setWebhookUrl(webhookUrl);
        existing.setStatus(STATUS_ACTIVE);
        existing.setLastCheckedAt(OffsetDateTime.now());
        return existing;
    }

    private void publishAudit(AuthenticatedUser caller, String action, RepositoryBinding entity) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", entity.getProjectId());
        detail.put("provider", entity.getProvider());
        detail.put("repoUrl", entity.getRepoUrl());
        detail.put("webhookUrl", entity.getWebhookUrl());
        // 即使监听器会再做一次 maskJsonNode，这里仍然显式预掩码：审计 detail 永远不写明文（R23.3）。
        detail.put("accessToken", MaskUtils.FULL_MASK);
        detail.put("webhookSecret", MaskUtils.FULL_MASK);
        AuditEvent event = AuditEvent.of(
                caller.id(),
                caller.username(),
                action,
                RESOURCE_REPOSITORY,
                entity.getId() == null ? null : String.valueOf(entity.getId()),
                null,
                detail);
        eventPublisher.publishEvent(event);
        if (log.isDebugEnabled()) {
            log.debug("repository audit published: action={} projectId={} bindingId={}",
                    action, entity.getProjectId(), entity.getId());
        }
    }

    private static RepositoryBindingDTO toDTO(RepositoryBinding e) {
        return new RepositoryBindingDTO(
                e.getId(),
                e.getProjectId(),
                e.getProvider(),
                e.getRepoUrl(),
                e.getWebhookUrl(),
                e.getStatus(),
                e.getLastCheckedAt());
    }

    private static String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "https://localhost:8080";
        }
        String trimmed = raw.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
