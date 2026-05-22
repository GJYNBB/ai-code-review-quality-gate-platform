package com.acrqg.platform.report.dto;

import java.time.OffsetDateTime;

/**
 * 评审任务概览（design.md §6.8 / R16.1）。
 *
 * <p>{@link ReviewReportDTO#taskOverview()} 的承载体，对应 R16.1 中的
 * "taskNo / PR / commit / status / score" 字段集合，并补充时间 / 分支 /
 * 项目名等便于前端直接渲染的元信息。
 *
 * <p>{@link #durationSeconds} 由 Service 层基于 {@code started_at} 与
 * {@code finished_at} 计算（任一为 {@code null} 时返回 {@code null}），
 * 避免前端再次访问后端做减法。
 *
 * <p>Covers: R16.1。
 *
 * @param taskId          任务主键
 * @param taskNo          业务编号 RT{yyyyMMdd}{seq}
 * @param projectId       所属项目主键
 * @param projectName     所属项目名称（缺失时为 {@code null}）
 * @param prId            PR/MR 编号；可空（直接 push 触发场景）
 * @param commitSha       commit SHA
 * @param sourceBranch    源分支
 * @param targetBranch    目标分支
 * @param status          {@link com.acrqg.platform.task.domain.ReviewTaskStatus#name()}
 * @param score           门禁评分；任务未到终态时为 {@code null}
 * @param durationSeconds 执行耗时（秒）；started/finished 任一缺失时为 {@code null}
 * @param createdAt       创建时间
 * @param finishedAt      结束时间；可空
 */
public record TaskOverviewDTO(
        Long taskId,
        String taskNo,
        Long projectId,
        String projectName,
        String prId,
        String commitSha,
        String sourceBranch,
        String targetBranch,
        String status,
        Integer score,
        Long durationSeconds,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt
) {
}
