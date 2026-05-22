package com.acrqg.platform.repository.controller;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.infra.permission.ProjectRole;
import com.acrqg.platform.infra.permission.RequirePermission;
import com.acrqg.platform.repository.dto.ConnectivityResultDTO;
import com.acrqg.platform.repository.dto.RepositoryBindRequest;
import com.acrqg.platform.repository.dto.RepositoryBindingDTO;
import com.acrqg.platform.repository.dto.RepositoryTestRequest;
import com.acrqg.platform.repository.service.RepositoryService;
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
 * 仓库绑定控制器（M02 / R5）。
 *
 * <p>对齐 design.md §8.4 与 §8.7：
 * <pre>
 * POST   /api/v1/projects/{id}/repository/test    @RequirePermission(projectMember=true, projectRole=PROJECT_ADMIN)
 * POST   /api/v1/projects/{id}/repository         @RequirePermission(projectMember=true, projectRole=PROJECT_ADMIN)
 * GET    /api/v1/projects/{id}/repository         @RequirePermission(projectMember=true)
 * </pre>
 *
 * <p>"已登录"语义靠 {@code JwtAuthFilter} 强制：未携带合法 token 的请求会在过滤器
 * 阶段被拒（HTTP 401 / {@code AUTH_INVALID_TOKEN}），无需在控制器层重复声明。
 *
 * <p>越权用例（design.md §15.6）：
 * <ul>
 *   <li>非项目成员请求任一接口 → 403 / {@code PERMISSION_DENIED}；</li>
 *   <li>REVIEWER 项目成员调用 {@code POST /repository} → 403（projectRole 不匹配）；</li>
 *   <li>DEVELOPER 项目成员调用 {@code POST /repository/test} → 403。</li>
 * </ul>
 *
 * <p>响应体由 {@link RepositoryService} 返回的 {@link RepositoryBindingDTO} 保证
 * <b>不包含</b> {@code accessToken / webhookSecret / *_encrypted} 字段（R5.4 / R23.3）。
 *
 * <p>Covers: R5, R23.1。
 */
@RestController
@RequestMapping("/api/v1/projects/{id}/repository")
@Tag(name = "Repository", description = "仓库绑定（M02 / R5）")
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Operation(summary = "测试仓库连通性",
            description = "仅项目内 PROJECT_ADMIN 可调用；不持久化（R5.1）。"
                    + " 不可达时返回 reachable=false + message（不抛异常）。")
    @RequirePermission(projectMember = true, projectIdParam = "id",
            projectRole = ProjectRole.PROJECT_ADMIN)
    @PostMapping("/test")
    public ApiResponse<ConnectivityResultDTO> test(@PathVariable("id") Long projectId,
                                                   @Valid @RequestBody RepositoryTestRequest request) {
        return ApiResponse.success(repositoryService.test(projectId, request));
    }

    @Operation(summary = "绑定（或更新）仓库",
            description = "仅项目内 PROJECT_ADMIN 可调用。先 ping 通才会写库；"
                    + " ping 失败抛 REPOSITORY_UNREACHABLE（R5.2）。响应 DTO 不含明文 token。")
    @RequirePermission(projectMember = true, projectIdParam = "id",
            projectRole = ProjectRole.PROJECT_ADMIN)
    @PostMapping
    public ApiResponse<RepositoryBindingDTO> bind(@PathVariable("id") Long projectId,
                                                  @Valid @RequestBody RepositoryBindRequest request) {
        return ApiResponse.success(repositoryService.bind(projectId, request));
    }

    @Operation(summary = "查询当前仓库绑定",
            description = "任意项目成员可访问（R6.4）。响应 DTO 不含明文 token / secret（R5.4）。")
    @RequirePermission(projectMember = true, projectIdParam = "id")
    @GetMapping
    public ApiResponse<RepositoryBindingDTO> get(@PathVariable("id") Long projectId) {
        return ApiResponse.success(repositoryService.get(projectId));
    }
}
