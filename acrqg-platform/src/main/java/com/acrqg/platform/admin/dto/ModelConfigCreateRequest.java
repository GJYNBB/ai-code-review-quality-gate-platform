package com.acrqg.platform.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建 AI 模型配置请求体（design.md §8.4）。
 *
 * <p>对应 {@code POST /api/v1/admin/model-configs}。Bean Validation 在
 * {@code AdminController} 上通过 {@code @Valid} 触发；不通过时由
 * {@code GlobalExceptionHandler} 映射为 {@code VALIDATION_ERROR} + 400（R21.4）。
 *
 * <p>字段约束：
 * <ul>
 *   <li>{@code name}：1..64，全局唯一（DB {@code uk_model_name} 强制）；</li>
 *   <li>{@code baseUrl}：1..255，使用 {@link Pattern} 校验为 http/https URL；</li>
 *   <li>{@code apiKey}：8..512，<b>明文</b>，由 Service 端经
 *       {@link com.acrqg.platform.infra.crypto.TokenEncryptor#encrypt} 加密后落库；</li>
 *   <li>{@code timeoutSeconds}：10..300，与 DB CHECK 约束一致。</li>
 * </ul>
 *
 * <p>注：{@code @URL} 来自 hibernate-validator 的实现，包名因版本而异；
 * 这里改用通用的 {@link Pattern} 表达式以避免对 hibernate-validator 内部包的依赖。
 *
 * <p>Covers: R21.1, R21.4, R23.2。
 *
 * @param name           模型名称（1..64，唯一）
 * @param baseUrl        基础 URL（http/https）
 * @param apiKey         明文 API Key（8..512），落库前必加密
 * @param timeoutSeconds 超时秒数（10..300）
 */
public record ModelConfigCreateRequest(
        @NotBlank @Size(max = 64) String name,
        @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "^https?://.+", message = "baseUrl 必须是 http(s) URL")
        String baseUrl,
        @NotBlank @Size(min = 8, max = 512) String apiKey,
        @Min(value = 10, message = "timeoutSeconds 必须 >= 10")
        @Max(value = 300, message = "timeoutSeconds 不能超过 300")
        int timeoutSeconds
) {
}
