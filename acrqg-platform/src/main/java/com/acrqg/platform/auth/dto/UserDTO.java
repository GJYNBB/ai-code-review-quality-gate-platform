package com.acrqg.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 用户对外视图（design.md §8.4）。
 *
 * <p>用于 {@code POST /api/v1/auth/login}（嵌入 {@link LoginResultDTO}）、
 * {@code GET /api/v1/auth/me}、{@code GET /api/v1/users}、
 * {@code POST /api/v1/users} 等接口的返回值。
 *
 * <p>关键设计：
 * <ul>
 *   <li>不含 {@code passwordHash}：即使该字段被未来扩展为 entity 直接序列化，
 *       {@link com.acrqg.platform.infra.log.ResponseMaskingAspect} 也会兜底掩码；</li>
 *   <li>{@link #status} 直接以字符串形式返回（{@code ENABLED} / {@code DISABLED}），
 *       与数据库 CHECK 约束保持一致；</li>
 *   <li>{@link #roles} 为全局角色编码列表，与 JWT {@code roles} claim 同义。</li>
 * </ul>
 *
 * <p>Covers: R1.1, R1.2, R3.1, R3.4。
 *
 * @param id        主键
 * @param username  用户名
 * @param email     邮箱
 * @param status    状态（{@code ENABLED} / {@code DISABLED}）
 * @param roles     全局角色编码列表
 * @param createdAt 创建时间
 */
@Schema(description = "用户视图（R1.1 / R3.1）")
public record UserDTO(
        @Schema(description = "用户主键") Long id,
        @Schema(description = "用户名") String username,
        @Schema(description = "邮箱") String email,
        @Schema(description = "状态：ENABLED / DISABLED") String status,
        @Schema(description = "全局角色编码列表") List<String> roles,
        @Schema(description = "创建时间") OffsetDateTime createdAt
) {
}
