package com.acrqg.platform.ai.service.impl;

import com.acrqg.platform.admin.domain.ModelConfig;
import com.acrqg.platform.admin.domain.SystemParam;
import com.acrqg.platform.admin.repository.ModelConfigMapper;
import com.acrqg.platform.admin.repository.SystemParamMapper;
import com.acrqg.platform.admin.service.AdminService;
import com.acrqg.platform.ai.client.AiIssue;
import com.acrqg.platform.ai.client.AiReviewClient;
import com.acrqg.platform.ai.client.AiReviewRequest;
import com.acrqg.platform.ai.client.AiReviewResponse;
import com.acrqg.platform.ai.exception.AiServiceUnavailableException;
import com.acrqg.platform.ai.exception.SchemaValidationException;
import com.acrqg.platform.ai.filter.AiReviewPayload;
import com.acrqg.platform.ai.filter.FilteredPayload;
import com.acrqg.platform.ai.filter.SensitiveFilter;
import com.acrqg.platform.ai.filter.SensitiveFilterFailureException;
import com.acrqg.platform.ai.prompt.PromptBuilder;
import com.acrqg.platform.ai.service.AiReviewOutcome;
import com.acrqg.platform.ai.service.AiReviewService;
import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.code_issue.domain.CodeIssue;
import com.acrqg.platform.code_issue.domain.CodeIssueSource;
import com.acrqg.platform.code_issue.domain.CodeIssueStatus;
import com.acrqg.platform.code_issue.domain.Severity;
import com.acrqg.platform.code_issue.repository.CodeIssueMapper;
import com.acrqg.platform.diff.domain.DiffFile;
import com.acrqg.platform.diff.repository.DiffFileMapper;
import com.acrqg.platform.project.domain.Project;
import com.acrqg.platform.project.repository.ProjectMapper;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.log.TaskLogger;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * {@link AiReviewService} 默认实现（B3-E.4）。
 *
 * <p>详细策略与降级路径见 {@link AiReviewService} 接口注释。本类聚焦"如何编排"：
 * <ol>
 *   <li>{@link #loadContext(Long)} 一次性加载 task / project / diff，便于后续步骤
 *       不再回库；</li>
 *   <li>{@link #pickEnabledModel()} 选取一个 enabled=true 的模型（按 id 升序）；
 *       无可用模型时 {@code aiAvailable=false} 直接返回；</li>
 *   <li>{@link #buildPayload(ReviewTask, Project, List)} 把 diff 列表转换为 AI
 *       payload，过滤 oversized 文件；</li>
 *   <li>调用 {@link SensitiveFilter#filter}；遇 {@link SensitiveFilterFailureException}
 *       直接降级；</li>
 *   <li>调用 {@link AiReviewClient#review}；按异常类型分别处理；</li>
 *   <li>{@link #persistIssues(Long, AiReviewResponse)} 批量落库 {@code code_issue}；</li>
 *   <li>{@link #computeAiRiskScore(List)} 按 design §12.5 公式计算分数；</li>
 *   <li>更新 {@code review_task.ai_risk_score / ai_available} 并发布审计。</li>
 * </ol>
 *
 * <p>本类无可变共享状态，所有依赖通过构造函数注入；可被 worker 线程池并发调用。
 *
 * <p>Covers: R12.1, R12.2, R12.3, R12.4, R12.5, R12.6。
 */
@Service
public class AiReviewServiceImpl implements AiReviewService {

    private static final Logger log = LoggerFactory.getLogger(AiReviewServiceImpl.class);

    /** task_log.stage 字段统一取值。 */
    static final String STAGE = "AI_REVIEWING";

    /** system_param key：AI 调用超时秒数。 */
    static final String PARAM_TIMEOUT_SECONDS = "ai.review.timeout.seconds";

    /** 兜底超时（秒）。 */
    static final int DEFAULT_TIMEOUT_SECONDS = 60;

    /** 默认门禁关注指标（在没有显式 gate 配置时使用，注入到 prompt）。 */
    static final List<String> DEFAULT_GATE_METRICS =
            List.of("critical_issue_count", "ai_risk_score");

    private final ReviewTaskMapper reviewTaskMapper;
    private final ProjectMapper projectMapper;
    private final DiffFileMapper diffFileMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final SystemParamMapper systemParamMapper;
    private final AdminService adminService;
    private final SensitiveFilter sensitiveFilter;
    private final PromptBuilder promptBuilder;
    private final AiReviewClient aiReviewClient;
    private final CodeIssueMapper codeIssueMapper;
    private final TaskLogger taskLogger;
    private final ApplicationEventPublisher eventPublisher;

    public AiReviewServiceImpl(ReviewTaskMapper reviewTaskMapper,
                               ProjectMapper projectMapper,
                               DiffFileMapper diffFileMapper,
                               ModelConfigMapper modelConfigMapper,
                               SystemParamMapper systemParamMapper,
                               AdminService adminService,
                               SensitiveFilter sensitiveFilter,
                               PromptBuilder promptBuilder,
                               AiReviewClient aiReviewClient,
                               CodeIssueMapper codeIssueMapper,
                               TaskLogger taskLogger,
                               ApplicationEventPublisher eventPublisher) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.projectMapper = projectMapper;
        this.diffFileMapper = diffFileMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.systemParamMapper = systemParamMapper;
        this.adminService = adminService;
        this.sensitiveFilter = sensitiveFilter;
        this.promptBuilder = promptBuilder;
        this.aiReviewClient = aiReviewClient;
        this.codeIssueMapper = codeIssueMapper;
        this.taskLogger = taskLogger;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public AiReviewOutcome execute(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId is required");
        }

        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            taskLogger.error(taskId, STAGE, "task not found, skip ai review");
            return AiReviewOutcome.unavailable();
        }
        Project project = projectMapper.selectById(task.getProjectId());
        if (project == null) {
            taskLogger.error(taskId, STAGE, "project not found, skip ai review");
            return finishUnavailable(taskId, "project_missing", null);
        }

        // 选择 enabled 模型
        ModelConfig model = pickEnabledModel();
        if (model == null) {
            taskLogger.warn(taskId, STAGE,
                    "no enabled model_config found, ai review skipped (degraded)");
            return finishUnavailable(taskId, "no_enabled_model", null);
        }

        // 读取 diff（过滤 oversized）
        List<DiffFile> diffs = diffFileMapper.changedFilesOf(taskId);
        AiReviewPayload payload = buildPayload(task, project, diffs);

        // 第一道：敏感过滤
        FilteredPayload filtered;
        try {
            filtered = sensitiveFilter.filter(payload);
        } catch (SensitiveFilterFailureException ex) {
            taskLogger.error(taskId, STAGE,
                    "sensitive filter failed: " + safeMessage(ex), ex);
            return finishUnavailable(taskId, "sensitive_filter_failed", safeMessage(ex));
        }

        // 第二道：构造 prompt + 调用 AI
        int timeoutSeconds = readTimeoutSecondsOrDefault();
        AiReviewRequest request = promptBuilder.build(
                filtered, model.getBaseUrl(), model.getName(), timeoutSeconds);

        String apiKey;
        try {
            apiKey = adminService.decryptModelApiKey(model.getId());
        } catch (RuntimeException ex) {
            taskLogger.warn(taskId, STAGE,
                    "decrypt model apiKey failed: " + safeMessage(ex), ex);
            return finishUnavailable(taskId, "decrypt_api_key_failed", safeMessage(ex));
        }

        AiReviewResponse response;
        try {
            response = aiReviewClient.review(request, apiKey);
        } catch (AiServiceUnavailableException ex) {
            taskLogger.warn(taskId, STAGE,
                    "ai service unavailable (degraded): " + safeMessage(ex), ex);
            return finishUnavailable(taskId, "ai_service_unavailable", safeMessage(ex));
        } catch (SchemaValidationException ex) {
            taskLogger.warn(taskId, STAGE,
                    "ai response schema invalid, discard: " + safeMessage(ex),
                    Map.of("violations", ex.getViolations()));
            // 注意：此分支不把 aiAvailable 置 false（design §12.4：不影响 SAST 结果）
            // 但因为没有 issues 落库，aiRiskScore = 0；为了报告侧能区分"AI 出过响应但
            // 不可用"，仍然把 aiAvailable 设为 true 并把 outcome 设为 (true, 0, 0)。
            return finishWithDiscardedResponse(taskId);
        } catch (RuntimeException ex) {
            // 任何未预期的异常都按"不可用"路径降级，避免阻塞任务
            taskLogger.warn(taskId, STAGE,
                    "ai client unexpected error (degraded): " + safeMessage(ex), ex);
            return finishUnavailable(taskId, "ai_client_unexpected", safeMessage(ex));
        }

        // 第三道：持久化 issues + 计算风险分
        int persisted = persistIssues(taskId, response);
        int aiRiskScore = computeAiRiskScore(response.issues());

        try {
            reviewTaskMapper.updateAiResult(taskId, aiRiskScore, Boolean.TRUE);
        } catch (RuntimeException ex) {
            log.warn("updateAiResult failed: taskId={} err={}", taskId, ex.toString(), ex);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("aiAvailable", true);
        detail.put("issuesPersisted", persisted);
        detail.put("aiRiskScore", aiRiskScore);
        detail.put("modelId", model.getId());
        detail.put("modelName", model.getName());
        detail.put("wasFiltered", filtered.wasFiltered());
        taskLogger.info(taskId, STAGE,
                "ai review completed: issues=" + persisted + ", score=" + aiRiskScore, detail);

        publishAuditCompleted(task, project, true, persisted, aiRiskScore, detail);

        return new AiReviewOutcome(true, persisted, aiRiskScore);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /** 选第一个 enabled=true 的模型；不存在返回 null。 */
    ModelConfig pickEnabledModel() {
        QueryWrapper<ModelConfig> qw = new QueryWrapper<>();
        qw.eq("enabled", Boolean.TRUE).orderByAsc("id").last("LIMIT 1");
        return modelConfigMapper.selectOne(qw);
    }

    int readTimeoutSecondsOrDefault() {
        try {
            SystemParam sp = systemParamMapper.selectByKey(PARAM_TIMEOUT_SECONDS);
            if (sp == null || sp.getParamValue() == null) {
                return DEFAULT_TIMEOUT_SECONDS;
            }
            int v = Integer.parseInt(sp.getParamValue().trim());
            if (v < 1) {
                return DEFAULT_TIMEOUT_SECONDS;
            }
            return v;
        } catch (RuntimeException ex) {
            log.warn("readTimeoutSecondsOrDefault fallback: {}", ex.toString());
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }

    AiReviewPayload buildPayload(ReviewTask task, Project project, List<DiffFile> diffs) {
        List<AiReviewPayload.FileEntry> files = new ArrayList<>();
        if (diffs != null) {
            for (DiffFile df : diffs) {
                if (Boolean.TRUE.equals(df.getOversized())) {
                    // R10.5：oversized 跳过 AI
                    continue;
                }
                String patch = df.getPatch();
                files.add(new AiReviewPayload.FileEntry(
                        df.getFilePath(), patch == null ? "" : patch, false));
            }
        }
        return new AiReviewPayload(
                task.getId(),
                project.getLanguage(),
                DEFAULT_GATE_METRICS,
                files);
    }

    /** 把 AI 响应批量落 code_issue。 */
    int persistIssues(Long taskId, AiReviewResponse response) {
        if (response == null || response.issues() == null || response.issues().isEmpty()) {
            return 0;
        }
        List<CodeIssue> rows = new ArrayList<>(response.issues().size());
        for (AiIssue ai : response.issues()) {
            CodeIssue ci = new CodeIssue();
            ci.setTaskId(taskId);
            ci.setFilePath(truncate(ai.filePath(), 512));
            ci.setLineNo(ai.lineNo());
            ci.setRuleCode(ai.ruleCode() == null ? null : truncate(ai.ruleCode(), 128));
            ci.setSource(CodeIssueSource.AI.name());
            ci.setSeverity(Severity.from(ai.severity()).name());
            ci.setStatus(CodeIssueStatus.NEW.name());
            ci.setDescription(ai.description());
            ci.setSuggestion(ai.suggestion());
            if (ai.confidence() != null) {
                ci.setConfidence(BigDecimal.valueOf(ai.confidence())
                        .setScale(4, RoundingMode.HALF_UP));
            }
            rows.add(ci);
        }
        try {
            return codeIssueMapper.insertBatch(rows);
        } catch (RuntimeException ex) {
            // 持久化失败不应阻塞任务流转：写 task_log(WARN) 并返回 0
            taskLogger.warn(taskId, STAGE,
                    "persist ai issues failed (degraded count=0): " + safeMessage(ex), ex);
            return 0;
        }
    }

    /**
     * design.md §12.5：
     * <pre>
     * score_per_issue = severityWeight * confidence
     * ai_risk_score   = clamp( max * 0.6 + avg * 0.4 , 0, 100 )
     * </pre>
     * issues 为空返回 0；confidence 为 null 时按 1.0 处理（保守评分）。
     */
    int computeAiRiskScore(List<AiIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return 0;
        }
        double max = 0d;
        double sum = 0d;
        int n = 0;
        for (AiIssue ai : issues) {
            int w = Severity.from(ai.severity()).weight();
            double c = ai.confidence() == null ? 1.0d : ai.confidence();
            if (c < 0) {
                c = 0;
            } else if (c > 1) {
                c = 1;
            }
            double score = w * c;
            if (score > max) {
                max = score;
            }
            sum += score;
            n++;
        }
        double avg = n == 0 ? 0d : sum / n;
        double finalScore = max * 0.6d + avg * 0.4d;
        if (finalScore < 0d) {
            finalScore = 0d;
        }
        if (finalScore > 100d) {
            finalScore = 100d;
        }
        return (int) Math.round(finalScore);
    }

    /** 不可用 / 降级路径的统一兜底：写 review_task + 发布审计 + 返回 outcome。 */
    private AiReviewOutcome finishUnavailable(Long taskId, String reason, String detailMsg) {
        try {
            reviewTaskMapper.updateAiResult(taskId, 0, Boolean.FALSE);
        } catch (RuntimeException ex) {
            log.warn("updateAiResult(FALSE) failed: taskId={} err={}", taskId, ex.toString(), ex);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("aiAvailable", false);
        detail.put("issuesPersisted", 0);
        detail.put("aiRiskScore", 0);
        detail.put("reason", reason);
        if (detailMsg != null) {
            detail.put("message", detailMsg);
        }
        publishAuditCompleted(taskId, false, 0, 0, detail);
        return AiReviewOutcome.unavailable();
    }

    /**
     * Schema 校验失败的特殊路径：aiAvailable=true 但 issues=0 / score=0
     * （design §12.4：不影响 SAST 结果）。
     */
    private AiReviewOutcome finishWithDiscardedResponse(Long taskId) {
        try {
            // ai_available 仍为 true（不修改），仅写 ai_risk_score=0
            reviewTaskMapper.updateAiResult(taskId, 0, Boolean.TRUE);
        } catch (RuntimeException ex) {
            log.warn("updateAiResult(discard) failed: taskId={} err={}",
                    taskId, ex.toString(), ex);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("aiAvailable", true);
        detail.put("issuesPersisted", 0);
        detail.put("aiRiskScore", 0);
        detail.put("reason", "schema_validation_failed");
        publishAuditCompleted(taskId, true, 0, 0, detail);
        return new AiReviewOutcome(true, 0, 0);
    }

    private void publishAuditCompleted(ReviewTask task, Project project,
                                        boolean aiAvailable, int persisted, int score,
                                        Map<String, Object> detail) {
        try {
            AuditEvent event = AuditEvent.system(
                    "AI_REVIEW_COMPLETED",
                    "REVIEW_TASK",
                    String.valueOf(task.getId()),
                    detail);
            eventPublisher.publishEvent(event);
        } catch (RuntimeException ex) {
            log.warn("publishAuditCompleted failed: taskId={} err={}",
                    task == null ? null : task.getId(), ex.toString(), ex);
        }
    }

    /** taskId-only 重载，用于 task / project 已不可用的兜底路径。 */
    private void publishAuditCompleted(Long taskId, boolean aiAvailable, int persisted,
                                        int score, Map<String, Object> detail) {
        try {
            AuditEvent event = AuditEvent.system(
                    "AI_REVIEW_COMPLETED",
                    "REVIEW_TASK",
                    String.valueOf(taskId),
                    detail);
            eventPublisher.publishEvent(event);
        } catch (RuntimeException ex) {
            log.warn("publishAuditCompleted failed: taskId={} err={}",
                    taskId, ex.toString(), ex);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String safeMessage(Throwable t) {
        if (t == null) {
            return "";
        }
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m;
    }

    /** 占位：保留对 ReviewTask context 加载的扩展点（当前未使用）。 */
    @SuppressWarnings("unused")
    private static Object loadContext(Long unused) {
        return Collections.emptyMap();
    }
}
