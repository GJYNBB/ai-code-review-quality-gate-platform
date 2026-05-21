package com.acrqg.platform.task.dto;

import java.time.OffsetDateTime;

/**
 * 评审任务对外视图（design.md §8.4）。
 *
 * <p>由 {@code ReviewTaskService} 在 create / get / page / retry / cancel 接口中返回。
 *
 * <p>Covers: R7, R8, R9, R16.5。
 *
 * @param id            主键
 * @param taskNo        业务编号 RT{yyyyMMdd}{seq}
 * @param projectId     所属项目主键
 * @param prId          PR/MR 编号（可空）
 * @param sourceBranch  源分支
 * @param targetBranch  目标分支
 * @param commitSha     commit SHA
 * @param status        当前状态（{@link com.acrqg.platform.task.domain.ReviewTaskStatus}）
 * @param triggerType   触发来源（{@link com.acrqg.platform.task.domain.TriggerType}）
 * @param score         门禁评分（终态前为 {@code null}）
 * @param aiRiskScore   AI 风险分（AI 阶段后写入）
 * @param aiAvailable   AI 是否可用
 * @param attempt       重试次数（首次为 1）
 * @param createdBy     触发用户主键（webhook 触发时为 {@code null}）
 * @param startedAt     开始时间（PENDING→FETCHING_DIFF 跃迁时写入）
 * @param finishedAt    结束时间（终态时写入）
 * @param createdAt     创建时间
 * @param updatedAt     最近更新时间
 */
public record ReviewTaskDTO(
        Long id,
        String taskNo,
        Long projectId,
        String prId,
        String sourceBranch,
        String targetBranch,
        String commitSha,
        String status,
        String triggerType,
        Integer score,
        Integer aiRiskScore,
        boolean aiAvailable,
        int attempt,
        Long createdBy,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
