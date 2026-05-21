package com.acrqg.platform.code_issue.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 问题状态变更历史 DO（V32__m05_code_issue.sql）。
 *
 * <p>由 B4-A {@code IssueService} 在状态流转时追加。本类作为共享数据模型先在 B3-D
 * 中定义。
 *
 * <p>Covers: R17。
 */
@TableName(value = "issue_history", autoResultMap = true)
public class IssueHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("code_issue_id")
    private Long codeIssueId;

    @TableField("from_status")
    private String fromStatus;

    @TableField("to_status")
    private String toStatus;

    @TableField("comment")
    private String comment;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("changed_at")
    private OffsetDateTime changedAt;

    public IssueHistory() {
        // for MyBatis-Plus
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCodeIssueId() {
        return codeIssueId;
    }

    public void setCodeIssueId(Long codeIssueId) {
        this.codeIssueId = codeIssueId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(OffsetDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
