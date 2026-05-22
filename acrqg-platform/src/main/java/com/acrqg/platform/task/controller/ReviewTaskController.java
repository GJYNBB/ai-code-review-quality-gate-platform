package com.acrqg.platform.task.controller;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.infra.permission.Role;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.acrqg.platform.task.domain.TriggerType;
import com.acrqg.platform.task.dto.CancelRequest;
import com.acrqg.platform.task.dto.ReviewTaskCreateRequest;
import com.acrqg.platform.task.dto.ReviewTaskDTO;
import com.acrqg.platform.task.dto.ReviewTaskQuery;
import com.acrqg.platform.task.dto.RetryRequest;
import com.acrqg.platform.task.service.ReviewTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评审任务（M03 / R7~R9 / R16.5）控制器。
 *
 * <p>对齐 design.md §8.7：
 * <pre>
 * POST   /api/v1/review-tasks                      已登录（service 内按项目成员校验）
 * GET    /api/v1/review-tasks                      已登录（service 内按项目成员过滤）
 * GET    /api/v1/review-tasks/{id}                 已登录（service 内按项目成员校验）
 * POST   /api/v1/review-tasks/{id}/retry           已登录（service 内按 REVIEWER/PROJECT_ADMIN 校验）
 * POST   /api/v1/review-tasks/{id}/cancel          已登录（service 内按 PROJECT_ADMIN 校验）
 * </pre>
 *
 * <p>{@code GET /api/v1/review-tasks/{id}/logs} 由
 * {@link com.acrqg.platform.report.controller.ReportController#logs} 承担（B4-B.3）；
 * {@link com.acrqg.platform.task.service.ReviewTaskService#pageLogs} 服务方法仍保留，
 * 由 {@code ReportService} 调用。
 *
 * <p>"已登录"语义靠 {@code JwtAuthFilter} 强制；项目成员关系由
 * {@link ReviewTaskService} 在每个方法内部基于任务的 {@code projectId} 实时查询，
 * 这样无需在控制器层指定 {@code projectIdParam}（路径变量是任务 id 而非项目 id）。
 *
 * <p>幂等：{@code POST /api/v1/review-tasks} 支持请求头
 * {@code Idempotency-Key}（R8.4）；header 缺失时退化为三元组幂等。
 *
 * <p>触发来源（R8.1 / R2.5）：
 * <ul>
 *   <li>当前用户具有全局 {@link Role#CI_CD} 角色 → {@link TriggerType#CI_CD}；</li>
 *   <li>否则 → {@link TriggerType#MANUAL}。</li>
 * </ul>
 *
 * <p>Covers: R8, R9, R16.5, R23.1。
 */
@RestController
@RequestMapping("/api/v1/review-tasks")
@Tag(name = "ReviewTask", description = "评审任务（M03 / R7~R9 / R16.5）")
public class ReviewTaskController {

    private final ReviewTaskService reviewTaskService;

    public ReviewTaskController(ReviewTaskService reviewTaskService) {
        this.reviewTaskService = reviewTaskService;
    }

    @Operation(summary = "手动创建评审任务",
            description = "已登录用户可调用。Idempotency-Key 头可选；触发来源由当前用户角色决定（CI_CD 角色→CI_CD，否则→MANUAL）。"
                    + " 创建后任务进入 PENDING 状态并入队 review-task-stream（R7.6 / R8）。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewTaskDTO> create(
            @Valid @RequestBody ReviewTaskCreateRequest request,
            @Parameter(description = "可选幂等键，相同值在 24h 内返回同一任务",
                    name = "Idempotency-Key", in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TriggerType trigger = resolveTriggerType();
        return ApiResponse.success(reviewTaskService.create(request, idempotencyKey, trigger));
    }

    @Operation(summary = "评审任务分页查询",
            description = "已登录用户可调用，service 内按项目成员关系过滤（R2.2）。"
                    + " 必须传入 projectId 才能列出该项目的任务；未传时返回空页。")
    @GetMapping
    public ApiResponse<PageResult<ReviewTaskDTO>> page(@Valid @ModelAttribute ReviewTaskQuery query) {
        return ApiResponse.success(reviewTaskService.page(query));
    }

    @Operation(summary = "评审任务详情",
            description = "仅当任务所属项目的成员可访问，否则返回 PERMISSION_DENIED。")
    @GetMapping("/{id}")
    public ApiResponse<ReviewTaskDTO> get(@PathVariable("id") Long id) {
        return ApiResponse.success(reviewTaskService.get(id));
    }

    // 注：{@code GET /api/v1/review-tasks/{id}/logs} 端点已迁移至
    // {@link com.acrqg.platform.report.controller.ReportController#logs} (B4-B.3)
    // —— 二者承担同一报告页用例，原控制器保留路径会导致 Spring "Ambiguous mapping"。
    // {@link com.acrqg.platform.task.service.ReviewTaskService#pageLogs} 仍由
    // ReportService 调用，业务行为完全保留。

    @Operation(summary = "重试评审任务",
            description = "项目内 REVIEWER 或 PROJECT_ADMIN 可调用；仅终态（PASSED/FAILED_GATE/EXECUTION_FAILED）可重试，"
                    + "否则返回 TASK_NOT_RETRYABLE（R9.5）。")
    @PostMapping("/{id}/retry")
    public ApiResponse<ReviewTaskDTO> retry(@PathVariable("id") Long id,
                                            @Valid @RequestBody RetryRequest request) {
        return ApiResponse.success(reviewTaskService.retry(id, request));
    }

    @Operation(summary = "取消评审任务",
            description = "项目内 PROJECT_ADMIN 可调用；仅 PENDING 状态可取消（R9.6）。")
    @PostMapping("/{id}/cancel")
    public ApiResponse<ReviewTaskDTO> cancel(@PathVariable("id") Long id,
                                             @Valid @RequestBody CancelRequest request) {
        return ApiResponse.success(reviewTaskService.cancel(id, request));
    }

    /**
     * 根据当前用户角色决定 {@link TriggerType}。
     *
     * <p>CI_CD 角色 → {@link TriggerType#CI_CD}（R2.5）；
     * 其他 → {@link TriggerType#MANUAL}（R8.1）。
     */
    private TriggerType resolveTriggerType() {
        AuthenticatedUser user = CurrentUserHolder.requireCurrent();
        if (user.hasRole(Role.CI_CD.code())) {
            return TriggerType.CI_CD;
        }
        return TriggerType.MANUAL;
    }
}
