package com.acrqg.platform.diff.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 评审任务变更文件领域对象（DO），对应数据库表 {@code diff_file}
 * （V31__m04_diff_file.sql / design.md §7.2）。
 *
 * <p>{@code hunks} 列为 PostgreSQL JSONB；本 DO 使用 {@link String} 承载已序列化好
 * 的 JSON 字符串（与 {@code task_log.detail} 一致的字符串 + cast 方案，由
 * {@link com.acrqg.platform.diff.repository.DiffFileMapper} 在 INSERT 时通过
 * {@code CAST(? AS JSONB)} 完成转换）。这样避免了在 DO 层引入 JacksonTypeHandler。
 *
 * <p>Covers: R10.2, R10.3。
 */
@TableName(value = "diff_file", autoResultMap = true)
public class DiffFile {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    /** 变更后文件路径（RENAMED 时为新路径）。 */
    @TableField("file_path")
    private String filePath;

    /** {@link ChangeType#name()}。 */
    @TableField("change_type")
    private String changeType;

    /** RENAMED 场景下的变更前路径，其余场景为 {@code null}。 */
    @TableField("old_path")
    private String oldPath;

    @TableField("added_lines")
    private Integer addedLines;

    @TableField("deleted_lines")
    private Integer deletedLines;

    @TableField("total_changed_lines")
    private Integer totalChangedLines;

    /** TRUE 表示超过 {@code diff.maxLinesPerFile}，AI 阶段跳过该文件（R10.5）。 */
    @TableField("oversized")
    private Boolean oversized;

    /** JSONB 形式的 {@code List<DiffHunk>}，已序列化为字符串。 */
    @TableField("hunks")
    private String hunksJson;

    /** 原始 unified diff 文本（archival 用途）。 */
    @TableField("patch")
    private String patch;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    public DiffFile() {
        // for MyBatis-Plus
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getOldPath() {
        return oldPath;
    }

    public void setOldPath(String oldPath) {
        this.oldPath = oldPath;
    }

    public Integer getAddedLines() {
        return addedLines;
    }

    public void setAddedLines(Integer addedLines) {
        this.addedLines = addedLines;
    }

    public Integer getDeletedLines() {
        return deletedLines;
    }

    public void setDeletedLines(Integer deletedLines) {
        this.deletedLines = deletedLines;
    }

    public Integer getTotalChangedLines() {
        return totalChangedLines;
    }

    public void setTotalChangedLines(Integer totalChangedLines) {
        this.totalChangedLines = totalChangedLines;
    }

    public Boolean getOversized() {
        return oversized;
    }

    public void setOversized(Boolean oversized) {
        this.oversized = oversized;
    }

    public String getHunksJson() {
        return hunksJson;
    }

    public void setHunksJson(String hunksJson) {
        this.hunksJson = hunksJson;
    }

    public String getPatch() {
        return patch;
    }

    public void setPatch(String patch) {
        this.patch = patch;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
