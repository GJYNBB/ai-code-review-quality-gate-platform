package com.acrqg.platform.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 评审任务创建请求（design.md §8.4）。
 *
 * <p>对应 {@code POST /api/v1/review-tasks}（手动触发，R8）。
 *
 * <p>{@code commitSha} 与 {@code prId} 至少填写一项（R8.2）；通过
 * {@link AssertTrue} 校验，错误时返回 {@code VALIDATION_ERROR} +
 * {@code details=[{field:"hasCommitOrPr"}]}。
 *
 * <p>本 DTO <b>不</b> 包含 {@code triggerType} 字段：
 * <ul>
 *   <li>从 Webhook 进来的请求由 {@code WebhookService} 调用 Service 时传入
 *       {@code TriggerType.WEBHOOK}；</li>
 *   <li>从 REST 接口进来的请求由 {@code ReviewTaskController} 根据当前用户角色
 *       自动决定（CI_CD 角色→{@code CI_CD}；否则→{@code MANUAL}）。</li>
 * </ul>
 *
 * <p>Covers: R7.3, R8.1, R8.2。
 */
@Schema(description = "评审任务创建请求（R7 / R8）")
public record ReviewTaskCreateRequest(

        @Schema(description = "项目主键", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "projectId 不能为空")
        Long projectId,

        @Schema(description = "源分支", example = "feature/foo", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "sourceBranch 不能为空")
        @Size(max = 255, message = "sourceBranch 不能超过 255 字符")
        String sourceBranch,

        @Schema(description = "目标分支", example = "main", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "targetBranch 不能为空")
        @Size(max = 255, message = "targetBranch 不能超过 255 字符")
        String targetBranch,

        @Schema(description = "触发评审的 commit SHA；与 prId 至少填一项", example = "a1b2c3d4")
        @Size(max = 64, message = "commitSha 不能超过 64 字符")
        String commitSha,

        @Schema(description = "PR/MR 编号；与 commitSha 至少填一项", example = "123")
        @Size(max = 64, message = "prId 不能超过 64 字符")
        String prId
) {

    /** R8.2：commitSha 与 prId 至少其一非空。 */
    @AssertTrue(message = "commitSha 与 prId 至少填写一项")
    @Schema(hidden = true)
    public boolean isHasCommitOrPr() {
        return notBlank(commitSha) || notBlank(prId);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
