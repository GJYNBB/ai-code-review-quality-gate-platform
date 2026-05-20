package com.acrqg.platform.auth.dto;

import com.acrqg.platform.auth.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 用户状态切换请求体（design.md §8.4）。
 *
 * <p>对应 {@code PATCH /api/v1/users/{id}/status}（仅 SYSTEM_ADMIN，R3.2）。
 * 当切换为 {@link UserStatus#DISABLED} 时，Service 层会立即把该用户已知的所有 jti
 * 加入黑名单 + 删除该用户的 refreshToken 记录，保证 5 分钟内必失效（R3.2）。
 *
 * <p>Covers: R3.2。
 *
 * @param status 目标状态（{@code ENABLED} / {@code DISABLED}）
 */
@Schema(description = "用户状态切换请求体（R3.2）")
public record UserStatusChangeRequest(
        @Schema(description = "目标状态：ENABLED / DISABLED")
        @NotNull(message = "status 不能为空") UserStatus status
) {
}
