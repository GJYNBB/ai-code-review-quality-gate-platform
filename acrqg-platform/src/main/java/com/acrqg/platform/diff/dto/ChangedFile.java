package com.acrqg.platform.diff.dto;

import com.acrqg.platform.diff.domain.ChangeType;
import com.acrqg.platform.diff.domain.DiffHunk;
import java.util.Collections;
import java.util.List;

/**
 * 单个变更文件视图（design.md §6.4）。
 *
 * <p>由 {@link com.acrqg.platform.diff.service.DiffParser#parse} 返回，作为
 * {@link DiffParseResult#files()} 的元素；同时也是属性测试 P7（design §19）
 * 的核心断言对象——其 {@link #addedLines} + {@link #deletedLines} 必须
 * 等于 {@link #totalChangedLines}。
 *
 * <p>Covers: R10.2, R10.3, R10.5。
 *
 * @param filePath          变更后文件路径
 * @param oldPath           RENAMED 场景下变更前路径；否则 {@code null}
 * @param changeType        变更类型
 * @param addedLines        新增行数
 * @param deletedLines      删除行数
 * @param totalChangedLines added + deleted（冗余存储，但与 DB 一致）
 * @param oversized         是否超过 {@code diff.maxLinesPerFile}
 * @param hunks             解析后的 hunk 列表
 */
public record ChangedFile(
        String filePath,
        String oldPath,
        ChangeType changeType,
        int addedLines,
        int deletedLines,
        int totalChangedLines,
        boolean oversized,
        List<DiffHunk> hunks
) {

    public ChangedFile {
        hunks = hunks == null ? Collections.emptyList() : List.copyOf(hunks);
    }
}
