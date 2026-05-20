package com.acrqg.platform.auth.controller;

import com.acrqg.platform.auth.dto.UserCreateRequest;
import com.acrqg.platform.auth.dto.UserDTO;
import com.acrqg.platform.auth.dto.UserQuery;
import com.acrqg.platform.auth.dto.UserStatusChangeRequest;
import com.acrqg.platform.auth.service.UserService;
import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.infra.permission.RequirePermission;
import com.acrqg.platform.infra.permission.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理控制器（仅 SYSTEM_ADMIN）。
 *
 * <p>路由清单（design.md §8.7）：
 * <pre>
 * GET   /api/v1/users                @RequirePermission(role=SYSTEM_ADMIN)
 * POST  /api/v1/users                @RequirePermission(role=SYSTEM_ADMIN)
 * PATCH /api/v1/users/{id}/status    @RequirePermission(role=SYSTEM_ADMIN)
 * </pre>
 *
 * <p>非 SYSTEM_ADMIN 调用由 {@link com.acrqg.platform.infra.permission.PermissionAspect}
 * 拒绝并返回 {@link com.acrqg.platform.common.api.ErrorCode#PERMISSION_DENIED}（HTTP 403）。
 *
 * <p>POST 接口返回 HTTP 201（@ResponseStatus(CREATED)），符合 RESTful 资源创建语义。
 *
 * <p>Covers: R3.1, R3.2, R3.3, R3.4, R23.1。
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "用户管理（M01 / R3）")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户分页查询",
            description = "仅 SYSTEM_ADMIN 可调用。支持 keyword 模糊匹配 username/email、"
                    + "按 status / role 精确过滤。")
    @RequirePermission(role = Role.SYSTEM_ADMIN)
    @GetMapping
    public ApiResponse<PageResult<UserDTO>> page(@Valid @ModelAttribute UserQuery query) {
        return ApiResponse.success(userService.page(query));
    }

    @Operation(summary = "创建用户",
            description = "仅 SYSTEM_ADMIN 可调用。username / email 唯一；"
                    + "password BCrypt 哈希存入；至少分配一个全局角色。")
    @RequirePermission(role = Role.SYSTEM_ADMIN)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDTO> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.success(userService.create(request));
    }

    @Operation(summary = "切换用户状态",
            description = "仅 SYSTEM_ADMIN 可调用。切换为 DISABLED 时立即让该用户的"
                    + "所有 access / refresh token 在 5 分钟内失效（R3.2）。")
    @RequirePermission(role = Role.SYSTEM_ADMIN)
    @PatchMapping("/{id}/status")
    public ApiResponse<UserDTO> changeStatus(@PathVariable("id") Long id,
                                             @Valid @RequestBody UserStatusChangeRequest request) {
        return ApiResponse.success(userService.changeStatus(id, request.status()));
    }
}
