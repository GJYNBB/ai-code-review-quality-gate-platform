package com.acrqg.platform.diff.domain;

import java.util.Collections;
import java.util.List;

/**
 * 一个 unified diff hunk（design.md §6.4 / §7.2 hunks JSONB 子结构）。
 *
 * <p>对应 unified diff 中的 {@code @@ -oldStart,oldLines +newStart,newLines @@}
 * 头行 + 紧随其后的行级条目（{@link DiffLine}）。
 *
 * <p>由 {@link com.acrqg.platform.diff.service.impl.DiffParserImpl} 解析，
 * 序列化进 {@code diff_file.hunks} JSONB 列。
 *
 * <p>Covers: R10.2, R10.3, R16.4。
 *
 * @param oldStart 头行中的旧文件起始行号
 * @param oldLines 头行中的旧文件行数
 * @param newStart 头行中的新文件起始行号
 * @param newLines 头行中的新文件行数
 * @param lines    行级 diff 条目；构造时拷贝为不可修改 {@link List}
 */
public record DiffHunk(
        int oldStart,
        int oldLines,
        int newStart,
        int newLines,
        List<DiffLine> lines
) {

    public DiffHunk {
        lines = lines == null ? Collections.emptyList() : List.copyOf(lines);
    }
}
