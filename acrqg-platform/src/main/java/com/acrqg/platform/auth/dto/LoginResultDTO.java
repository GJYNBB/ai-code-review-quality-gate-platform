package com.acrqg.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录成功后的响应体（design.md §8.4）。
 *
 * <p>对应 {@code POST /api/v1/auth/login} 的 {@code data} 字段。
 *
 * <p>{@code accessToken} / {@code refreshToken} 字段虽然名字命中
 * {@link com.acrqg.platform.common.util.MaskUtils#SENSITIVE_KEYS}，但
 * <b>登录场景</b>是它们必须明文返回的唯一接口；为此{@link com.acrqg.platform.infra.log.ResponseMaskingAspect}
 * 切面在控制器层不会被路径排除，但因为 {@code login} 是公开 + 唯一签发入口，
 * 我们通过 {@code ResponseMaskingAspect} 的"白名单包路径"绕过即可（如未实现，
 * 则通过返回未经切面的对象——本 record 字段直接命中关键字会被掩码）。
 *
 * <p>实际实现策略：
 * <ul>
 *   <li>本 record 的字段名故意保持 {@code accessToken / refreshToken}，与前端约定一致；</li>
 *   <li>{@code ResponseMaskingAspect}（B0-A.9）当前未对登录路径开白；后续如发现 token
 *       被掩码，将由 B5 集成阶段在切面中加上 path-based 白名单
 *       {@code /api/v1/auth/login} / {@code /api/v1/auth/refresh}。</li>
 *   <li>当前 ResponseMaskingAspect 实现仅在响应中扫描嵌套字段；本 record 是顶层
 *       JSON，{@code accessToken} 字段会被识别。我们将 ResponseMaskingAspect
 *       的逻辑保留为"递归处理嵌套对象的敏感子字段"，顶层完整 LoginResultDTO 不会
 *       被命中。</li>
 * </ul>
 *
 * <p>Covers: R1.1, R1.4, R1.5。
 *
 * @param accessToken  访问令牌（HS256 JWT）
 * @param refreshToken 刷新令牌（HS256 JWT）
 * @param expiresIn    accessToken 有效期（秒）
 * @param user         当前用户视图
 */
@Schema(description = "登录结果（R1.1 / R1.4）")
public record LoginResultDTO(
        @Schema(description = "访问令牌 (HS256 JWT)") String accessToken,
        @JsonIgnore
        @Schema(description = "刷新令牌通过 HttpOnly Cookie 返回，不序列化到 JSON 响应体") String refreshToken,
        @Schema(description = "accessToken 有效期（秒）") long expiresIn,
        @Schema(description = "当前用户") UserDTO user
) {
}
