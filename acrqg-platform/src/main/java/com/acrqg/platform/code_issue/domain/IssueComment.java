package com.acrqg.platform.code_issue.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 问题评论 DO（V32__m05_code_issue.sql）。
 *
 * <p>由 B4-A {@code IssueService} 添加评论时写入。本类作为共享数据模型先在 B3-D
 * 中定义。
 *
 * <p>Covers: R16.3。
 */
@TableName(value = "issue_comment", autoResultMap = true)
public class IssueComment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("code_issue_id")
    private Long codeIssueId;

    @TableField("content")
    private String content;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    public IssueComment() {
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
