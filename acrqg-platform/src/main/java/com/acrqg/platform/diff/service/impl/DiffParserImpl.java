package com.acrqg.platform.diff.service.impl;

import com.acrqg.platform.admin.domain.SystemParam;
import com.acrqg.platform.admin.repository.SystemParamMapper;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.common.util.JsonUtils;
import com.acrqg.platform.diff.domain.ChangeType;
import com.acrqg.platform.diff.domain.DiffFile;
import com.acrqg.platform.diff.domain.DiffHunk;
import com.acrqg.platform.diff.dto.ChangedFile;
import com.acrqg.platform.diff.dto.DiffParseResult;
import com.acrqg.platform.diff.repository.DiffFileMapper;
import com.acrqg.platform.diff.service.DiffParser;
import com.acrqg.platform.repository.client.DiffFetchException;
import com.acrqg.platform.repository.client.ProviderClient;
import com.acrqg.platform.repository.client.ProviderClientFactory;
import com.acrqg.platform.repository.domain.Provider;
import com.acrqg.platform.repository.domain.RepositoryBinding;
import com.acrqg.platform.repository.dto.DiffFetchRequest;
import com.acrqg.platform.repository.dto.DiffFilePayload;
import com.acrqg.platform.repository.dto.DiffPayload;
import com.acrqg.platform.repository.repository.RepositoryBindingMapper;
import com.acrqg.platform.repository.service.RepositoryService;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.log.TaskLogger;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DiffParser} 默认实现（B3-C.4）。
 *
 * <h3>主流程（{@link #parse(Long)}）</h3>
 * <ol>
 *   <li>查 {@code review_task}，缺失抛 {@link ErrorCode#TASK_NOT_FOUND}；</li>
 *   <li>查 {@code repository_binding}，缺失抛 {@link DiffFetchException}（视为不可恢复）；</li>
 *   <li>取明文 access token；</li>
 *   <li>调 {@link ProviderClient#fetchDiff} 拉取；</li>
 *   <li>对每个 {@link DiffFilePayload}：
 *       <ul>
 *         <li>调 {@link UnifiedDiffParser#parse} 把 patch 文本解析成 hunks + 增删行数；</li>
 *         <li>{@code totalChangedLines = added + deleted}；</li>
 *         <li>{@code oversized = totalChangedLines > diff.maxLinesPerFile}（默认 5000）；</li>
 *         <li>插入 {@code diff_file}（重试场景下先 DELETE）。</li>
 *       </ul>
 *   </li>
 *   <li>聚合任务级 {@code totalAddedLines} / {@code totalDeletedLines} / {@code changedFileCount}；</li>
 *   <li>返回 {@link DiffParseResult}。</li>
 * </ol>
 *
 * <p>失败处理：
 * <ul>
 *   <li>{@link DiffFetchException} 已经包含 provider/repoUrl/prId 上下文，直接写
 *       ERROR task_log 后向上抛；</li>
 *   <li>未预期 {@link RuntimeException} 同样写 ERROR task_log 后包装为
 *       {@link DiffFetchException} 抛出（保证 {@code TaskOrchestrator} 接到的是
 *       领域异常，统一转 EXECUTION_FAILED）。</li>
 * </ul>
 *
 * <h3>{@link #parseFromPayload}</h3>
 *
 * <p>纯函数：仅基于入参计算 {@link DiffParseResult}，不写库、不读 system_param。
 * 用途：属性测试 P7（design §19）与单元测试。{@code oversized} 在该方法中仍按
 * 默认阈值 5000 计算（与系统默认一致）；调用方需精确控制阈值时，应直接调
 * {@link UnifiedDiffParser#parse} 并自行组装。
 *
 * <p>Covers: R10.1, R10.2, R10.3, R10.4, R10.5, R23.3。
 */
@Service
public class DiffParserImpl implements DiffParser {

    private static final Logger log = LoggerFactory.getLogger(DiffParserImpl.class);

    /** {@code task_log.stage} 字面量。 */
    private static final String STAGE = "FETCHING_DIFF";

    /** system_param 中存储 maxLinesPerFile 的 key（B1-D 种子已写）。 */
    static final String PARAM_MAX_LINES = "diff.maxLinesPerFile";

    /** maxLinesPerFile 的默认值（与种子一致）。 */
    static final int DEFAULT_MAX_LINES = 5_000;

    private final ReviewTaskMapper reviewTaskMapper;
    private final RepositoryBindingMapper repositoryBindingMapper;
    private final RepositoryService repositoryService;
    private final ProviderClientFactory providerClientFactory;
    private final DiffFileMapper diffFileMapper;
    private final SystemParamMapper systemParamMapper;
    private final TaskLogger taskLogger;

    public DiffParserImpl(ReviewTaskMapper reviewTaskMapper,
                          RepositoryBindingMapper repositoryBindingMapper,
                          RepositoryService repositoryService,
                          ProviderClientFactory providerClientFactory,
                          DiffFileMapper diffFileMapper,
                          SystemParamMapper systemParamMapper,
                          TaskLogger taskLogger) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.repositoryBindingMapper = repositoryBindingMapper;
        this.repositoryService = repositoryService;
        this.providerClientFactory = providerClientFactory;
        this.diffFileMapper = diffFileMapper;
        this.systemParamMapper = systemParamMapper;
        this.taskLogger = taskLogger;
    }

    // =====================================================================
    // public API
    // =====================================================================

    @Override
    @Transactional
    public DiffParseResult parse(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "taskId 不能为空");
        }
        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "任务不存在: " + taskId);
        }
        Long projectId = task.getProjectId();

        RepositoryBinding binding = repositoryBindingMapper.selectByProjectId(projectId);
        if (binding == null) {
            String message = "repository binding not found for project " + projectId;
            taskLogger.error(taskId, STAGE, "diff fetch failed: " + message);
            throw new DiffFetchException(null, null, task.getPrId(), message);
        }
        Provider provider;
        try {
            provider = Provider.valueOf(binding.getProvider());
        } catch (IllegalArgumentException ex) {
            String message = "invalid provider: " + binding.getProvider();
            taskLogger.error(taskId, STAGE, "diff fetch failed: " + message, ex);
            throw new DiffFetchException(null, binding.getRepoUrl(), task.getPrId(), message, ex);
        }

        String accessToken;
        try {
            accessToken = repositoryService.decryptAccessToken(projectId);
        } catch (RuntimeException ex) {
            String message = "decrypt accessToken failed";
            taskLogger.error(taskId, STAGE, "diff fetch failed: " + message, ex);
            throw new DiffFetchException(provider, binding.getRepoUrl(), task.getPrId(), message, ex);
        }

        DiffFetchRequest request = new DiffFetchRequest(
                provider,
                binding.getRepoUrl(),
                task.getPrId(),
                null,
                task.getCommitSha(),
                accessToken);

        ProviderClient client = providerClientFactory.byProvider(provider);
        DiffPayload payload;
        try {
            payload = client.fetchDiff(request);
        } catch (DiffFetchException ex) {
            taskLogger.error(taskId, STAGE, "diff fetch failed: " + safeMsg(ex), ex);
            throw ex;
        } catch (RuntimeException ex) {
            taskLogger.error(taskId, STAGE, "diff fetch failed: " + safeMsg(ex), ex);
            throw new DiffFetchException(provider, binding.getRepoUrl(), task.getPrId(),
                    "unexpected error: " + safeMsg(ex), ex);
        }
        if (payload == null) {
            payload = DiffPayload.empty();
        }

        // 重试场景下清理旧的 diff_file
        try {
            int deleted = diffFileMapper.deleteByTask(taskId);
            if (deleted > 0) {
                log.debug("DiffParser: deleted {} stale diff_file rows for task {}", deleted, taskId);
            }
        } catch (RuntimeException ex) {
            taskLogger.warn(taskId, STAGE, "deleteByTask failed: " + safeMsg(ex), ex);
            // 不重抛：唯一约束兜底
        }

        int maxLinesPerFile = readMaxLinesPerFile();
        DiffParseResult result = parseAndPersist(taskId, payload, maxLinesPerFile);

        taskLogger.info(taskId, STAGE,
                "diff parsed: files=" + result.changedFileCount()
                        + " added=" + result.totalAddedLines()
                        + " deleted=" + result.totalDeletedLines());
        return result;
    }

    @Override
    public DiffParseResult parseFromPayload(DiffPayload payload) {
        if (payload == null || payload.files() == null || payload.files().isEmpty()) {
            return DiffParseResult.empty();
        }
        return parseInMemory(payload, DEFAULT_MAX_LINES);
    }

    // =====================================================================
    // internal
    // =====================================================================

    /** 同 {@link #parseInMemory}，但同时把每个 ChangedFile 写入 diff_file 表。 */
    private DiffParseResult parseAndPersist(long taskId, DiffPayload payload, int maxLinesPerFile) {
        List<ChangedFile> files = new ArrayList<>(payload.files().size());
        long totalAdded = 0;
        long totalDeleted = 0;
        for (DiffFilePayload fp : payload.files()) {
            ChangedFile cf = parseSingleFile(fp, maxLinesPerFile);
            files.add(cf);
            totalAdded += cf.addedLines();
            totalDeleted += cf.deletedLines();

            DiffFile entity = toEntity(taskId, fp, cf);
            try {
                diffFileMapper.insertDiffFile(entity);
            } catch (RuntimeException ex) {
                // 单文件写库失败：写 ERROR task_log，但不中断整个解析流程
                // （uk_diff_file_task_path 兜底；DiffFetchException 会将整任务转 EXECUTION_FAILED 由 retry 重写）
                taskLogger.error(taskId, STAGE,
                        "insert diff_file failed: filePath=" + fp.filePath()
                                + " err=" + safeMsg(ex), ex);
                throw new DiffFetchException(null, null, null,
                        "insert diff_file failed for " + fp.filePath() + ": " + safeMsg(ex), ex);
            }
        }
        return new DiffParseResult(files.size(), totalAdded, totalDeleted, files);
    }

    /** 仅在内存中解析（用于 parseFromPayload）。 */
    private static DiffParseResult parseInMemory(DiffPayload payload, int maxLinesPerFile) {
        List<ChangedFile> files = new ArrayList<>(payload.files().size());
        long totalAdded = 0;
        long totalDeleted = 0;
        for (DiffFilePayload fp : payload.files()) {
            ChangedFile cf = parseSingleFile(fp, maxLinesPerFile);
            files.add(cf);
            totalAdded += cf.addedLines();
            totalDeleted += cf.deletedLines();
        }
        return new DiffParseResult(files.size(), totalAdded, totalDeleted, files);
    }

    /** 解析单个文件 patch 为 {@link ChangedFile}。 */
    private static ChangedFile parseSingleFile(DiffFilePayload fp, int maxLinesPerFile) {
        UnifiedDiffParser.Result parsed = UnifiedDiffParser.parse(fp.patch());
        int added = parsed.addedLines();
        int deleted = parsed.deletedLines();
        int total = added + deleted;
        boolean oversized = total > maxLinesPerFile;
        ChangeType type = parseChangeType(fp.changeType());
        // RENAMED 情况下 oldPath 取载荷字段（GitHub previous_filename / GitLab old_path）
        String oldPath = type == ChangeType.RENAMED ? fp.oldPath() : null;
        return new ChangedFile(
                fp.filePath(),
                oldPath,
                type,
                added,
                deleted,
                total,
                oversized,
                parsed.hunks());
    }

    private static ChangeType parseChangeType(String raw) {
        if (raw == null) {
            return ChangeType.MODIFIED;
        }
        try {
            return ChangeType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return ChangeType.fromGithubLike(raw);
        }
    }

    private DiffFile toEntity(long taskId, DiffFilePayload fp, ChangedFile cf) {
        DiffFile e = new DiffFile();
        e.setTaskId(taskId);
        e.setFilePath(cf.filePath());
        e.setChangeType(cf.changeType().name());
        e.setOldPath(cf.oldPath());
        e.setAddedLines(cf.addedLines());
        e.setDeletedLines(cf.deletedLines());
        e.setTotalChangedLines(cf.totalChangedLines());
        e.setOversized(cf.oversized());
        // hunks 序列化为 JSON 字符串；MyBatis 在 INSERT 时通过 CAST AS JSONB 写入
        List<DiffHunk> hunks = cf.hunks();
        e.setHunksJson(hunks == null || hunks.isEmpty() ? "[]" : JsonUtils.toJson(hunks));
        e.setPatch(fp.patch());
        return e;
    }

    /** 读取 system_param.diff.maxLinesPerFile；非法或缺失退化为默认值。 */
    private int readMaxLinesPerFile() {
        try {
            SystemParam sp = systemParamMapper.selectByKey(PARAM_MAX_LINES);
            if (sp == null || sp.getParamValue() == null) {
                return DEFAULT_MAX_LINES;
            }
            int v = Integer.parseInt(sp.getParamValue().trim());
            if (v <= 0) {
                return DEFAULT_MAX_LINES;
            }
            return v;
        } catch (RuntimeException ex) {
            log.warn("readMaxLinesPerFile fallback to default: {}", ex.toString());
            return DEFAULT_MAX_LINES;
        }
    }

    private static String safeMsg(Throwable t) {
        if (t == null) {
            return "null";
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }
}
