package com.acrqg.platform.report.service;

import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.diff.dto.DiffViewDTO;
import com.acrqg.platform.report.dto.ReviewReportDTO;
import com.acrqg.platform.task.dto.TaskLogDTO;
import com.acrqg.platform.task.dto.TaskLogQuery;

/**
 * 评审报告服务（design.md §6.8 / B4-B）。
 *
 * <p>对外暴露 {@code GET /api/v1/review-tasks/{id}/report}、
 * {@code GET /api/v1/review-tasks/{id}/diff}、
 * {@code GET /api/v1/review-tasks/{id}/logs} 三个端点的业务逻辑：
 * <ul>
 *   <li>{@link #report(Long)} —— 一次返回任务概览 / 门禁摘要 / 问题二维聚合 /
 *       AI 可用性。命中 Caffeine 缓存（key=taskId, TTL=10s）减少 DB 压力。</li>
 *   <li>{@link #diffView(Long)} —— 透传
 *       {@link com.acrqg.platform.diff.service.DiffViewService#diffView(Long)}
 *       结果；本任务暂不在此处叠加问题标记（B3-C.6 的 markIssueLines hook 已存在，
 *       不影响调用方）。</li>
 *   <li>{@link #logs(Long, TaskLogQuery)} —— 任务流水分页查询，支持 stage / level
 *       过滤；与 {@code ReviewTaskService.pageLogs} 行为一致，但权限校验在
 *       {@code ReportService} 内部完成，便于报告页端到端调用。</li>
 * </ul>
 *
 * <p>异常路径（与 {@link com.acrqg.platform.gate.service.GateResultService} 对齐）：
 * <ul>
 *   <li>任务不存在 → {@link com.acrqg.platform.common.exception.BusinessException}
 *       ({@link com.acrqg.platform.common.api.ErrorCode#TASK_NOT_FOUND})；</li>
 *   <li>当前用户不是任务所属项目的成员 → {@link com.acrqg.platform.common.exception.BusinessException}
 *       ({@link com.acrqg.platform.common.api.ErrorCode#PERMISSION_DENIED})。</li>
 * </ul>
 *
 * <p>Covers: R16.1, R16.2, R16.4, R16.5, R16.6, R2.2, R9.7。
 */
public interface ReportService {

    /**
     * 聚合任务报告：taskOverview + gateResultSummary + issueCounts + aiAvailability。
     *
     * <p>Caffeine 缓存：key=taskId，TTL=10s，maximumSize=500。
     *
     * <p>{@code gate_result} 不存在时（任务尚未走完 GATE_EVALUATING 阶段），
     * {@code gateResultSummary} 字段为 {@code null}，但仍正常返回 taskOverview /
     * issueCounts / aiAvailability，不阻断报告查询。
     *
     * @param taskId 任务主键
     * @return 评审报告 DTO，永不为 {@code null}
     */
    ReviewReportDTO report(Long taskId);

    /**
     * 任务级 diff 视图（透传 {@code DiffViewService}）。
     *
     * <p>本服务负责权限校验；具体实现委托给
     * {@link com.acrqg.platform.diff.service.DiffViewService}，因此返回值与
     * {@code diffView(taskId)} 一致——{@link DiffViewDTO}。
     *
     * @param taskId 任务主键
     * @return diff 视图，永不为 {@code null}（无变更文件时返回空 view）
     */
    DiffViewDTO diffView(Long taskId);

    /**
     * 任务流水分页查询。
     *
     * <p>排序：{@code created_at DESC, id DESC}；过滤：{@code stage} / {@code level}
     * 任意为空时不参与过滤。
     *
     * @param taskId 任务主键
     * @param query  分页 + 过滤参数；为 {@code null} 时退化为 {@code page=1,pageSize=50}
     * @return 流水分页结果
     */
    PageResult<TaskLogDTO> logs(Long taskId, TaskLogQuery query);
}
