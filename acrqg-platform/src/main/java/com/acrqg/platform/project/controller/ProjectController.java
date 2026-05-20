package com.acrqg.platform.project.controller;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.infra.permission.ProjectRole;
import com.acrqg.platform.infra.permission.RequirePermission;
import com.acrqg.platform.infra.permission.Role;
import com.acrqg.platform.project.dto.AddMemberRequest;
import com.acrqg.platform.project.dto.ProjectCreateRequest;
import com.acrqg.platform.project.dto.ProjectDTO;
import com.acrqg.platform.project.dto.ProjectMemberDTO;
import com.acrqg.platform.project.dto.ProjectQuery;
import com.acrqg.platform.project.dto.ProjectUpdateRequest;
import com.acrqg.platform.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目（M02）控制器。
 *
 * <p>对齐 design.md §8.7 的接口清单：
 * <pre>
 * POST   /api/v1/projects                            @RequirePermission(role={PROJECT_ADMIN,SYSTEM_ADMIN})
 * GET    /api/v1/projects                            已登录
 * GET    /api/v1/projects/{id}                       @RequirePermission(projectMember=true)
 * PUT    /api/v1/projects/{id}                       @RequirePermission(projectMember=true, projectRole=PROJECT_ADMIN)
 * POST   /api/v1/projects/{id}/members               @RequirePermission(projectMember=true, projectRole=PROJECT_ADMIN)
 * DELETE /api/v1/projects/{id}/members/{userId}      @RequirePermission(projectMember=true, projectRole=PROJECT_ADMIN)
 * GET    /api/v1/projects/{id}/members               @RequirePermission(projectMember=true)
 * </pre>
 *
 * <p>所有接口的请求体 / 查询参数 / 路径变量均通过 {@code @Valid} 触发 Bean Validation；
 * 校验失败由 {@code GlobalExceptionHandler} 映射为 {@code VALIDATION_ERROR}（HTTP 400）。
 *
 * <p>"已登录"语义靠 {@code JwtAuthFilter} 强制：未携带合法 token 的请求会在过滤器
 * 阶段被拒（HTTP 401 / {@code AUTH_INVALID_TOKEN}），无需在控制器层重复声明。
 *
 * <p>Covers: R4, R6, R23.1。
 */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Project", description = "项目与成员（M02 / R4 / R6）")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    // ---------------------------------------------------------------------
    // 项目主资源
    // ---------------------------------------------------------------------

    @Operation(summary = "创建项目",
            description = "PROJECT_ADMIN 或 SYSTEM_ADMIN 可调用；创建者自动成为 PROJECT_ADMIN（R4.1, R4.2, R6.1）。")
    @RequirePermission(role = {Role.PROJECT_ADMIN, Role.SYSTEM_ADMIN})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectDTO> create(@Valid @RequestBody ProjectCreateRequest request) {
        ProjectDTO dto = projectService.create(request);
        return ApiResponse.success(dto);
    }

    @Operation(summary = "项目分页查询",
            description = "已登录用户可调用，支持关键字模糊匹配（name + description）（R4.3）。")
    @GetMapping
    public ApiResponse<PageResult<ProjectDTO>> page(@Valid @ModelAttribute ProjectQuery query) {
        return ApiResponse.success(projectService.page(query));
    }

    @Operation(summary = "项目详情",
            description = "仅项目成员可访问（R6.4）。")
    @RequirePermission(projectMember = true, projectIdParam = "id")
    @GetMapping("/{id}")
    public ApiResponse<ProjectDTO> get(@PathVariable("id") Long id) {
        return ApiResponse.success(projectService.get(id));
    }

    @Operation(summary = "更新项目",
            description = "仅项目内 PROJECT_ADMIN 可调用（R4.4）。null 字段不覆盖。")
    @RequirePermission(projectMember = true, projectIdParam = "id",
            projectRole = ProjectRole.PROJECT_ADMIN)
    @PutMapping("/{id}")
    public ApiResponse<ProjectDTO> update(@PathVariable("id") Long id,
                                          @Valid @RequestBody ProjectUpdateRequest request) {
        return ApiResponse.success(projectService.update(id, request));
    }

    // ---------------------------------------------------------------------
    // 项目成员管理
    // ---------------------------------------------------------------------

    @Operation(summary = "列出项目成员",
            description = "仅项目成员可调用。")
    @RequirePermission(projectMember = true, projectIdParam = "id")
    @GetMapping("/{id}/members")
    public ApiResponse<List<ProjectMemberDTO>> listMembers(@PathVariable("id") Long id) {
        return ApiResponse.success(projectService.listMembers(id));
    }

    @Operation(summary = "添加项目成员",
            description = "仅项目内 PROJECT_ADMIN 可调用（R6.1, R6.2）。")
    @RequirePermission(projectMember = true, projectIdParam = "id",
            projectRole = ProjectRole.PROJECT_ADMIN)
    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> addMember(@PathVariable("id") Long id,
                                       @Valid @RequestBody AddMemberRequest request) {
        projectService.addMember(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "移除项目成员",
            description = "仅项目内 PROJECT_ADMIN 可调用（R6.3）。仅删除关联，不删全局用户。")
    @RequirePermission(projectMember = true, projectIdParam = "id",
            projectRole = ProjectRole.PROJECT_ADMIN)
    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable("id") Long id,
                                          @PathVariable("userId") Long userId) {
        projectService.removeMember(id, userId);
        return ApiResponse.success(null);
    }
}
