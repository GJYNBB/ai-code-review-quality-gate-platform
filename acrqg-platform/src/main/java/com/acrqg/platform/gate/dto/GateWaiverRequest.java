package com.acrqg.platform.gate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交门禁豁免申请的请求载荷（design.md §8.4 / R15.1 / R15.2）。
 *
 * <p>由 {@code POST /api/v1/review-tasks/{taskId}/waivers} 接收。
 *
 * <p>字段约束：
 * <ul>
 *   <li>{@code reason} —— 必填；至少 10 字符（R15.2），最长 500 字符以保证 UI
 *       友好与日志可读性。</li>
 * </ul>
 *
 * <p>本批次（B4-E）未引入 {@code expireAt} 字段——豁免审批通过后任务直接转
 * PASSED，没有"过期失效"语义；如需该能力可在后续迁移中扩展。
 *
 * <p>Covers: R15.1, R15.2。
 *
 * @param reason 申请原因，10~500 字符
 */
@Schema(description = "提交门禁豁免申请请求体")
public record GateWaiverRequest(

        @NotBlank(message = "reason 不能为空")
        @Size(min = 10, max = 500, message = "reason 长度必须在 10~500 字符之间")
        @Schema(description = "申请原因（10~500 字符）", example = "本次失败由 flaky 测试导致，已修复，临时豁免以解锁发布。")
        String reason
) {
}
