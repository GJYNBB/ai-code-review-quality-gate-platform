package com.acrqg.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 刷新 access token 后的响应体（design.md §8.4）。
 *
 * <p>对应 {@code POST /api/v1/auth/refresh}。返回新的 accessToken 与剩余有效期；
 * refreshToken 通过"令牌轮换"策略可能也会更新，因此一并返回。
 *
 * <p>Covers: R1.5。
 *
 * @param accessToken  新签发的访问令牌
 * @param refreshToken 旋转后的刷新令牌
 * @param expiresIn    accessToken 有效期（秒）
 */
@Schema(description = "刷新令牌结果（R1.5）")
public record RefreshResultDTO(
        @Schema(description = "新签发的访问令牌") String accessToken,
        @JsonIgnore
        @Schema(description = "旋转后的刷新令牌通过 HttpOnly Cookie 返回，不序列化到 JSON 响应体") String refreshToken,
        @Schema(description = "accessToken 有效期（秒）") long expiresIn
) {
}
