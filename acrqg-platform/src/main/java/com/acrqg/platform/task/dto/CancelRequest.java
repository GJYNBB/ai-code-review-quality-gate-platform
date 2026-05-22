package com.acrqg.platform.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 取消任务请求（design.md §8.4 / R9.6）。
 *
 * <p>对应 {@code POST /api/v1/review-tasks/{id}/cancel}。
 *
 * <p>{@code reason} 必填且长度 2..512：会被写入 {@code task_log}（WARN 级别）
 * 与审计事件 {@code TASK_CANCELLED}（R9.6 / R22.1）。
 *
 * <p>Covers: R9.6, R22.1。
 *
 * @param reason 取消原因
 */
@Schema(description = "评审任务取消请求")
public record CancelRequest(

        @Schema(description = "取消原因", example = "误触发，已通过本地验证",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "reason 不能为空")
        @Size(min = 2, max = 512, message = "reason 长度需在 2..512 之间")
        String reason
) {
}
