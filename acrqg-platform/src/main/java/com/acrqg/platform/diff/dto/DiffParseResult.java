package com.acrqg.platform.diff.dto;

import java.util.Collections;
import java.util.List;

/**
 * Diff 解析结果（design.md §6.4）。
 *
 * <p>{@link com.acrqg.platform.diff.service.DiffParser#parse} 与
 * {@link com.acrqg.platform.diff.service.DiffParser#parseFromPayload} 的统一返回值。
 *
 * <p>属性测试 P7（design §19）针对此结果断言：
 * <ul>
 *   <li>对每个 {@link ChangedFile}：{@code addedLines + deletedLines == totalChangedLines}；</li>
 *   <li>整体：{@code Σfile.addedLines == totalAddedLines}、
 *       {@code Σfile.deletedLines == totalDeletedLines}。</li>
 * </ul>
 *
 * <p>Covers: R10.2, R10.3。
 *
 * @param changedFileCount  变更文件数（== {@code files.size()}）
 * @param totalAddedLines   全任务新增行数
 * @param totalDeletedLines 全任务删除行数
 * @param files             变更文件列表
 */
public record DiffParseResult(
        int changedFileCount,
        long totalAddedLines,
        long totalDeletedLines,
        List<ChangedFile> files
) {

    public DiffParseResult {
        files = files == null ? Collections.emptyList() : List.copyOf(files);
    }

    /** 空结果（无变更）。 */
    public static DiffParseResult empty() {
        return new DiffParseResult(0, 0L, 0L, Collections.emptyList());
    }
}
