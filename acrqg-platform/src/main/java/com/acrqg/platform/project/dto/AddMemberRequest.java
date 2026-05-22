package com.acrqg.platform.project.dto;

import com.acrqg.platform.infra.permission.ProjectRole;
import jakarta.validation.constraints.NotNull;

/**
 * 添加项目成员请求体（design.md §8.4）。
 *
 * <p>对应 {@code POST /api/v1/projects/{id}/members}。
 *
 * @param userId  待添加的用户主键
 * @param role    项目内角色（DEVELOPER / REVIEWER / PROJECT_ADMIN）
 */
public record AddMemberRequest(
        @NotNull(message = "userId 不能为空") Long userId,
        @NotNull(message = "role 不能为空") ProjectRole role
) {
}
