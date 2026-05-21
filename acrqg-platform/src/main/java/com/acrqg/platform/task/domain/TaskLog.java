package com.acrqg.platform.task.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 任务执行流水（DO），对应数据库表 {@code task_log}（V30__m03_review_task.sql）。
 *
 * <p>append-only：仅 INSERT 与 SELECT；无 updated_at 列。当 review_task 被删除时
 * 通过 {@code ON DELETE CASCADE} 一并清理。
 *
 * <p>{@code detail} 列为 PostgreSQL JSONB；与 {@code audit_log.detail} 一致，本
 * DO 使用 {@link String} 承载已序列化好的 JSON 字符串（由
 * {@link com.acrqg.platform.task.log.TaskLogger} 通过 Jackson 写入）。
 * Mapper 在 INSERT 时通过 {@code CAST(? AS JSONB)} 完成字符串到 JSONB 的转换，
 * SELECT 时通过 {@code detail::text AS detail_json} 转回字符串。这种"字符串 + cast"
 * 的方案避免了在 DO 层引入 JacksonTypeHandler 的运行时依赖。
 *
 * <p>Covers: R9.7。
 */
@TableName(value = "task_log", autoResultMap = true)
public class TaskLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    /** 阶段名（FETCHING_DIFF / STATIC_SCANNING / AI_REVIEWING / GATE_EVALUATING / SYSTEM）。 */
    @TableField("stage")
    private String stage;

    /** 级别：INFO / WARN / ERROR。 */
    @TableField("level")
    private String level;

    @TableField("message")
    private String message;

    /** JSONB 明细，存储为已序列化的 JSON 字符串；{@code null} 时不写入。 */
    @TableField("detail")
    private String detailJson;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    public TaskLog() {
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

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
