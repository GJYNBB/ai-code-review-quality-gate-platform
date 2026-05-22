package com.acrqg.platform.diff.dto;

import java.util.Collections;
import java.util.List;

/**
 * 任务级 diff 视图（design.md §16.4 / R16.4）。
 *
 * <p>{@link com.acrqg.platform.diff.service.DiffViewService#diffView} 的返回值；
 * Report 模块（B4-B）会再叠加每行关联问题数后透传给前端。
 *
 * <p>Covers: R16.4。
 *
 * @param taskId            任务主键
 * @param changedFileCount  变更文件数
 * @param totalAddedLines   全任务新增行数
 * @param totalDeletedLines 全任务删除行数
 * @param files             单文件 diff 视图列表（按 file_path 升序）
 */
public record DiffViewDTO(
        long taskId,
        int changedFileCount,
        long totalAddedLines,
        long totalDeletedLines,
        List<DiffFileViewDTO> files
) {

    public DiffViewDTO {
        files = files == null ? Collections.emptyList() : List.copyOf(files);
    }
}
