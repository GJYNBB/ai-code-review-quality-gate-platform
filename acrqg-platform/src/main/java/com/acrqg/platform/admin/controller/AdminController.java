package com.acrqg.platform.admin.controller;

import com.acrqg.platform.admin.dto.ModelConfigCreateRequest;
import com.acrqg.platform.admin.dto.ModelConfigDTO;
import com.acrqg.platform.admin.dto.ModelConfigUpdateRequest;
import com.acrqg.platform.admin.dto.ScannerConfigDTO;
import com.acrqg.platform.admin.dto.ScannerConfigRequest;
import com.acrqg.platform.admin.dto.SystemParamDTO;
import com.acrqg.platform.admin.dto.SystemParamUpdateRequest;
import com.acrqg.platform.admin.service.AdminService;
import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.infra.permission.RequirePermission;
import com.acrqg.platform.infra.permission.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理控制器（M10）。
 *
 * <p>对齐 design.md §8.7 的接口清单：
 * <pre>
 * GET    /api/v1/admin/model-configs                 @RequirePermission(role=SYSTEM_ADMIN)
 * POST   /api/v1/admin/model-configs                 @RequirePermission(role=SYSTEM_ADMIN)
 * PATCH  /api/v1/admin/model-configs/{id}            @RequirePermission(role=SYSTEM_ADMIN)
 * GET    /api/v1/admin/scanners                      @RequirePermission(role=SYSTEM_ADMIN)
 * POST   /api/v1/admin/scanners                      @RequirePermission(role=SYSTEM_ADMIN)
 * GET    /api/v1/admin/system-params                 @RequirePermission(role=SYSTEM_ADMIN)
 * PATCH  /api/v1/admin/system-params/{key}           @RequirePermission(role=SYSTEM_ADMIN)
 * </pre>
 *
 * <p>所有方法在类级别声明 {@link RequirePermission}({@code role = Role.SYSTEM_ADMIN})；
 * {@link com.acrqg.platform.infra.permission.PermissionAspect} 在请求进入方法前完成
 * 角色校验，未通过抛出 {@link org.springframework.security.access.AccessDeniedException}
 * 由 {@code GlobalExceptionHandler} 映射为 {@code PERMISSION_DENIED} / HTTP 403（R21.6）。
 *
 * <p>请求体校验由 Bean Validation（{@code @Valid}）触发；校验失败由
 * {@code GlobalExceptionHandler} 映射为 {@code VALIDATION_ERROR} / HTTP 400。
 *
 * <p>响应统一使用 {@link ApiResponse} 包装；{@link com.acrqg.platform.infra.log.ResponseMaskingAspect}
 * 在返回前对常见敏感字段名（apiKey / accessToken / webhookSecret 等）做兜底掩码，
 * 但 {@link AdminService} 的 DTO 转换路径已主动脱敏，二者互为冗余。
 *
 * <p>Covers: R21.1, R21.2, R21.3, R21.4, R21.5, R21.6, R23.3。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequirePermission(role = Role.SYSTEM_ADMIN)
@Tag(name = "Admin", description = "系统管理：模型 / 扫描器 / 系统参数（M10 / R21）")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ---------------------------------------------------------------------
    // 模型管理
    // ---------------------------------------------------------------------

    @Operation(summary = "列出 AI 模型配置",
            description = "返回全部模型，apiKey 始终掩码为 '****'（R21.2 / R23.3）。")
    @GetMapping("/model-configs")
    public ApiResponse<List<ModelConfigDTO>> listModels() {
        return ApiResponse.success(adminService.listModels());
    }

    @Operation(summary = "创建 AI 模型配置",
            description = "apiKey 经 AES-GCM 加密后落库（R23.2）；响应中 apiKey 掩码。")
    @PostMapping("/model-configs")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ModelConfigDTO> createModel(@Valid @RequestBody ModelConfigCreateRequest request) {
        return ApiResponse.success(adminService.createModel(request));
    }

    @Operation(summary = "更新 AI 模型配置",
            description = "PATCH 语义：仅更新非 null 字段；apiKey 提供时重新加密。")
    @PatchMapping("/model-configs/{id}")
    public ApiResponse<ModelConfigDTO> updateModel(@PathVariable("id") Long id,
                                                   @Valid @RequestBody ModelConfigUpdateRequest request) {
        return ApiResponse.success(adminService.updateModel(id, request));
    }

    // ---------------------------------------------------------------------
    // 扫描器管理
    // ---------------------------------------------------------------------

    @Operation(summary = "列出静态扫描器配置")
    @GetMapping("/scanners")
    public ApiResponse<List<ScannerConfigDTO>> listScanners() {
        return ApiResponse.success(adminService.listScanners());
    }

    @Operation(summary = "Upsert 静态扫描器配置",
            description = "以 name 为业务键 upsert：存在则更新，不存在则插入（R21.3）。")
    @PostMapping("/scanners")
    public ApiResponse<ScannerConfigDTO> upsertScanner(@Valid @RequestBody ScannerConfigRequest request) {
        return ApiResponse.success(adminService.upsertScanner(request));
    }

    // ---------------------------------------------------------------------
    // 系统参数管理
    // ---------------------------------------------------------------------

    @Operation(summary = "列出系统参数",
            description = "可选 prefix 前缀过滤（不区分大小写）；敏感参数 paramValue 已掩码。")
    @GetMapping("/system-params")
    public ApiResponse<List<SystemParamDTO>> listParams(
            @RequestParam(name = "prefix", required = false) String prefix) {
        return ApiResponse.success(adminService.listParams(prefix));
    }

    @Operation(summary = "更新系统参数",
            description = "已知 key 进行类型 / 范围校验；越界返回 VALIDATION_ERROR。"
                    + "敏感参数加密落库；同步通过 Redis pub/sub 通知 worker 热更新（R24.3）。")
    @PatchMapping("/system-params/{key}")
    public ApiResponse<SystemParamDTO> updateParam(@PathVariable("key") String key,
                                                   @Valid @RequestBody SystemParamUpdateRequest request) {
        SystemParamDTO dto = adminService.updateParam(key, request.value());
        if (dto == null) {
            // updateParam 现实现保证不会返回 null；这里防御性兜底
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "参数不存在: " + key);
        }
        return ApiResponse.success(dto);
    }
}
