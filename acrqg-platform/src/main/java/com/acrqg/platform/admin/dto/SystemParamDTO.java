package com.acrqg.platform.admin.dto;

import java.time.OffsetDateTime;

/**
 * 系统参数对外视图。
 *
 * <p>由 {@code AdminService.getParam} / {@code listParams} / {@code updateParam}
 * 返回。当 {@link #sensitive} 为 {@code true} 时，{@link #paramValue} 已被替换为
 * {@code "****"}（R23.3 / R21.5），<b>不会</b>包含明文或密文原值。
 *
 * <p>Covers: R21.4, R21.5, R23.3。
 *
 * @param paramKey    参数键
 * @param paramValue  参数值（敏感参数已脱敏为 {@code "****"}）
 * @param description 描述
 * @param sensitive   是否为敏感参数
 * @param updatedBy   最近更新者用户主键（{@code null} 表示从未更新过）
 * @param updatedAt   最近更新时间
 */
public record SystemParamDTO(
        String paramKey,
        String paramValue,
        String description,
        boolean sensitive,
        Long updatedBy,
        OffsetDateTime updatedAt
) {
}
