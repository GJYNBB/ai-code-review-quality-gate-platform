package com.acrqg.platform.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新 AI 模型配置请求体（design.md §8.4）。
 *
 * <p>对应 {@code PATCH /api/v1/admin/model-configs/{id}}。所有字段都可空，
 * Service 层只更新非 {@code null} 的字段（PATCH 语义）。
 *
 * <p>字段约束：
 * <ul>
 *   <li>{@code baseUrl}：可空；非空时必须为 http/https URL，最长 255；</li>
 *   <li>{@code apiKey}：可空；非空时长度 8..512，由 Service 端加密后落库；
 *       传 {@code null} 表示不修改；</li>
 *   <li>{@code timeoutSeconds}：可空；非空时范围 10..300；</li>
 *   <li>{@code enabled}：可空，可作为开关字段独立使用（也可由
 *       {@code AdminService.enableDisableModel} 单独切换）。</li>
 * </ul>
 *
 * <p>{@code name} 不允许在更新中修改：作为唯一约束的业务键，重命名带来的影响范围
 * 较大（R21 不要求支持），强制走"删除 + 新建"路径。
 *
 * <p>Covers: R21.2, R21.5。
 *
 * @param baseUrl         基础 URL（可空）
 * @param apiKey          明文 API Key（可空，非空时落库前加密）
 * @param timeoutSeconds  超时秒数（可空，10..300）
 * @param enabled         是否启用（可空）
 */
public record ModelConfigUpdateRequest(
        @Size(max = 255)
        @Pattern(regexp = "^https?://.+", message = "baseUrl 必须是 http(s) URL")
        String baseUrl,
        @Size(min = 8, max = 512) String apiKey,
        @Min(value = 10, message = "timeoutSeconds 必须 >= 10")
        @Max(value = 300, message = "timeoutSeconds 不能超过 300")
        Integer timeoutSeconds,
        Boolean enabled
) {
}
