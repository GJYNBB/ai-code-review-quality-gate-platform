package com.acrqg.platform.diff.service;

import com.acrqg.platform.common.util.JsonUtils;
import com.acrqg.platform.diff.domain.DiffFile;
import com.acrqg.platform.diff.domain.DiffHunk;
import com.acrqg.platform.diff.dto.DiffFileViewDTO;
import com.acrqg.platform.diff.dto.DiffViewDTO;
import com.acrqg.platform.diff.dto.FileLineIssueMark;
import com.acrqg.platform.diff.repository.DiffFileMapper;
import com.acrqg.platform.diff.repository.DiffFileSummary;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Diff 视图服务（design.md §6.8 / R16.4）。
 *
 * <p>读路径：从 {@code diff_file} 表加载某个任务的全部变更文件，反序列化
 * {@code hunks} JSONB 后构造 {@link DiffViewDTO} 供 Report 模块（B4-B）使用。
 *
 * <p>{@link #markIssueLines} 提供"行级问题数"叠加钩子：B4-B 在聚合
 * {@code code_issue} 后构造 {@link FileLineIssueMark} 列表传入，本服务按
 * filePath 与 newLineNo 把 issueCount 注入到对应 {@link DiffFileViewDTO} 的
 * {@code issueCountByLine} Map 中。本任务（B3-C）保留 issueCountByLine 字段
 * 与映射逻辑；mark 注入仅由 B4-B 触发。
 *
 * <p>本服务<b>只读</b>；事务由调用方决定（Report 内部一般直接读，无需事务）。
 *
 * <p>Covers: R16.4。
 */
@Service
public class DiffViewService {

    private static final Logger log = LoggerFactory.getLogger(DiffViewService.class);

    /** 反序列化 hunks JSON 的 {@link TypeReference}（线程安全）。 */
    private static final TypeReference<List<DiffHunk>> HUNKS_TYPE = new TypeReference<>() {
    };

    private final DiffFileMapper diffFileMapper;

    public DiffViewService(DiffFileMapper diffFileMapper) {
        this.diffFileMapper = diffFileMapper;
    }

    /**
     * 加载某任务的 diff view（不含问题标记）。
     *
     * @param taskId 任务主键
     * @return 视图，永不为 {@code null}；任务无变更文件时返回空 view
     */
    public DiffViewDTO diffView(Long taskId) {
        Objects.requireNonNull(taskId, "taskId");
        List<DiffFile> rows = diffFileMapper.changedFilesOf(taskId);
        DiffFileSummary summary = diffFileMapper.sumByTask(taskId);
        long totalAdded = nullSafe(summary == null ? null : summary.getTotalAddedLines());
        long totalDeleted = nullSafe(summary == null ? null : summary.getTotalDeletedLines());

        List<DiffFileViewDTO> files = new ArrayList<>(rows.size());
        for (DiffFile r : rows) {
            files.add(toView(r));
        }
        return new DiffViewDTO(taskId, files.size(), totalAdded, totalDeleted, files);
    }

    /**
     * 把行级问题数标记叠加到现有的 diff view 上，返回新视图（不修改入参）。
     *
     * <p>同一 (filePath, lineNo) 多次出现时按 issueCount 求和。
     *
     * @param view  原始视图
     * @param marks 行级问题标记列表，可能为 {@code null} / 空（直接返回原视图）
     * @return 新视图（与入参共享 hunks，仅 issueCountByLine 不同）
     */
    public DiffViewDTO markIssueLines(DiffViewDTO view, List<FileLineIssueMark> marks) {
        Objects.requireNonNull(view, "view");
        if (marks == null || marks.isEmpty()) {
            return view;
        }
        // 按 filePath 聚合标记
        Map<String, Map<Integer, Integer>> markIndex = new LinkedHashMap<>();
        for (FileLineIssueMark m : marks) {
            if (m == null || m.filePath() == null) {
                continue;
            }
            markIndex.computeIfAbsent(m.filePath(), k -> new LinkedHashMap<>())
                    .merge(m.lineNo(), m.issueCount(), Integer::sum);
        }
        if (markIndex.isEmpty()) {
            return view;
        }

        List<DiffFileViewDTO> updated = new ArrayList<>(view.files().size());
        for (DiffFileViewDTO f : view.files()) {
            Map<Integer, Integer> filePerLine = markIndex.get(f.filePath());
            if (filePerLine == null || filePerLine.isEmpty()) {
                updated.add(f);
                continue;
            }
            Map<Integer, Integer> merged = new LinkedHashMap<>(f.issueCountByLine());
            filePerLine.forEach((line, count) -> merged.merge(line, count, Integer::sum));
            updated.add(f.withIssueCountByLine(merged));
        }
        return new DiffViewDTO(
                view.taskId(),
                view.changedFileCount(),
                view.totalAddedLines(),
                view.totalDeletedLines(),
                updated);
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private static DiffFileViewDTO toView(DiffFile r) {
        List<DiffHunk> hunks = parseHunks(r.getHunksJson());
        return new DiffFileViewDTO(
                r.getFilePath(),
                r.getOldPath(),
                r.getChangeType(),
                nullSafe(r.getAddedLines()),
                nullSafe(r.getDeletedLines()),
                nullSafe(r.getTotalChangedLines()),
                Boolean.TRUE.equals(r.getOversized()),
                hunks,
                Collections.emptyMap());
    }

    private static List<DiffHunk> parseHunks(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return Collections.emptyList();
        }
        try {
            List<DiffHunk> v = JsonUtils.fromJson(json, HUNKS_TYPE);
            return v == null ? Collections.emptyList() : v;
        } catch (RuntimeException ex) {
            log.warn("parseHunks failed (returning empty list): {}", ex.toString());
            return Collections.emptyList();
        }
    }

    private static int nullSafe(Integer v) {
        return v == null ? 0 : v;
    }

    private static long nullSafe(Long v) {
        return v == null ? 0L : v;
    }
}
