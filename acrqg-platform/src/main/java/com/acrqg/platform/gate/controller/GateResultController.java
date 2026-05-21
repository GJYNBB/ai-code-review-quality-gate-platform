package com.acrqg.platform.gate.controller;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.gate.dto.GateResultDTO;
import com.acrqg.platform.gate.service.GateResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门禁判定结果查询控制器（B3-F.6）。
 *
 * <p>对齐 design.md §8.7：
 * <pre>
 * GET /api/v1/review-tasks/{id}/gate-result    已登录（service 内按项目成员校验）
 * </pre>
 *
 * <p>"已登录"语义靠 {@code JwtAuthFilter} 强制；项目成员关系由
 * {@link GateResultService} 在内部按 {@code task.projectId} 实时校验——这是因为
 * 路径变量 {@code id} 指向<b>任务</b>而非<b>项目</b>，{@code @RequirePermission}
 * 的 {@code projectIdParam} 无法直接复用，故采用与 {@code ReviewTaskController.get}
 * 一致的"控制器无权限注解 + 服务层校验"模式。
 *
 * <p>Covers: R14.8, R16.1, R2.2, R23.1。
 */
@RestController
@RequestMapping("/api/v1/review-tasks")
@Tag(name = "GateResult", description = "门禁判定结果（M07 / R14.8 / R16.1）")
public class GateResultController {

    private final GateResultService gateResultService;

    public GateResultController(GateResultService gateResultService) {
        this.gateResultService = gateResultService;
    }

    @Operation(summary = "查询任务的门禁判定结果",
            description = "仅当任务所属项目的成员可访问，否则返回 PERMISSION_DENIED。"
                    + " 任务尚未走完 GATE_EVALUATING 阶段时返回 TASK_NOT_FOUND（message=\"gate result not generated\"）。")
    @GetMapping("/{id}/gate-result")
    public ApiResponse<GateResultDTO> get(@PathVariable("id") Long id) {
        return ApiResponse.success(gateResultService.get(id));
    }
}
