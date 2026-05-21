package com.acrqg.platform.code_issue.controller;

import com.acrqg.platform.code_issue.dto.CodeIssueDTO;
import com.acrqg.platform.code_issue.dto.IssueQuery;
import com.acrqg.platform.code_issue.dto.IssueStatusChangeRequest;
import com.acrqg.platform.code_issue.service.IssueService;
import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问题（M08 / R16.2-3, R17）控制器（design.md §8.4 / §8.7）。
 *
 * <p>路由：
 * <pre>
 * GET    /api/v1/review-tasks/{taskId}/issues       已登录（service 内按项目成员过滤）
 * GET    /api/v1/issues/{id}                        已登录（service 内按项目成员过滤）
 * PATCH  /api/v1/issues/{id}/status                 已登录（service 内按项目成员 + R17.5）
 * POST   /api/v1/issues/{id}/comments               已登录（service 内按项目成员过滤）
 * </pre>
 *
 * <p>权限说明：projectId 必须经由 {@code issue → review_task} 实时解析，
 * 因此控制器不使用 {@code @RequirePermission(projectMember=true)}（注解切面无法
 * 自动从 path variable {@code id} 推导 projectId），而是在 {@link IssueService}
 * 内部显式调用 {@code PermissionEvaluator.isProjectMember}——与
 * {@link com.acrqg.platform.task.controller.ReviewTaskController#get} 模式保持一致。
 *
 * <p>"已登录"语义靠 {@code JwtAuthFilter} 强制；未登录请求会先被 401 拦下。
 *
 * <p>Covers: R16.2, R16.3, R17.1, R17.2, R17.3, R17.4, R17.5。
 */
@RestController
@Tag(name = "Issue", description = "问题生命周期（M08 / R16.2-3, R17）")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @Operation(summary = "按评审任务分页查询问题列表",
            description = "已登录用户可调用，service 内按项目成员关系过滤（R2.2）。"
                    + " 支持 severity[] / status[] / source / filePath / keyword 多维过滤；"
                    + " 排序 severity 升序 rank（CRITICAL 最高）+ createdAt 升序（R16.2）。")
    @GetMapping("/api/v1/review-tasks/{taskId}/issues")
    public ApiResponse<PageResult<CodeIssueDTO>> page(@PathVariable("taskId") Long taskId,
                                                      @Valid @ModelAttribute IssueQuery query) {
        return ApiResponse.success(issueService.page(taskId, query));
    }

    @Operation(summary = "查询问题详情（含历史与评论）",
            description = "仅当任务所属项目的成员可访问，否则返回 PERMISSION_DENIED。"
                    + " 返回的 history / comments 字段非空（即使无记录也为空数组）。")
    @GetMapping("/api/v1/issues/{id}")
    public ApiResponse<CodeIssueDTO> get(@PathVariable("id") Long id) {
        return ApiResponse.success(issueService.get(id));
    }

    @Operation(summary = "变更问题状态",
            description = "项目成员可调用；DEVELOPER 全局角色仅能操作自己创建的任务下的问题（R17.5）；"
                    + " 状态迁移合法集见 R17.1；FALSE_POSITIVE / CLOSED 时 comment 长度需 ≥ 5（R17.3）。"
                    + " 变更成功后写 issue_history 并发布 ISSUE_STATUS_CHANGED 审计事件（R17.4）。")
    @PatchMapping("/api/v1/issues/{id}/status")
    public ApiResponse<Void> changeStatus(@PathVariable("id") Long id,
                                          @Valid @RequestBody IssueStatusChangeRequest request) {
        issueService.changeStatus(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "追加问题评论",
            description = "项目成员可调用；评论内容长度 1..1000 字符（R16.3）。"
                    + " 写入后发布 ISSUE_COMMENT_ADDED 审计事件。")
    @PostMapping("/api/v1/issues/{id}/comments")
    public ApiResponse<Void> addComment(@PathVariable("id") Long id,
                                        @Valid @RequestBody CommentCreateRequest request) {
        issueService.addComment(id, request.content());
        return ApiResponse.success(null);
    }

    /**
     * 评论创建请求体。
     *
     * <p>独立 record 而非内联 {@code String}：让 Bean Validation 在 controller 层
     * 直接拦截空 / 超长内容，避免任何调用都进入 service。
     *
     * @param content 评论内容；非空 + 长度 ≤ 1000
     */
    public record CommentCreateRequest(
            @NotBlank @Size(max = 1000) String content
    ) {
    }
}
