package com.acrqg.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求体（design.md §8.4）。
 *
 * <p>对应 {@code POST /api/v1/auth/login}。Bean Validation 通过
 * Controller 上的 {@code @Valid} 触发，校验失败由
 * {@link com.acrqg.platform.common.exception.GlobalExceptionHandler}
 * 映射为 {@code VALIDATION_ERROR} + 400。
 *
 * <p>{@code password} 字段是敏感数据：
 * <ul>
 *   <li>响应中绝不会回显（{@link com.acrqg.platform.infra.log.ResponseMaskingAspect}
 *       会扫描 {@link com.acrqg.platform.common.util.MaskUtils#SENSITIVE_KEYS} 兜底）；</li>
 *   <li>日志层 {@code MaskingLogbackEncoder} 也会把 message 中匹配 password
 *       关键字的子串替换为 {@code "****"}（R23.3）。</li>
 * </ul>
 *
 * @param username 用户名（3..64）
 * @param password 明文密码（8..128，仅在登录请求中存在）
 */
@Schema(description = "登录请求体（R1.1）")
public record LoginRequest(

        @Schema(description = "用户名", example = "admin", minLength = 3, maxLength = 64)
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 64, message = "用户名长度需在 3..64 之间")
        String username,

        @Schema(description = "明文密码", example = "Admin@123", minLength = 8, maxLength = 128)
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 128, message = "密码长度需在 8..128 之间")
        String password
) {
}
