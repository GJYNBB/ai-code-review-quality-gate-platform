package com.acrqg.platform.gate.controller;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.gate.dto.QualityGateDTO;
import com.acrqg.platform.gate.dto.QualityGateSaveRequest;
import com.acrqg.platform.gate.service.QualityGateService;
import com.acrqg.platform.infra.permission.ProjectRole;
import com.acrqg.platform.infra.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 质量门禁配置控制器（M07，仅 CRUD，不含执行引擎）。
 *
 * <p>对齐 design.md §8.7 的接口清单：
 * <pre>
 * POST   /api/v1/projects/{id}/quality-gate     @RequirePermission(projectMember=true, projectRole=PROJECT_ADMIN)
 * GET    /api/v1/projects/{id}/quality-gate     @RequirePermission(projectMember=true)
 * GET    /api/v1/quality-gates/templates        已登录可见（无 @RequirePermission）
 * </pre>
 *
 * <p>"已登录"语义靠 {@code JwtAuthFilter} 强制：未携带合法 token 的请求会在过滤器
 * 阶段被拒（HTTP 401 / {@code AUTH_INVALID_TOKEN}），无需在控制器层重复声明。
 *
 * <p>路径前缀差异（{@code /projects/{id}/quality-gate} vs
 * {@code /quality-gates/templates}）由两个 {@code @RequestMapping} 注解的方法分别承载：
 * 模板查询是<b>公共</b>资源（与具体项目无关），因此不嵌入 {@code /projects/{id}}
 * 路径下，避免误用 {@code projectMember=true} 校验。同时这也使前端的 "使用模板"
 * 按钮可在<b>未选中项目</b>的页面上预加载默认 3 条规则。
 *
 * <p>Covers: R13.1, R13.2, R13.5, R23.1。
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "QualityGate", description = "质量门禁配置（M07 / R13）")
public class QualityGateController {

    private final QualityGateService qualityGateService;

    public QualityGateController(QualityGateService qualityGateService) {
        this.qualityGateService = qualityGateService;
    }

    // ---------------------------------------------------------------------
    // 项目作用域：保存 / 查询启用版本
    // ---------------------------------------------------------------------

    @Operation(summary = "保存质量门禁配置",
            description = "项目内 PROJECT_ADMIN 才能调用。每次保存生成新版本，"
                    + "旧版本自动 enabled=false。规则的 metric/operator/severity 取值非法时返回 GATE_RULE_INVALID。")
    @RequirePermission(projectMember = true, projectIdParam = "id",
            projectRole = ProjectRole.PROJECT_ADMIN)
    @PostMapping("/projects/{id}/quality-gate")
    public ApiResponse<QualityGateDTO> save(@PathVariable("id") Long projectId,
                                            @Valid @RequestBody QualityGateSaveRequest request) {
        return ApiResponse.success(qualityGateService.save(projectId, request));
    }

    @Operation(summary = "查询项目当前启用的质量门禁",
            description = "任意项目成员可访问。当前未配置启用版本时返回 data=null。")
    @RequirePermission(projectMember = true, projectIdParam = "id")
    @GetMapping("/projects/{id}/quality-gate")
    public ApiResponse<QualityGateDTO> getEnabled(@PathVariable("id") Long projectId) {
        return ApiResponse.success(qualityGateService.getEnabled(projectId));
    }

    // ---------------------------------------------------------------------
    // 模板：与项目无关，已登录可见
    // ---------------------------------------------------------------------

    @Operation(summary = "查询默认门禁模板",
            description = "返回 design §13.5 / R13.5 的 3 条默认规则，"
                    + "供前端 \"使用模板\" 按钮预加载。返回 DTO 的 id/projectId/version 等字段为 null。")
    @GetMapping("/quality-gates/templates")
    public ApiResponse<QualityGateDTO> getDefaultTemplate() {
        return ApiResponse.success(qualityGateService.getDefaultTemplate());
    }
}
