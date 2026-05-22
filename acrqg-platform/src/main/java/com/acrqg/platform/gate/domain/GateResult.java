package com.acrqg.platform.gate.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 门禁判定结果 DO，对应 {@code gate_result} 表（V33__m07_gate_result.sql）。
 *
 * <p>表结构（design.md §7.2）：
 * <pre>
 * CREATE TABLE gate_result (
 *   id            BIGSERIAL    PRIMARY KEY,
 *   task_id       BIGINT       NOT NULL REFERENCES review_task(id) ON DELETE CASCADE,
 *   status        VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING','PASSED','FAILED','WAIVED')),
 *   score         INT          CHECK (score IS NULL OR score BETWEEN 0 AND 100),
 *   ai_risk_score INT          CHECK (ai_risk_score IS NULL OR ai_risk_score BETWEEN 0 AND 100),
 *   ai_available  BOOLEAN      NOT NULL DEFAULT TRUE,
 *   summary       JSONB        NOT NULL,
 *   created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
 *   updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
 *   CONSTRAINT uk_gate_result_task UNIQUE (task_id)
 * );
 * </pre>
 *
 * <p>{@code summary} 在持久层以 JSON 字符串形式承载；Service 层负责
 * {@code GateResultSummary <-> String} 的双向转换（{@code JsonUtils}）。
 *
 * <p>Covers: R14.3, R14.4, R14.5, R14.8。
 */
@TableName(value = "gate_result", autoResultMap = true)
public class GateResult {

    /** 主键，{@code BIGSERIAL}，由数据库自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联评审任务主键。 */
    @TableField("task_id")
    private Long taskId;

    /** 状态（{@link GateResultStatus#name()}）。 */
    @TableField("status")
    private String status;

    /** 质量评分 [0,100]，可为 null。 */
    @TableField("score")
    private Integer score;

    /** AI 风险分快照 [0,100]，可为 null。 */
    @TableField("ai_risk_score")
    private Integer aiRiskScore;

    /** AI 服务在本次评估时是否可用。 */
    @TableField("ai_available")
    private Boolean aiAvailable;

    /** {@code JSONB} 列的 JSON 字符串原文（由 {@link com.acrqg.platform.gate.dto.GateResultSummary} 序列化）。 */
    @TableField("summary")
    private String summary;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    public GateResult() {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getAiRiskScore() {
        return aiRiskScore;
    }

    public void setAiRiskScore(Integer aiRiskScore) {
        this.aiRiskScore = aiRiskScore;
    }

    public Boolean getAiAvailable() {
        return aiAvailable;
    }

    public void setAiAvailable(Boolean aiAvailable) {
        this.aiAvailable = aiAvailable;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
