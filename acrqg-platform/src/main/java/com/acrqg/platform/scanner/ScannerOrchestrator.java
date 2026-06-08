package com.acrqg.platform.scanner;

import com.acrqg.platform.code_issue.domain.CodeIssue;
import com.acrqg.platform.code_issue.repository.CodeIssueMapper;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.diff.domain.DiffFile;
import com.acrqg.platform.diff.repository.DiffFileMapper;
import com.acrqg.platform.project.domain.Project;
import com.acrqg.platform.project.repository.ProjectMapper;
import com.acrqg.platform.repository.domain.RepositoryBinding;
import com.acrqg.platform.repository.repository.RepositoryBindingMapper;
import com.acrqg.platform.repository.service.RepositoryService;
import com.acrqg.platform.scanner.adapter.ScanContext;
import com.acrqg.platform.scanner.adapter.SemgrepScanner;
import com.acrqg.platform.scanner.adapter.StaticScannerAdapter;
import com.acrqg.platform.scanner.source.MaterializedSource;
import com.acrqg.platform.scanner.source.SourceMaterializationRequest;
import com.acrqg.platform.scanner.source.SourceMaterializationService;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.log.TaskLogger;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 静态扫描编排器（design.md §6.5 / §11.1 / §11.4）。
 *
 * <p>{@link #scan(Long)} 流程：
 * <ol>
 *   <li>查 {@code review_task} → {@code project.language}；</li>
 *   <li>查 {@code diff_file}（{@link DiffFileMapper#changedFilesOf(Long)}），剔除
 *       {@code oversized=true} 的文件（R11.5）；</li>
 *   <li>从所有 {@link StaticScannerAdapter} bean 中筛出"语言匹配 OR Semgrep"的
 *       且 {@link StaticScannerAdapter#isAvailable()} 返回 true 的子集；</li>
 *   <li>{@code parallelStream()} 并行执行扫描；单扫描器抛出的
 *       {@link RuntimeException} 在此被捕获，写 WARN task_log 并继续（R11.4）；</li>
 *   <li>合并所有 CodeIssue，回填 {@code taskId}，批量插入 {@code code_issue}（B3-D.5）；</li>
 *   <li>返回插入的总条数。</li>
 * </ol>
 *
 * <p>仅在 {@code worker} profile 下注册 bean——api 进程不消费任务，避免
 * 启动期触发 {@code ScannerConfig} 加载的 PostConstruct（这些 bean 也仅 worker 装配）。
 *
 * <p>{@link #scan(Long)} 在事务边界内执行。批量插入 CodeIssue 失败时事务回滚；
 * 但单扫描器执行失败 / 解析失败被吞掉后不影响事务（已收集到的其他扫描器结果仍会写入）。
 *
 * <p>Covers: R11.1, R11.2, R11.3, R11.4, R11.5。
 */
@Service
@Profile("worker")
public class ScannerOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ScannerOrchestrator.class);

    /** task_log.stage 字面量。 */
    public static final String STAGE = "STATIC_SCANNING";

    private final ReviewTaskMapper reviewTaskMapper;
    private final ProjectMapper projectMapper;
    private final DiffFileMapper diffFileMapper;
    private final CodeIssueMapper codeIssueMapper;
    private final RepositoryBindingMapper repositoryBindingMapper;
    private final RepositoryService repositoryService;
    private final SourceMaterializationService sourceMaterializationService;
    private final TaskLogger taskLogger;
    private final List<StaticScannerAdapter> adapters;

    public ScannerOrchestrator(ReviewTaskMapper reviewTaskMapper,
                               ProjectMapper projectMapper,
                               DiffFileMapper diffFileMapper,
                               CodeIssueMapper codeIssueMapper,
                               RepositoryBindingMapper repositoryBindingMapper,
                               RepositoryService repositoryService,
                               SourceMaterializationService sourceMaterializationService,
                               TaskLogger taskLogger,
                               List<StaticScannerAdapter> adapters) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.projectMapper = projectMapper;
        this.diffFileMapper = diffFileMapper;
        this.codeIssueMapper = codeIssueMapper;
        this.repositoryBindingMapper = repositoryBindingMapper;
        this.repositoryService = repositoryService;
        this.sourceMaterializationService = sourceMaterializationService;
        this.taskLogger = taskLogger;
        this.adapters = adapters == null ? List.of() : List.copyOf(adapters);
    }

    /**
     * 执行任务级静态扫描。
     *
     * @param taskId 评审任务主键
     * @return 写入 {@code code_issue} 的总条数
     */
    @Transactional
    public int scan(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "taskId 不能为空");
        }
        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "任务不存在: " + taskId);
        }
        Project project = projectMapper.selectById(task.getProjectId());
        if (project == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "project not found: " + task.getProjectId());
        }
        String language = normalizeLanguage(project.getLanguage());

        List<DiffFile> changedFiles = diffFileMapper.changedFilesOf(taskId);
        List<DiffFile> applicable = changedFiles == null ? List.of()
                : changedFiles.stream()
                        .filter(f -> !Boolean.TRUE.equals(f.getOversized()))
                        .collect(Collectors.toUnmodifiableList());

        List<DiffFile> safeApplicable = applicable.stream()
                .filter(f -> isSafeChangedPath(f.getFilePath()))
                .collect(Collectors.toUnmodifiableList());
        if (safeApplicable.size() != applicable.size()) {
            taskLogger.warn(taskId, STAGE,
                    "some changed files were skipped because their paths are unsafe for scanner checkout");
        }

        List<StaticScannerAdapter> selected = selectAdapters(language);
        if (selected.isEmpty()) {
            taskLogger.info(taskId, STAGE,
                    "no applicable scanner: language=" + language + " adapters=" + adapters.size());
            return 0;
        }
        if (task.getCommitSha() == null || task.getCommitSha().isBlank()) {
            taskLogger.warn(taskId, STAGE,
                    "static scanners skipped: exact commit SHA is required for safe source checkout");
            return 0;
        }

        RepositoryBinding binding = repositoryBindingMapper.selectByProjectId(task.getProjectId());
        if (binding == null || binding.getRepoUrl() == null || binding.getRepoUrl().isBlank()) {
            taskLogger.warn(taskId, STAGE,
                    "static scanners skipped: repository binding is missing");
            return 0;
        }

        String accessToken = repositoryService.decryptAccessToken(task.getProjectId());
        AtomicInteger succeededScanners = new AtomicInteger(0);
        AtomicInteger failedScanners = new AtomicInteger(0);
        List<CodeIssue> all;
        try (MaterializedSource source = sourceMaterializationService.materialize(
                new SourceMaterializationRequest(
                        taskId,
                        binding.getRepoUrl(),
                        task.getCommitSha(),
                        accessToken))) {
            all = selected.parallelStream()
                    .flatMap(adapter -> safeScan(taskId, adapter, task, safeApplicable, source.workdir(),
                            succeededScanners, failedScanners))
                    .collect(Collectors.toList());
        }

        if (succeededScanners.get() == 0 && failedScanners.get() > 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "all selected static scanners failed; check scanner installation and workdir configuration");
        }

        if (all.isEmpty()) {
            taskLogger.info(taskId, STAGE,
                    "scan finished, 0 issues from " + selected.size() + " scanners");
            return 0;
        }

        // 回填 taskId（防御性，AbstractStaticScanner 已设置）
        for (CodeIssue issue : all) {
            issue.setTaskId(taskId);
        }
        int inserted = codeIssueMapper.insertBatch(all);
        taskLogger.info(taskId, STAGE,
                "scan finished, issues=" + inserted
                        + " scanners=" + selected.size()
                        + " files=" + safeApplicable.size());
        return inserted;
    }

    /** 选择"语言匹配 OR Semgrep（通用安全）"且可用的适配器集合。 */
    List<StaticScannerAdapter> selectAdapters(String language) {
        List<StaticScannerAdapter> result = new ArrayList<>();
        for (StaticScannerAdapter adapter : adapters) {
            if (!adapter.isAvailable()) {
                continue;
            }
            boolean isSemgrep = SemgrepScanner.NAME.equalsIgnoreCase(adapter.name());
            boolean langMatches = language != null
                    && adapter.supportedLanguages() != null
                    && adapter.supportedLanguages().stream()
                            .anyMatch(s -> s != null && s.equalsIgnoreCase(language));
            if (langMatches || isSemgrep) {
                result.add(adapter);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private Stream<CodeIssue> safeScan(long taskId,
                                       StaticScannerAdapter adapter,
                                       ReviewTask task,
                                       List<DiffFile> applicable,
                                       Path workdir,
                                       AtomicInteger succeededScanners,
                                       AtomicInteger failedScanners) {
        try {
            ScanContext ctx = new ScanContext(
                    taskId,
                    task,
                    applicable,
                    workdir,
                    getCachedConfig(adapter));
            List<CodeIssue> issues = adapter.scan(ctx);
            succeededScanners.incrementAndGet();
            if (issues == null || issues.isEmpty()) {
                return Stream.empty();
            }
            return issues.stream();
        } catch (RuntimeException ex) {
            failedScanners.incrementAndGet();
            String message = "scanner failed: " + adapter.name() + ", " + safeMsg(ex);
            log.warn("ScannerOrchestrator: {}", message, ex);
            taskLogger.warn(taskId, STAGE, message, ex);
            return Stream.empty();
        }
    }

    private static com.acrqg.platform.admin.domain.ScannerConfig getCachedConfig(StaticScannerAdapter adapter) {
        if (adapter instanceof com.acrqg.platform.scanner.adapter.AbstractStaticScanner abstr) {
            return abstr.getCachedConfig();
        }
        return null;
    }

    private static boolean isSafeChangedPath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        String normalized = filePath.replace('\\', '/');
        if (normalized.startsWith("/")
                || normalized.equals("..")
                || normalized.contains("../")
                || normalized.startsWith("-")) {
            return false;
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (Character.isISOControl(normalized.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeLanguage(String lang) {
        if (lang == null || lang.isBlank()) {
            return null;
        }
        return lang.trim().toLowerCase(Locale.ROOT);
    }

    private static String safeMsg(Throwable t) {
        if (t == null) {
            return "null";
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }
}
