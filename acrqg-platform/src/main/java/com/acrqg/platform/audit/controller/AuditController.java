package com.acrqg.platform.audit.controller;

import com.acrqg.platform.audit.dto.AuditLogDTO;
import com.acrqg.platform.audit.dto.AuditQuery;
import com.acrqg.platform.audit.service.AuditService;
import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.infra.permission.RequirePermission;
import com.acrqg.platform.infra.permission.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志控制器（仅 SYSTEM_ADMIN）。
 *
 * <p>暴露唯一接口 {@code GET /api/v1/admin/audit-logs}：
 * <ul>
 *   <li>URL 归属 {@code admin} 域（与系统管理界面 UI-010 对齐），但代码归属
 *       {@code audit} 模块（保持内聚 + 避免 admin 模块对 audit 内部数据建模产生反向依赖）；
 *       这与 design.md §5.2 / §6.10 / §8.7 的接口清单一致。</li>
 *   <li>权限：{@code @RequirePermission(role = Role.SYSTEM_ADMIN)}；非 SYSTEM_ADMIN
 *       调用由 {@code PermissionAspect} 拒绝并返回 {@code PERMISSION_DENIED}（HTTP 403）。
 *       这满足 R22.4 中"仅 SYSTEM_ADMIN 角色可调用查询接口"的要求。</li>
 * </ul>
 *
 * <p>响应：使用 {@link ApiResponse} 统一包装，{@code data} 为
 * {@link PageResult}&lt;{@link AuditLogDTO}&gt;。{@code AuditLogDTO.detail}
 * 已经在写入时完成敏感字段掩码，前端可直接展示。
 *
 * <p>Covers: R22.2, R22.4。
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Audit", description = "审计日志（M01 / R22）")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 分页查询审计日志。
     *
     * <p>查询参数走 {@code @ModelAttribute} 绑定到 {@link AuditQuery} record；
     * Bean Validation 通过 {@code @Valid} 触发，违规返回 {@code VALIDATION_ERROR}
     * （由 {@code GlobalExceptionHandler} 统一处理）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "审计日志分页查询",
            description = "仅 SYSTEM_ADMIN 可调用（R22.4）。"
                    + "支持按 operator / action / 创建时间区间过滤，"
                    + "结果按 created_at DESC, id DESC 排序。")
    @RequirePermission(role = Role.SYSTEM_ADMIN)
    @GetMapping
    public ApiResponse<PageResult<AuditLogDTO>> page(@Valid @ModelAttribute AuditQuery query) {
        PageResult<AuditLogDTO> data = auditService.page(query);
        return ApiResponse.success(data);
    }
}
