package com.acrqg.platform.gate.controller;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.gate.dto.GateWaiverApprovalRequest;
import com.acrqg.platform.gate.dto.GateWaiverDTO;
import com.acrqg.platform.gate.dto.GateWaiverRequest;
import com.acrqg.platform.gate.service.GateWaiverService;
import com.acrqg.platform.infra.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门禁豁免审批控制器（M07 / R15）。
 *
 * <p>对齐 design.md §8.7：
 * <pre>
 * POST /api/v1/review-tasks/{taskId}/waivers          已登录（service 内按项目成员校验）
 * POST /api/v1/waivers/{waiverId}/approval            已登录（service 内按项目 PROJECT_ADMIN 校验）
 * GET  /api/v1/projects/{projectId}/waivers           @RequirePermission(projectMember=true)
 * GET  /api/v1/waivers/{id}                           已登录（service 内按项目成员校验）
 * </pre>
 *
 * <p>"已登录"语义靠 {@code JwtAuthFilter} 强制；当路径变量是 {@code taskId} /
 * {@code waiverId}（而非 {@code projectId}）时，{@code @RequirePermission}
 * 的 {@code projectIdParam} 不能直接套用——这与 {@code GateResultController} /
 * {@code ReviewTaskController.get} 一致，由 service 内部解析项目成员关系。
 *
 * <p>路径分两个 prefix：
 * <ul>
 *   <li>申请：嵌入到 {@code /review-tasks/{taskId}} 下，符合 RESTful 资源层级；</li>
 *   <li>审批 / 单条查询：以 {@code /waivers/{waiverId}} 为根资源；</li>
 *   <li>项目维度列表：嵌入到 {@code /projects/{projectId}} 下，可使用 {@code @RequirePermission}。</li>
 * </ul>
 *
 * <p>Covers: R15.1, R15.3, R15.5, R15.6, R23.1。
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "GateWaiver", description = "门禁豁免审批（M07 / R15）")
public class GateWaiverController {

    private final GateWaiverService gateWaiverService;

    public GateWaiverController(GateWaiverService gateWaiverService) {
        this.gateWaiverService = gateWaiverService;
    }

    // ---------------------------------------------------------------------
    // 申请：POST /review-tasks/{taskId}/waivers
    // ---------------------------------------------------------------------

    @Operation(summary = "提交门禁豁免申请",
            description = "已登录用户可调用；service 层校验项目成员（R2.2）+ task.status=FAILED_GATE（R15.1）"
                    + " + 同任务无 PENDING 申请（R15.6）。reason ≥ 10 字符（R15.2）。")
    @PostMapping("/review-tasks/{taskId}/waivers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GateWaiverDTO> apply(@PathVariable("taskId") Long taskId,
                                            @Valid @RequestBody GateWaiverRequest request) {
        return ApiResponse.success(gateWaiverService.apply(taskId, request));
    }

    // ---------------------------------------------------------------------
    // 审批：POST /waivers/{waiverId}/approval
    // ---------------------------------------------------------------------

    @Operation(summary = "审批门禁豁免申请",
            description = "已登录用户可调用；service 层校验项目内 PROJECT_ADMIN 角色。"
                    + " approve=true 时把 GateResult 转 WAIVED + task 转 PASSED（R15.3 / R15.4）；"
                    + " approve=false 时仅记录拒绝（R15.5）。waiver 状态非 PENDING 时返回 VALIDATION_ERROR。")
    @PostMapping("/waivers/{waiverId}/approval")
    public ApiResponse<Void> approve(@PathVariable("waiverId") Long waiverId,
                                     @Valid @RequestBody GateWaiverApprovalRequest request) {
        gateWaiverService.approve(waiverId, request);
        return ApiResponse.success(null);
    }

    // ---------------------------------------------------------------------
    // 项目维度列表：GET /projects/{projectId}/waivers?status=PENDING&...
    // ---------------------------------------------------------------------

    @Operation(summary = "按项目分页查询豁免申请（默认 PENDING）",
            description = "项目成员可访问。当前实现仅返回 PENDING 状态行；"
                    + " status 参数保留以兼容前端，未来可扩展为多状态过滤。")
    @RequirePermission(projectMember = true, projectIdParam = "projectId")
    @GetMapping("/projects/{projectId}/waivers")
    public ApiResponse<PageResult<GateWaiverDTO>> page(
            @PathVariable("projectId") Long projectId,
            @Parameter(description = "状态过滤；目前实现仅返回 PENDING")
            @RequestParam(name = "status", required = false, defaultValue = "PENDING") String status,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "20") int pageSize) {
        // 当前 service 方法仅支持 PENDING 列表；其他取值忽略并返回 PENDING（与前端契约一致）
        return ApiResponse.success(gateWaiverService.pendingByProject(projectId, page, pageSize));
    }

    // ---------------------------------------------------------------------
    // 单条查询：GET /waivers/{id}
    // ---------------------------------------------------------------------

    @Operation(summary = "查询单条豁免申请",
            description = "已登录用户可调用；service 层按项目成员校验。")
    @GetMapping("/waivers/{id}")
    public ApiResponse<GateWaiverDTO> get(@PathVariable("id") Long id) {
        return ApiResponse.success(gateWaiverService.get(id));
    }
}
