package com.acrqg.platform.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 系统参数更新请求体。
 *
 * <p>对应 {@code PATCH /api/v1/admin/system-params/{key}}。{@code key} 由 path
 * 携带，请求体只承载 {@code value}。
 *
 * <p>{@link #value} 长度上限与 DB 列 {@code param_value VARCHAR(1024)} 一致；
 * 类型校验（如 {@code review.worker.concurrency} 必须是 1..32 的整数）由
 * {@code AdminService.updateParam} 在 Service 层完成，越界时抛
 * {@code BusinessException(VALIDATION_ERROR, "value out of range")}。
 *
 * <p>Covers: R21.4, R21.5, R24.3。
 *
 * @param value 新值（敏感参数也以明文传入，由 Service 端加密落库）
 */
public record SystemParamUpdateRequest(
        @NotNull
        @Size(max = 1024, message = "value 长度不能超过 1024")
        String value
) {
}
