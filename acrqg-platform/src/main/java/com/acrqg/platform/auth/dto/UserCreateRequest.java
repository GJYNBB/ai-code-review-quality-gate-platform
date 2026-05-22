package com.acrqg.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 创建用户请求体。
 *
 * <p>对应 {@code POST /api/v1/users}（仅 SYSTEM_ADMIN 可调用，R3.4）。Service 层
 * 会通过 BCrypt 哈希 {@link #password()} 后写入 {@code "user".password_hash}；
 * username / email 唯一性由数据库唯一约束 + {@code DuplicateKeyException} 捕获保证（R3.4）。
 *
 * <p>{@code roles} 至少需要包含一个角色编码（{@code DEVELOPER} / {@code REVIEWER} /
 * {@code PROJECT_ADMIN} / {@code SYSTEM_ADMIN} / {@code CI_CD}）；Service 层会
 * 通过 {@code RoleMapper} 反查角色 id 并写入 {@code user_role} 关联表。
 *
 * <p>Covers: R3.4。
 *
 * @param username 用户名（3..64）
 * @param email    邮箱（合法 RFC 5322，最长 128）
 * @param password 明文密码（8..128，由 Service 层 BCrypt 哈希）
 * @param roles    要分配的全局角色编码列表（非空）
 */
@Schema(description = "创建用户请求体（R3.4）")
public record UserCreateRequest(

        @Schema(description = "用户名", minLength = 3, maxLength = 64)
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 64, message = "用户名长度需在 3..64 之间")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$",
                message = "用户名仅允许字母、数字、下划线、连字符、点号")
        String username,

        @Schema(description = "邮箱", maxLength = 128)
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不合法")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        String email,

        @Schema(description = "明文密码", minLength = 8, maxLength = 128)
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 128, message = "密码长度需在 8..128 之间")
        String password,

        @Schema(description = "角色编码列表，至少一个")
        @NotEmpty(message = "至少需要分配一个角色")
        List<@Pattern(regexp = "DEVELOPER|REVIEWER|PROJECT_ADMIN|SYSTEM_ADMIN|CI_CD",
                message = "角色编码必须为 DEVELOPER / REVIEWER / PROJECT_ADMIN / SYSTEM_ADMIN / CI_CD 之一") String> roles
) {
}
