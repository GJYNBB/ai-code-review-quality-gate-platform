package com.acrqg.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌请求体。
 *
 * <p>对应 {@code POST /api/v1/auth/refresh}。
 *
 * <p>{@code refreshToken} 通过请求体传递（而非 URL / cookie），原因：
 * <ul>
 *   <li>避免 URL 中的敏感令牌被反向代理日志捕获；</li>
 *   <li>当前接口设计无 cookie session（SecurityConfig 已禁用），
 *       请求体更直接；</li>
 *   <li>前端实现简单：直接 {@code POST} 一段 JSON 即可。</li>
 * </ul>
 *
 * <p>Covers: R1.5。
 *
 * @param refreshToken 刷新令牌（HS256 JWT 字符串，{@code tokenType=REFRESH}）
 */
@Schema(description = "刷新令牌请求体（R1.5）")
public record RefreshRequest(
        @Schema(description = "刷新令牌")
        @NotBlank(message = "refreshToken 不能为空") String refreshToken
) {
}
