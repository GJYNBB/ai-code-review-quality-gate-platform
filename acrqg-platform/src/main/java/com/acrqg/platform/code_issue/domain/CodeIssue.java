package com.acrqg.platform.code_issue.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 代码问题领域对象（DO），对应数据库表 {@code code_issue}（design.md §7.2）。
 *
 * <p><b>合并约定</b>：B3-D（feat/m05-scanner）与 B3-E（feat/m06-ai）都向
 * {@code code_issue} 表写入；本类是两条分支的最小共享契约。在 worktree 起点
 * 没有 B3-D 写入的现有定义，因此 B3-E 提供该最小定义；当后续合并 B3-D 的
 * 完整版本时，由集成 PR 解决冲突——B3-D 的版本胜出，但字段集合应保持兼容
 * （字段名 / 类型 / 表名一致）。
 *
 * <p>表 DDL（参考 design §7.2）：
 * <pre>
 * CREATE TABLE code_issue (
 *   id          BIGSERIAL PRIMARY KEY,
 *   task_id     BIGINT NOT NULL REFERENCES review_task(id) ON DELETE CASCADE,
 *   file_path   VARCHAR(512) NOT NULL,
 *   line_no     INT,
 *   rule_code   VARCHAR(128),
 *   source      VARCHAR(16) NOT NULL CHECK (source IN ('SAST','AI','MANUAL')),
 *   severity    VARCHAR(16) NOT NULL CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW','INFO')),
 *   status      VARCHAR(16) NOT NULL DEFAULT 'NEW'
 *               CHECK (status IN ('NEW','CONFIRMED','FALSE_POSITIVE','PENDING_VERIFY','CLOSED','REOPENED')),
 *   description TEXT NOT NULL,
 *   suggestion  TEXT,
 *   confidence  NUMERIC(5,4) CHECK (confidence IS NULL OR (confidence &gt;= 0 AND confidence &lt;= 1)),
 *   created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 *
 * <p>Covers: R11.2, R12.3, R16.2, R17.1。
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

    /** {@link CodeIssueStatus#name()}（默认 {@code NEW}）。 */
    @TableField("status")
    private String status;

    @TableField("description")
    private String description;

    @TableField("suggestion")
    private String suggestion;

    /** AI 阶段写入的置信度 [0,1]；SAST 来源为 {@code null}。 */
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
