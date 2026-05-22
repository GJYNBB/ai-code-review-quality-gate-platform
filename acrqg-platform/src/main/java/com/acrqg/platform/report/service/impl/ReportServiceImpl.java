package com.acrqg.platform.report.service.impl;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.diff.dto.DiffViewDTO;
import com.acrqg.platform.diff.service.DiffViewService;
import com.acrqg.platform.gate.dto.GateResultDTO;
import com.acrqg.platform.gate.service.GateResultService;
import com.acrqg.platform.infra.permission.PermissionEvaluator;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.acrqg.platform.project.domain.Project;
import com.acrqg.platform.project.repository.ProjectMapper;
import com.acrqg.platform.report.dto.GateResultSummaryDTO;
import com.acrqg.platform.report.dto.IssueCountAggDTO;
import com.acrqg.platform.report.dto.ReviewReportDTO;
import com.acrqg.platform.report.dto.TaskOverviewDTO;
import com.acrqg.platform.report.repository.ReportIssueAggregationMapper;
import com.acrqg.platform.report.service.ReportService;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.dto.TaskLogDTO;
import com.acrqg.platform.task.dto.TaskLogQuery;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.acrqg.platform.task.service.ReviewTaskService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * {@link ReportService} 默认实现（B4-B.2）。
 *
 * <h3>聚合策略</h3>
 *
 * <ol>
 *   <li>{@code review_task} 单行查询 → {@link TaskOverviewDTO}（附加项目名）；</li>
 *   <li>{@link GateResultService#get(Long)} 取门禁结果；
 *       {@link com.acrqg.platform.common.api.ErrorCode#TASK_NOT_FOUND}
 *       (message {@code "gate result not generated"}) 不阻断报告查询，
 *       直接将 {@code gateResultSummary} 置 {@code null}；</li>
 *   <li>{@link ReportIssueAggregationMapper#aggregateBySeverityAndSource(Long)}
 *       一次 SQL 完成 (severity, source) 二维聚合；</li>
 *   <li>{@code aiAvailability} 取值优先级：{@code gate_result.ai_available} →
 *       {@code review_task.ai_available} → {@code true}（默认）。</li>
 * </ol>
 *
 * <h3>缓存策略</h3>
 *
 * <p>使用 Caffeine 内存缓存：
 * <ul>
 *   <li>key = taskId（{@link Long}）；</li>
 *   <li>TTL = 10 秒 expireAfterWrite，足以扛住报告页 100 并发首屏（R16.6
 *       p95 ≤ 2s）但又不会让审批 / 状态切换的最新数据滞留太久；</li>
 *   <li>maximumSize = 500 兜底防止热点任务长时间被反复访问导致缓存膨胀。</li>
 * </ul>
 *
 * <p>{@link #diffView(Long)} 与 {@link #logs(Long, TaskLogQuery)} 没有缓存——
 * diffView 体积较大且按需加载，logs 涉及分页参数命中率低，缓存收益小于失效成本。
 *
 * <p>Covers: R16.1, R16.2, R16.4, R16.5, R16.6, R2.2, R9.7。
 */
@Service
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    /** 报告聚合缓存 TTL：10 秒（design.md §16.6 性能目标）。 */
    static final Duration REPORT_CACHE_TTL = Duration.ofSeconds(10);

    /** 缓存最大条目数（5 倍并发预估，避免热点 LRU 抖动）。 */
    static final long REPORT_CACHE_MAX_SIZE = 500L;

    private final ReviewTaskMapper reviewTaskMapper;
    private final ProjectMapper projectMapper;
    private final ReportIssueAggregationMapper issueAggregationMapper;
    private final GateResultService gateResultService;
    private final DiffViewService diffViewService;
    private final ReviewTaskService reviewTaskService;
    private final PermissionEvaluator permissionEvaluator;

    /** 报告聚合 DTO 的本地缓存。 */
    private final Cache<Long, ReviewReportDTO> reportCache;

    public ReportServiceImpl(ReviewTaskMapper reviewTaskMapper,
                              ProjectMapper projectMapper,
                              ReportIssueAggregationMapper issueAggregationMapper,
                              GateResultService gateResultService,
                              DiffViewService diffViewService,
                              ReviewTaskService reviewTaskService,
                              PermissionEvaluator permissionEvaluator) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.projectMapper = projectMapper;
        this.issueAggregationMapper = issueAggregationMapper;
        this.gateResultService = gateResultService;
        this.diffViewService = diffViewService;
        this.reviewTaskService = reviewTaskService;
        this.permissionEvaluator = permissionEvaluator;
        this.reportCache = Caffeine.newBuilder()
                .expireAfterWrite(REPORT_CACHE_TTL)
                .maximumSize(REPORT_CACHE_MAX_SIZE)
                .build();
    }

    // =====================================================================
    // report
    // =====================================================================

    @Override
    public ReviewReportDTO report(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "taskId 不能为空");
        }

        ReviewReportDTO cached = reportCache.getIfPresent(taskId);
        if (cached != null) {
            // 即使命中缓存，仍需校验当前调用方是否为项目成员
            ensureProjectMemberByTaskId(taskId, cached.taskOverview().projectId());
            return cached;
        }

        ReviewTask task = requireTaskById(taskId);
        ensureProjectMemberByTaskId(taskId, task.getProjectId());

        TaskOverviewDTO overview = buildOverview(task);
        GateResultDTO gateResult = tryFetchGateResult(taskId);
        GateResultSummaryDTO summary = (gateResult == null) ? null : toSummary(gateResult);
        List<IssueCountAggDTO> counts = aggregateIssueCounts(taskId);
        boolean aiAvailability = resolveAiAvailability(task, gateResult);

        ReviewReportDTO dto = new ReviewReportDTO(overview, summary, counts, aiAvailability);
        reportCache.put(taskId, dto);
        return dto;
    }

    // =====================================================================
    // diff
    // =====================================================================

    @Override
    public DiffViewDTO diffView(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "taskId 不能为空");
        }
        ReviewTask task = requireTaskById(taskId);
        ensureProjectMemberByTaskId(taskId, task.getProjectId());
        return diffViewService.diffView(taskId);
    }

    // =====================================================================
    // logs
    // =====================================================================

    @Override
    public PageResult<TaskLogDTO> logs(Long taskId, TaskLogQuery query) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "taskId 不能为空");
        }
        // 直接转发到 ReviewTaskService.pageLogs：该方法已经做了"任务存在 + 项目成员"
        // 校验，无需在此重复一遍。这里的转发避免了报告模块直接持有 TaskLogMapper，
        // 保持模块边界清晰。
        return reviewTaskService.pageLogs(taskId, query);
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private ReviewTask requireTaskById(Long taskId) {
        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND,
                    ErrorCode.TASK_NOT_FOUND.getMessage());
        }
        return task;
    }

    /**
     * 校验当前调用方是否为指定项目的成员；非成员抛 {@code PERMISSION_DENIED}。
     *
     * <p>{@code expectedTaskId} 仅作日志对账用，不影响判定结果。
     */
    private void ensureProjectMemberByTaskId(Long expectedTaskId, Long projectId) {
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();
        if (projectId == null
                || !permissionEvaluator.isProjectMember(caller.id(), projectId)) {
            log.debug("ReportService: deny userId={} taskId={} projectId={}",
                    caller.id(), expectedTaskId, projectId);
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }
    }

    private TaskOverviewDTO buildOverview(ReviewTask task) {
        String projectName = null;
        if (task.getProjectId() != null) {
            Project project = projectMapper.selectById(task.getProjectId());
            if (project != null) {
                projectName = project.getName();
            }
        }
        Long durationSeconds = computeDurationSeconds(task.getStartedAt(), task.getFinishedAt());
        return new TaskOverviewDTO(
                task.getId(),
                task.getTaskNo(),
                task.getProjectId(),
                projectName,
                task.getPrId(),
                task.getCommitSha(),
                task.getSourceBranch(),
                task.getTargetBranch(),
                task.getStatus(),
                task.getScore(),
                durationSeconds,
                task.getCreatedAt(),
                task.getFinishedAt());
    }

    private static Long computeDurationSeconds(OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        long seconds = Duration.between(startedAt, finishedAt).getSeconds();
        return seconds < 0 ? 0L : seconds;
    }

    /**
     * 调用 {@link GateResultService#get(Long)}；{@code TASK_NOT_FOUND} 异常表示
     * 任务尚未走完 GATE_EVALUATING 阶段，此时返回 {@code null}（不阻断报告）。
     *
     * <p>其他异常向上抛出，由全局 ExceptionHandler 处理。
     */
    private GateResultDTO tryFetchGateResult(Long taskId) {
        try {
            return gateResultService.get(taskId);
        } catch (BusinessException ex) {
            if (ex.getCode() == ErrorCode.TASK_NOT_FOUND) {
                log.debug("ReportService: gate_result not generated taskId={} message={}",
                        taskId, ex.getMessage());
                return null;
            }
            throw ex;
        }
    }

    private static GateResultSummaryDTO toSummary(GateResultDTO gate) {
        return new GateResultSummaryDTO(
                gate.status(),
                gate.score(),
                gate.aiRiskScore(),
                gate.aiAvailable(),
                gate.summary() == null ? Collections.emptyList() : gate.summary().failedRules(),
                gate.summary() == null ? Collections.emptyList() : gate.summary().passedRules());
    }

    private List<IssueCountAggDTO> aggregateIssueCounts(Long taskId) {
        List<Map<String, Object>> rows = issueAggregationMapper.aggregateBySeverityAndSource(taskId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<IssueCountAggDTO> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String severity = asString(row.get("severity"));
            String source = asString(row.get("source"));
            long count = asLong(row.get("cnt"));
            result.add(new IssueCountAggDTO(severity, source, count));
        }
        return result;
    }

    /**
     * AI 可用性优先取 {@code gate_result.ai_available}（最权威的快照）；
     * 缺失时退化到 {@code review_task.ai_available}；都没有则按 {@code true} 兜底。
     */
    private static boolean resolveAiAvailability(ReviewTask task, GateResultDTO gateResult) {
        if (gateResult != null) {
            return gateResult.aiAvailable();
        }
        Boolean v = task.getAiAvailable();
        return v == null || v;
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private static long asLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
