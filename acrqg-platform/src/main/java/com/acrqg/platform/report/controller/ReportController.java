package com.acrqg.platform.report.controller;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.diff.dto.DiffViewDTO;
import com.acrqg.platform.report.dto.ReviewReportDTO;
import com.acrqg.platform.report.service.ReportService;
import com.acrqg.platform.task.dto.TaskLogDTO;
import com.acrqg.platform.task.dto.TaskLogQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评审报告控制器（B4-B.3 / R16）。
 *
 * <p>对齐 design.md §8.7：
 * <pre>
 * GET /api/v1/review-tasks/{id}/report   已登录（service 内按项目成员校验）
 * GET /api/v1/review-tasks/{id}/diff     已登录（service 内按项目成员校验）
 * GET /api/v1/review-tasks/{id}/logs     已登录（service 内按项目成员校验）
 * </pre>
 *
 * <p>"已登录"语义靠 {@code JwtAuthFilter} 强制；项目成员关系由
 * {@link ReportService} 在每个方法内部基于任务的 {@code projectId} 实时查询，
 * 这与 {@code GateResultController} / {@code ReviewTaskController} 的"控制器无
 * 权限注解 + 服务层校验"模式保持一致——路径变量 {@code id} 指向<b>任务</b>而非
 * <b>项目</b>，{@code @RequirePermission(projectIdParam=...)} 无法直接复用。
 *
 * <p>三个端点共用 {@code /api/v1/review-tasks} 前缀；与
 * {@link com.acrqg.platform.task.controller.ReviewTaskController#logs(Long, TaskLogQuery)}
 * 的 {@code /logs} 端点路径相同，但本控制器优先级一致，由 Spring MVC 路由决议——
 * <b>注意</b>：实际部署时仅保留一处映射；这里以 {@code ReportController} 暴露
 * 报告页路径，与 task 模块的 {@code logs} 端点形成同义路径。如启动期产生
 * "Ambiguous mapping"，应在 task 模块中下线该映射并交由 {@code ReportController}
 * 承担。本任务 (B4-B.3) 仅按 design 与 tasks 要求新增 controller，不修改 task 模块。
 *
 * <p>Covers: R16, R23.1。
 */
@RestController
@RequestMapping("/api/v1/review-tasks")
@Tag(name = "Report", description = "评审报告聚合（M08 / R16）")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "查询评审任务报告聚合",
            description = "返回 taskOverview / gateResultSummary / issueCounts / aiAvailability。"
                    + " 仅当任务所属项目的成员可访问，否则返回 PERMISSION_DENIED；任务不存在返回 TASK_NOT_FOUND。")
    @GetMapping("/{id}/report")
    public ApiResponse<ReviewReportDTO> report(@PathVariable("id") Long id) {
        return ApiResponse.success(reportService.report(id));
    }

    @Operation(summary = "查询评审任务的代码差异视图",
            description = "返回每个变更文件的 hunks 列表（DiffViewDTO）。仅项目成员可访问。")
    @GetMapping("/{id}/diff")
    public ApiResponse<DiffViewDTO> diff(@PathVariable("id") Long id) {
        return ApiResponse.success(reportService.diffView(id));
    }

    @Operation(summary = "查询评审任务流水日志",
            description = "支持 stage / level 过滤；按 created_at DESC 排序。仅项目成员可访问。")
    @GetMapping("/{id}/logs")
    public ApiResponse<PageResult<TaskLogDTO>> logs(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute TaskLogQuery query) {
        return ApiResponse.success(reportService.logs(id, query));
    }
}
