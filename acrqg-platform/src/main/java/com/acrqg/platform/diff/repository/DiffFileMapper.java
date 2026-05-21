package com.acrqg.platform.diff.repository;

import com.acrqg.platform.diff.domain.DiffFile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 评审任务变更文件 Mapper。
 *
 * <p>{@code diff_file.hunks} 列为 PostgreSQL JSONB；本 Mapper 在 INSERT 时通过
 * {@code CAST(#{hunksJson} AS JSONB)} 完成字符串到 JSONB 的转换，SELECT 时通过
 * {@code hunks::text AS hunks_json} 转回字符串。这与 {@code task_log.detail}
 * 一致的"字符串 + cast"方案。
 *
 * <p>提供以下查询：
 * <ul>
 *   <li>{@link #insertDiffFile} —— JSONB 显式 cast 的 INSERT；</li>
 *   <li>{@link #deleteByTask}  —— DiffParser 重试场景下重写 diff_file 的清理；</li>
 *   <li>{@link #sumByTask}     —— 任务级聚合（fileCount / addedLines / deletedLines / totalChangedLines）；</li>
 *   <li>{@link #changedFilesOf} —— 报告页 / DiffViewService 使用的列表查询，按 file_path 升序。</li>
 * </ul>
 *
 * <p>Covers: R10.2, R10.3, R16.1, R16.4。
 */
public interface DiffFileMapper extends BaseMapper<DiffFile> {

    /**
     * 插入一条变更文件，{@code hunks} 通过显式 cast 写入 JSONB 列。
     *
     * @param entity diff_file DO
     * @return 受影响行数（1）
     */
    @Insert("""
            INSERT INTO diff_file (
                task_id, file_path, change_type, old_path,
                added_lines, deleted_lines, total_changed_lines,
                oversized, hunks, patch, created_at
            ) VALUES (
                #{taskId}, #{filePath}, #{changeType}, #{oldPath},
                #{addedLines}, #{deletedLines}, #{totalChangedLines},
                #{oversized}, CAST(#{hunksJson} AS JSONB), #{patch},
                COALESCE(#{createdAt}, NOW())
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertDiffFile(DiffFile entity);

    /**
     * 删除指定任务的所有 diff_file 行（用于重试场景：DiffParser 在重新写入前
     * 先清理旧记录，避免 uk_diff_file_task_path 唯一约束冲突）。
     *
     * @param taskId 任务主键
     * @return 受影响行数
     */
    @Delete("DELETE FROM diff_file WHERE task_id = #{taskId}")
    int deleteByTask(@Param("taskId") Long taskId);

    /**
     * 聚合任务级 diff 统计。
     *
     * <p>查询返回单行；当指定任务无 diff_file 行时，COUNT 为 0、SUM 为 NULL。
     * 调用方按 {@link DiffFileSummary} getter 的 null 兜底成 0。
     *
     * @param taskId 任务主键
     * @return 聚合结果，永不为 {@code null}（COUNT 至少返回一行）
     */
    @Select("""
            SELECT COUNT(1)                       AS file_count,
                   COALESCE(SUM(added_lines), 0)  AS total_added_lines,
                   COALESCE(SUM(deleted_lines), 0) AS total_deleted_lines,
                   COALESCE(SUM(total_changed_lines), 0) AS total_changed_lines
              FROM diff_file
             WHERE task_id = #{taskId}
            """)
    @Results({
            @Result(column = "file_count", property = "fileCount"),
            @Result(column = "total_added_lines", property = "totalAddedLines"),
            @Result(column = "total_deleted_lines", property = "totalDeletedLines"),
            @Result(column = "total_changed_lines", property = "totalChangedLines")
    })
    DiffFileSummary sumByTask(@Param("taskId") Long taskId);

    /**
     * 按任务列出全部变更文件，按 file_path 升序。
     *
     * @param taskId 任务主键
     * @return 列表（可能为空）
     */
    @Select("""
            SELECT id, task_id, file_path, change_type, old_path,
                   added_lines, deleted_lines, total_changed_lines,
                   oversized, hunks::text AS hunks_json, patch, created_at
              FROM diff_file
             WHERE task_id = #{taskId}
             ORDER BY file_path ASC
            """)
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "task_id", property = "taskId"),
            @Result(column = "file_path", property = "filePath"),
            @Result(column = "change_type", property = "changeType"),
            @Result(column = "old_path", property = "oldPath"),
            @Result(column = "added_lines", property = "addedLines"),
            @Result(column = "deleted_lines", property = "deletedLines"),
            @Result(column = "total_changed_lines", property = "totalChangedLines"),
            @Result(column = "oversized", property = "oversized"),
            @Result(column = "hunks_json", property = "hunksJson"),
            @Result(column = "patch", property = "patch"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<DiffFile> changedFilesOf(@Param("taskId") Long taskId);
}
