package com.acrqg.platform.admin.dto;

import java.time.OffsetDateTime;

/**
 * AI 模型配置对外视图（design.md §8.4）。
 *
 * <p>由 {@code AdminService} 在 {@code createModel} / {@code listModels} /
 * {@code getModel} / {@code updateModel} 接口中返回；通过
 * {@code ApiResponse<ModelConfigDTO>} 包装。
 *
 * <p>{@link #apiKeyMasked} 字段始终为字面量 {@code "****"}（R21.2 / R23.3），
 * <b>禁止</b>在任何接口响应中暴露 apiKey 明文或加密密文。
 *
 * <p>Covers: R21.1, R21.2, R23.3。
 *
 * @param id              主键
 * @param name            模型名称
 * @param baseUrl         基础 URL
 * @param apiKeyMasked    始终为 {@code "****"}
 * @param timeoutSeconds  超时秒数
 * @param enabled         是否启用
 * @param createdAt       创建时间
 * @param updatedAt       最近更新时间
 */
public record ModelConfigDTO(
        Long id,
        String name,
        String baseUrl,
        String apiKeyMasked,
        int timeoutSeconds,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
