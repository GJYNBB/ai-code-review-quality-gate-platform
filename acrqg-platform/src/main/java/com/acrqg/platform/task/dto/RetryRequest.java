package com.acrqg.platform.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重试任务请求（design.md §8.4 / R9.4）。
 *
 * <p>对应 {@code POST /api/v1/review-tasks/{id}/retry}。
 *
 * <p>{@code reason} 必填且长度 ≥ 2：审计日志中作为 {@code TASK_RETRIED} 事件的
 * 关键 detail，便于追溯重试动机；前端表单层面给出"原因"输入框（design.md §6.3）。
 *
 * <p>Covers: R9.4, R22.1。
 *
 * @param reason 重试原因
 */
@Schema(description = "评审任务重试请求")
public record RetryRequest(

        @Schema(description = "重试原因", example = "AI 服务恢复后再试一次",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "reason 不能为空")
        @Size(min = 2, max = 512, message = "reason 长度需在 2..512 之间")
        String reason
) {
}
