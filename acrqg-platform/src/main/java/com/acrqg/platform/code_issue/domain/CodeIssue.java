package com.acrqg.platform.code_issue.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 代码问题领域对象（DO），对应数据库表 {@code code_issue}（V32__m05_code_issue.sql）。
 *
 * <p>表结构来自 design.md §7.2：
 * <pre>
 * CREATE TABLE code_issue (
 *   id          BIGSERIAL PRIMARY KEY,
 *   task_id     BIGINT       NOT NULL REFERENCES review_task(id) ON DELETE CASCADE,
 *   file_path   VARCHAR(1024) NOT NULL,
 *   line_no     INT,
 *   rule_code   VARCHAR(255),
 *   source      VARCHAR(16)  NOT NULL CHECK (source IN ('SAST','AI','MANUAL')),
 *   severity    VARCHAR(16)  NOT NULL CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW','INFO')),
 *   status      VARCHAR(16)  NOT NULL DEFAULT 'NEW' CHECK (...),
 *   description TEXT         NOT NULL,
 *   suggestion  TEXT,
 *   confidence  NUMERIC(3,2),
 *   created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 *
 * <p>共享给 B3-D（SAST 写入）/ B3-E（AI 写入）/ B3-F（聚合查询）/ B4-A（状态流转）使用。
 *
 * <p>Covers: R11.2, R12.3, R16.2, R17。
 */
@TableName(value = "code_issue", autoResultMap = true)
public class CodeIssue {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("file_path")
    private String filePath;

    @TableField("line_no")
    private Integer lineNo;

    @TableField("rule_code")
    private String ruleCode;

    /** {@link CodeIssueSource#name()}。 */
    @TableField("source")
    private String source;

    /** {@link Severity#name()}。 */
    @TableField("severity")
    private String severity;

    /** {@link CodeIssueStatus#name()}；默认 NEW。 */
    @TableField("status")
    private String status;

    @TableField("description")
    private String description;

    @TableField("suggestion")
    private String suggestion;

    /** AI 评审置信度 [0,1]；SAST 通常为 NULL。 */
    @TableField("confidence")
    private BigDecimal confidence;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    public CodeIssue() {
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

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
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
