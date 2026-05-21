package com.acrqg.platform.writeback.controller;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.infra.permission.PermissionEvaluator;
import com.acrqg.platform.infra.permission.ProjectRole;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.acrqg.platform.writeback.service.WritebackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Writeback 控制器（M09 / R14.7）。
 *
 * <p>对齐 design.md §8.7 与任务清单 B4-E.8：
 * <pre>
 * POST /api/v1/review-tasks/{id}/writeback/retry      已登录（service 内按项目 PROJECT_ADMIN 校验）
 * </pre>
 *
 * <p>"已登录"语义靠 {@code JwtAuthFilter} 强制；项目 PROJECT_ADMIN 角色由本控制器
 * 在调用 service 前显式校验——路径变量是 taskId 而非 projectId，{@code @RequirePermission}
 * 的 {@code projectIdParam} 不能直接套用，因此采用与 {@code GateWaiverController.approve}
 * 一致的"控制器内联校验"模式。
 *
 * <p>调用 {@link WritebackService#writeback}（同步阻塞）：等待最多 3 次重试完成，
 * 即便最终失败也<b>不</b>抛异常——失败信息已通过 task_log + WritebackFailedEvent
 * 暴露给前端。返回 {@code ApiResponse<Void>} 即表示"重试动作已被接受并完成"。
 *
 * <p>Covers: R14.7, R20.4, R23.1。
 */
@RestController
@RequestMapping("/api/v1/review-tasks")
@Tag(name = "Writeback", description = "状态回写手动重试（M09 / R14.7）")
public class WritebackController {

    private final WritebackService writebackService;
    private final ReviewTaskMapper reviewTaskMapper;
    private final PermissionEvaluator permissionEvaluator;

    public WritebackController(WritebackService writebackService,
                               ReviewTaskMapper reviewTaskMapper,
                               PermissionEvaluator permissionEvaluator) {
        this.writebackService = writebackService;
        this.reviewTaskMapper = reviewTaskMapper;
        this.permissionEvaluator = permissionEvaluator;
    }

    @Operation(summary = "手动重试状态回写",
            description = "项目内 PROJECT_ADMIN 可调用。同步执行 1+2+4s 三次指数退避重试；"
                    + " 最终失败仍返回 200 + ApiResponse.success（具体失败信息记录在 task_log）。")
    @PostMapping("/{id}/writeback/retry")
    public ApiResponse<Void> retry(@PathVariable("id") Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "id 不能为空");
        }
        ReviewTask task = reviewTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND,
                    ErrorCode.TASK_NOT_FOUND.getMessage());
        }

        // 权限校验：项目内 PROJECT_ADMIN
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();
        if (!permissionEvaluator.hasProjectRole(caller.id(), task.getProjectId(),
                new ProjectRole[]{ProjectRole.PROJECT_ADMIN})) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }

        // 同步执行；service 层不抛异常
        writebackService.writeback(id);
        return ApiResponse.success(null);
    }
}
