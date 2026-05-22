package com.acrqg.platform.diff.dto;

import com.acrqg.platform.diff.domain.DiffHunk;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 单文件 diff 视图（design.md §16.4）。
 *
 * <p>{@link com.acrqg.platform.diff.service.DiffViewService#diffView} 的元素，
 * 渲染报告页 diff view 时使用。相对 {@link ChangedFile} 增加：
 * <ul>
 *   <li>{@link #issueCountByLine}：基于
 *       {@link com.acrqg.platform.diff.service.DiffViewService#markIssueLines}
 *       的行号 → 问题数 映射；空 Map 表示尚未注入或本文件无问题。</li>
 * </ul>
 *
 * <p>Covers: R16.4。
 *
 * @param filePath          变更后文件路径
 * @param oldPath           RENAMED 场景下变更前路径
 * @param changeType        {@link com.acrqg.platform.diff.domain.ChangeType#name()}
 * @param addedLines        新增行数
 * @param deletedLines      删除行数
 * @param totalChangedLines 总变更行数
 * @param oversized         是否超过 maxLinesPerFile
 * @param hunks             hunk 列表
 * @param issueCountByLine  newLineNo → 问题数；不可修改 Map
 */
public record DiffFileViewDTO(
        String filePath,
        String oldPath,
        String changeType,
        int addedLines,
        int deletedLines,
        int totalChangedLines,
        boolean oversized,
        List<DiffHunk> hunks,
        Map<Integer, Integer> issueCountByLine
) {

    public DiffFileViewDTO {
        hunks = hunks == null ? Collections.emptyList() : List.copyOf(hunks);
        issueCountByLine = issueCountByLine == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new java.util.LinkedHashMap<>(issueCountByLine));
    }

    /** 复制并替换 {@link #issueCountByLine}。 */
    public DiffFileViewDTO withIssueCountByLine(Map<Integer, Integer> newMap) {
        return new DiffFileViewDTO(filePath, oldPath, changeType,
                addedLines, deletedLines, totalChangedLines, oversized, hunks, newMap);
    }
}
