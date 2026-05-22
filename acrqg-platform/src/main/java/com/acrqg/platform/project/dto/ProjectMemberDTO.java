package com.acrqg.platform.project.dto;

import java.time.OffsetDateTime;

/**
 * 项目成员对外视图（B1-C.4）。
 *
 * <p>对应 {@code GET /api/v1/projects/{id}/members} 返回的元素。包含
 * {@code userId / username / role / joinedAt}，{@code username} 由 Service 层
 * 通过 join {@code "user"} 表填充，避免前端再发一次用户查询。
 *
 * <p>Covers: R6.1, R6.4。
 *
 * @param userId    成员用户主键
 * @param username  成员用户名快照
 * @param role      项目内角色（DEVELOPER / REVIEWER / PROJECT_ADMIN）
 * @param joinedAt  加入时间
 */
public record ProjectMemberDTO(
        Long userId,
        String username,
        String role,
        OffsetDateTime joinedAt
) {
}
