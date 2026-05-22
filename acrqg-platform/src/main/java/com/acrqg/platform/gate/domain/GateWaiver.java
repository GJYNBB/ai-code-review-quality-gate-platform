package com.acrqg.platform.gate.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 门禁豁免申请 DO，对应数据库表 {@code gate_waiver}（V51__m07_gate_waiver.sql）。
 *
 * <p>表结构（B4-E.1）：
 * <pre>
 * CREATE TABLE gate_waiver (
 *   id               BIGSERIAL    PRIMARY KEY,
 *   task_id          BIGINT       NOT NULL REFERENCES review_task(id) ON DELETE CASCADE,
 *   reason           TEXT         NOT NULL,
 *   status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
 *                    CHECK (status IN ('PENDING','APPROVED','REJECTED')),
 *   applicant_id     BIGINT       NOT NULL REFERENCES "user"(id),
 *   approver_id      BIGINT       REFERENCES "user"(id),
 *   approved_at      TIMESTAMPTZ,
 *   approval_comment TEXT,
 *   created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
 *   updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
 * );
 * </pre>
 *
 * <p>{@code status} 字段在 Java 端使用字符串存储，由 Service 通过
 * {@link GateWaiverStatus#name()} 转换。{@code approver_id} / {@code approved_at} /
 * {@code approval_comment} 在 PENDING 状态下均为 {@code null}，由审批操作回填。
 *
 * <p>Covers: R15.1, R15.2, R15.3, R15.5, R15.6。
 */
@TableName(value = "gate_waiver", autoResultMap = true)
public class GateWaiver {

    /** 主键，{@code BIGSERIAL}，由数据库自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联评审任务主键。 */
    @TableField("task_id")
    private Long taskId;

    /** 申请原因（R15.2，至少 10 字符；服务层强制）。 */
    @TableField("reason")
    private String reason;

    /** 申请状态（{@link GateWaiverStatus#name()}）。 */
    @TableField("status")
    private String status;

    /** 申请人用户主键。 */
    @TableField("applicant_id")
    private Long applicantId;

    /** 审批人用户主键；PENDING 时为 {@code null}。 */
    @TableField("approver_id")
    private Long approverId;

    /** 审批时间；PENDING 时为 {@code null}。 */
    @TableField("approved_at")
    private OffsetDateTime approvedAt;

    /** 审批意见，可空。 */
    @TableField("approval_comment")
    private String approvalComment;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    public GateWaiver() {
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getApprovalComment() {
        return approvalComment;
    }

    public void setApprovalComment(String approvalComment) {
        this.approvalComment = approvalComment;
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
