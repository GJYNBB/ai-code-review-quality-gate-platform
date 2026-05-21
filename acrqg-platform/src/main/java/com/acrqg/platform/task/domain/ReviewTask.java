package com.acrqg.platform.task.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 评审任务领域对象（DO）。
 *
 * <p>对应数据库表 {@code review_task}（V30__m03_review_task.sql）。
 *
 * <p>状态字段 {@code status} 与 {@code trigger_type} 在 DB 层有 CHECK 约束；
 * Java 端使用字符串存储，由 Service 通过 {@link ReviewTaskStatus#name()} /
 * {@link TriggerType#name()} 转换。{@code score} / {@code aiRiskScore} /
 * {@code startedAt} / {@code finishedAt} 在创建时为 {@code null}，由 Worker
 * 在执行各阶段后回填。
 *
 * <p>Covers: R7.4, R8.3, R9.1, R9.7。
 */
@TableName(value = "review_task", autoResultMap = true)
public class ReviewTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务可读编号 RT{yyyyMMdd}{seq}（uk_review_task_no）。 */
    @TableField("task_no")
    private String taskNo;

    @TableField("project_id")
    private Long projectId;

    /** PR/MR 编号（外部代码平台），允许为空。 */
    @TableField("pr_id")
    private String prId;

    @TableField("source_branch")
    private String sourceBranch;

    @TableField("target_branch")
    private String targetBranch;

    /** 触发评审的 commit；与 (project_id, pr_id) 共同构成幂等键。 */
    @TableField("commit_sha")
    private String commitSha;

    /** 状态机当前状态（design §6.3.1）。 */
    @TableField("status")
    private String status;

    /** 触发来源：WEBHOOK / MANUAL / CI_CD / RETRY。 */
    @TableField("trigger_type")
    private String triggerType;

    /** 门禁评分 [0,100]，由 GateEngine 写入。 */
    @TableField("score")
    private Integer score;

    /** AI 风险分 [0,100]，由 AI 阶段写入。 */
    @TableField("ai_risk_score")
    private Integer aiRiskScore;

    /** AI 服务在本任务内是否可用；FALSE 表示走降级（R12.5）。 */
    @TableField("ai_available")
    private Boolean aiAvailable;

    /** 重试次数，首次为 1。 */
    @TableField("attempt")
    private Integer attempt;

    /** 触发用户主键；webhook 触发时为 NULL。 */
    @TableField("created_by")
    private Long createdBy;

    @TableField("started_at")
    private OffsetDateTime startedAt;

    @TableField("finished_at")
    private OffsetDateTime finishedAt;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    public ReviewTask() {
        // for MyBatis-Plus
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getPrId() {
        return prId;
    }

    public void setPrId(String prId) {
        this.prId = prId;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public void setSourceBranch(String sourceBranch) {
        this.sourceBranch = sourceBranch;
    }

    public String getTargetBranch() {
        return targetBranch;
    }

    public void setTargetBranch(String targetBranch) {
        this.targetBranch = targetBranch;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
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

    public Integer getAttempt() {
        return attempt;
    }

    public void setAttempt(Integer attempt) {
        this.attempt = attempt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
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
