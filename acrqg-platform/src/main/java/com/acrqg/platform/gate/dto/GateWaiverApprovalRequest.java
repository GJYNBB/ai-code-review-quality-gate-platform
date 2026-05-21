package com.acrqg.platform.gate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 审批门禁豁免申请的请求载荷（design.md §8.4 / R15.3 / R15.5）。
 *
 * <p>由 {@code POST /api/v1/waivers/{waiverId}/approval} 接收。
 *
 * <p>字段约束：
 * <ul>
 *   <li>{@code approve} —— 必填；{@code true} 表示批准（GateResult 转 WAIVED +
 *       任务转 PASSED），{@code false} 表示拒绝。</li>
 *   <li>{@code comment} —— 可空；最长 500 字符，用于记录审批意见。</li>
 * </ul>
 *
 * <p>Covers: R15.3, R15.4, R15.5。
 *
 * @param approve 审批结论：{@code true}=批准 / {@code false}=拒绝
 * @param comment 审批意见（可空）
 */
@Schema(description = "审批门禁豁免申请请求体")
public record GateWaiverApprovalRequest(

        @NotNull(message = "approve 不能为空")
        @Schema(description = "true=批准；false=拒绝", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean approve,

        @Size(max = 500, message = "comment 不能超过 500 字符")
        @Schema(description = "审批意见（最长 500 字符）", example = "确认是 flaky 测试，同意豁免。")
        String comment
) {
}
