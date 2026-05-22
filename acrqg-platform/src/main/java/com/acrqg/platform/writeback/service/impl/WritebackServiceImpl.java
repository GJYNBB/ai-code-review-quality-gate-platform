package com.acrqg.platform.writeback.service.impl;

import com.acrqg.platform.gate.domain.GateResult;
import com.acrqg.platform.gate.dto.GateResultSummary;
import com.acrqg.platform.gate.repository.GateResultMapper;
import com.acrqg.platform.repository.client.ProviderClient;
import com.acrqg.platform.repository.client.ProviderClientFactory;
import com.acrqg.platform.repository.domain.Provider;
import com.acrqg.platform.repository.domain.RepositoryBinding;
import com.acrqg.platform.repository.dto.CommitStatusRequest;
import com.acrqg.platform.repository.dto.CommitStatusState;
import com.acrqg.platform.repository.repository.RepositoryBindingMapper;
import com.acrqg.platform.repository.service.RepositoryService;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.log.TaskLogger;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.acrqg.platform.writeback.config.WritebackAsyncConfig;
import com.acrqg.platform.writeback.event.WritebackFailedEvent;
import com.acrqg.platform.writeback.exception.WritebackException;
import com.acrqg.platform.writeback.service.WritebackService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * {@link WritebackService} 默认实现。
 *
 * <h3>核心流程</h3>
 *
 * <ol>
 *   <li>取 {@link ReviewTask} → 校验状态是 PASSED / FAILED_GATE / EXECUTION_FAILED；
 *       否则跳过（PENDING/中间态不回写）。</li>
 *   <li>取 {@link RepositoryBinding} 反查 provider；未绑定时跳过并写 task_log(WARN)。</li>
 *   <li>取 {@link GateResult}（可空——EXECUTION_FAILED 任务可能没有 gate_result）。</li>
 *   <li>映射 state：
 *       <ul>
 *         <li>PASSED → SUCCESS（含豁免 WAIVED 场景）；</li>
 *         <li>FAILED_GATE → FAILURE；</li>
 *         <li>EXECUTION_FAILED → ERROR；</li>
 *       </ul>
 *   </li>
 *   <li>构造 description：{@code "score=" + score + ", critical=" + criticalCount}（取自
 *       {@link GateResultSummary}.metricValues）；豁免态时拼上"已豁免"前缀（R15.4）。</li>
 *   <li>构造 targetUrl：{@code {frontend.base.url}/review-tasks/{taskId}}。</li>
 *   <li>调 ProviderClient.postCommitStatus；4xx 立即失败，5xx / 网络异常按
 *       1s/2s/4s 三次指数退避（R20.3 / R20.4）；最终失败仅写 task_log(ERROR) +
 *       发布 {@link WritebackFailedEvent}，<b>不</b>抛异常向上。</li>
 * </ol>
 *
 * <p><b>幂等</b>：本服务在每次调用都会重新计算 state / description / targetUrl，不依赖
 * 上次回写结果；ProviderClient.postCommitStatus 在 GitHub/GitLab/Gitee 都是
 * 同 sha + context 覆盖语义。
 *
 * <p>Covers: R14.6, R14.7, R20.1, R20.2, R20.3, R20.4。
 */
@Service
public class WritebackServiceImpl implements WritebackService {

    private static final Logger log = LoggerFactory.getLogger(WritebackServiceImpl.class);

    /** 重试次数（最多）。 */
    static final int MAX_RETRY = 3;

    /** 指数退避基础延迟（毫秒）；序列为 1s / 2s / 4s。 */
    static final long BASE_BACKOFF_MILLIS = 1_000L;

    /** Commit status context 标识，与 ReviewTask 关联。 */
    static final String STATUS_CONTEXT = "acrqg/quality-gate";

    /** 终态合法集合：仅这些状态会触发回写。 */
    private static final java.util.Set<String> WRITEBACK_STATUSES = java.util.Set.of(
            ReviewTaskStatus.PASSED.name(),
            ReviewTaskStatus.FAILED_GATE.name(),
            ReviewTaskStatus.EXECUTION_FAILED.name());

    /** 任务 stage 字面量（写 task_log 时使用）。 */
    private static final String STAGE = "WRITEBACK";

    private final ReviewTaskMapper reviewTaskMapper;
    private final RepositoryBindingMapper repositoryBindingMapper;
    private final GateResultMapper gateResultMapper;
    private final ProviderClientFactory providerClientFactory;
    private final RepositoryService repositoryService;
    private final TaskLogger taskLogger;
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendBaseUrl;

    public WritebackServiceImpl(ReviewTaskMapper reviewTaskMapper,
                                 RepositoryBindingMapper repositoryBindingMapper,
                                 GateResultMapper gateResultMapper,
                                 ProviderClientFactory providerClientFactory,
                                 RepositoryService repositoryService,
                                 TaskLogger taskLogger,
                                 ApplicationEventPublisher eventPublisher,
                                 @Value("${frontend.base.url:https://acrqg.local}") String frontendBaseUrl) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.repositoryBindingMapper = repositoryBindingMapper;
        this.gateResultMapper = gateResultMapper;
        this.providerClientFactory = providerClientFactory;
        this.repositoryService = repositoryService;
        this.taskLogger = taskLogger;
        this.eventPublisher = eventPublisher;
        this.frontendBaseUrl = normalizeBaseUrl(frontendBaseUrl);
    }

    // =====================================================================
    // public API
    // =====================================================================

    @Override
    public void writeback(Long taskId) {
        if (taskId == null) {
            log.warn("writeback called with null taskId");
            return;
        }
        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("writeback: task not found, taskId={}", taskId);
            return;
        }

        // 1) 状态门：仅终态可回写
        String status = task.getStatus();
        if (status == null || !WRITEBACK_STATUSES.contains(status)) {
            taskLogger.info(taskId, STAGE,
                    "writeback skipped: task not in writeback-eligible status (status="
                            + status + ")");
            return;
        }

        // 2) commit sha 必须有
        String commitSha = task.getCommitSha();
        if (commitSha == null || commitSha.isBlank()) {
            taskLogger.warn(taskId, STAGE,
                    "writeback skipped: task has no commitSha");
            return;
        }

        // 3) 仓库绑定
        RepositoryBinding binding = repositoryBindingMapper.selectByProjectId(task.getProjectId());
        if (binding == null) {
            taskLogger.warn(taskId, STAGE,
                    "writeback skipped: project has no repository binding (projectId="
                            + task.getProjectId() + ")");
            return;
        }
        Provider provider;
        try {
            provider = Provider.valueOf(binding.getProvider());
        } catch (IllegalArgumentException ex) {
            taskLogger.error(taskId, STAGE,
                    "writeback skipped: unknown provider in binding=" + binding.getProvider(), ex);
            return;
        }

        // 4) GateResult（可空）
        GateResult gate = gateResultMapper.findByTaskId(taskId);

        // 5) 计算 state / description / targetUrl
        CommitStatusState state = mapState(status, gate);
        if (state == null) {
            taskLogger.info(taskId, STAGE,
                    "writeback skipped: state mapping returned null for status=" + status);
            return;
        }
        String description = buildDescription(task, gate);
        String targetUrl = frontendBaseUrl + "/review-tasks/" + taskId;

        CommitStatusRequest request = new CommitStatusRequest(
                provider,
                binding.getRepoUrl(),
                commitSha,
                state,
                description,
                STATUS_CONTEXT,
                targetUrl);

        // 6) 解密 token
        final String token;
        try {
            token = repositoryService.decryptAccessToken(task.getProjectId());
        } catch (RuntimeException ex) {
            taskLogger.error(taskId, STAGE,
                    "writeback failed: unable to decrypt access token", ex);
            eventPublisher.publishEvent(new WritebackFailedEvent(taskId, "token-decrypt-failed"));
            return;
        }

        // 7) 调用 + 重试
        ProviderClient client = providerClientFactory.byProvider(provider);
        WritebackException lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                client.postCommitStatus(request, token);
                taskLogger.info(taskId, STAGE,
                        "writeback successful: provider=" + provider.name()
                                + " state=" + state.name() + " attempt=" + attempt);
                return;
            } catch (WritebackException ex) {
                lastError = ex;
                if (ex.isClientError()) {
                    // 4xx：不重试（R20.3）
                    taskLogger.error(taskId, STAGE,
                            "writeback failed (4xx, no retry): " + ex.getMessage(), ex);
                    eventPublisher.publishEvent(new WritebackFailedEvent(taskId, ex.getMessage()));
                    return;
                }
                // 5xx / 网络异常：按指数退避重试
                if (attempt < MAX_RETRY) {
                    long sleepMs = BASE_BACKOFF_MILLIS * (1L << (attempt - 1)); // 1s, 2s, 4s
                    taskLogger.warn(taskId, STAGE,
                            "writeback attempt=" + attempt + " failed, retry after " + sleepMs
                                    + "ms: " + ex.getMessage());
                    if (!sleepQuietly(sleepMs)) {
                        // 线程被中断；记录后退出
                        taskLogger.warn(taskId, STAGE, "writeback interrupted during backoff");
                        eventPublisher.publishEvent(new WritebackFailedEvent(taskId,
                                "interrupted: " + ex.getMessage()));
                        return;
                    }
                }
            } catch (RuntimeException ex) {
                // ProviderClient 不应抛 WritebackException 之外的异常；防御性兜底
                taskLogger.error(taskId, STAGE,
                        "writeback unexpected exception (no retry): " + ex.toString(), ex);
                eventPublisher.publishEvent(new WritebackFailedEvent(taskId, ex.toString()));
                return;
            }
        }

        // 三次重试用尽
        String reason = lastError == null ? "unknown" : lastError.getMessage();
        taskLogger.error(taskId, STAGE,
                "writeback failed after " + MAX_RETRY + " retries: " + reason, lastError);
        eventPublisher.publishEvent(new WritebackFailedEvent(taskId, reason));
    }

    @Override
    @Async(WritebackAsyncConfig.EXECUTOR_BEAN_NAME)
    public void writebackAsync(Long taskId) {
        // 简单代理；@Async 让本调用在 writebackTaskExecutor 线程池执行
        writeback(taskId);
    }

    // =====================================================================
    // helpers
    // =====================================================================

    /**
     * 把 ReviewTask.status + GateResult 映射为 commit status state。
     *
     * <ul>
     *   <li>PASSED → SUCCESS（gate 为 WAIVED 时同样，由 description 区分）；</li>
     *   <li>FAILED_GATE → FAILURE；</li>
     *   <li>EXECUTION_FAILED → ERROR；</li>
     *   <li>其他 → null（跳过）。</li>
     * </ul>
     */
    static CommitStatusState mapState(String taskStatus, GateResult gate) {
        if (taskStatus == null) {
            return null;
        }
        return switch (taskStatus) {
            case "PASSED" -> CommitStatusState.SUCCESS;
            case "FAILED_GATE" -> CommitStatusState.FAILURE;
            case "EXECUTION_FAILED" -> CommitStatusState.ERROR;
            default -> null;
        };
    }

    /**
     * 构造 commit status description。
     *
     * <p>格式：
     * <pre>
     *   PASSED 普通  ：score=80, critical=0
     *   PASSED 豁免  ：已豁免：score=60, critical=2
     *   FAILED_GATE  ：score=60, critical=2
     *   EXECUTION_FAILED：execution failed
     * </pre>
     *
     * <p>结尾会被各 ProviderClient 截断到平台限长（GitHub/Gitee 140 / GitLab 255）。
     */
    String buildDescription(ReviewTask task, GateResult gate) {
        if (ReviewTaskStatus.EXECUTION_FAILED.name().equals(task.getStatus())) {
            return "execution failed";
        }
        Integer score = gate != null && gate.getScore() != null
                ? gate.getScore()
                : task.getScore();
        Integer criticalCount = extractCriticalCount(gate);
        StringBuilder sb = new StringBuilder();
        if (gate != null && "WAIVED".equals(gate.getStatus())) {
            sb.append("已豁免：");
        }
        sb.append("score=").append(score == null ? "-" : score)
                .append(", critical=").append(criticalCount == null ? "-" : criticalCount);
        return sb.toString();
    }

    /** 从 {@code GateResultSummary.metricValues.critical_issue_count} 提取整数；缺失返回 null。 */
    private Integer extractCriticalCount(GateResult gate) {
        if (gate == null || gate.getSummary() == null || gate.getSummary().isBlank()) {
            return null;
        }
        try {
            GateResultSummary summary = com.acrqg.platform.common.util.JsonUtils.fromJson(
                    gate.getSummary(), new TypeReference<GateResultSummary>() {});
            if (summary == null || summary.metricValues() == null) {
                return null;
            }
            java.math.BigDecimal v = summary.metricValues().get("critical_issue_count");
            if (v == null) {
                return null;
            }
            return v.intValueExact();
        } catch (RuntimeException ex) {
            log.debug("extractCriticalCount: parse summary failed taskId={} err={}",
                    gate.getTaskId(), ex.toString());
            return null;
        }
    }

    /**
     * 安静睡眠：被中断时返回 {@code false}，并恢复中断标志。
     */
    private static boolean sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "https://acrqg.local";
        }
        String trimmed = raw.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
