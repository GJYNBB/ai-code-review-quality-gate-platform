package com.acrqg.platform.project.dto;

import java.time.OffsetDateTime;

/**
 * 项目对外视图（design.md §8.4）。
 *
 * <p>由 {@code ProjectService} 在 {@code create} / {@code get} / {@code update} /
 * {@code page} 接口中返回；通过 {@code ApiResponse<ProjectDTO>} 或
 * {@code ApiResponse<PageResult<ProjectDTO>>} 包装。
 *
 * <p>{@code memberCount} 通过 {@code ProjectMemberMapper.countMembers(projectId)}
 * 查询并填充；分页接口为减少 N+1 查询，可在 SQL 中通过子查询一次性返回（参见
 * {@code ProjectMapper.pageWithMemberCount}）。
 *
 * @param id             项目主键
 * @param name           项目名称
 * @param description    项目描述（可能为 {@code null}）
 * @param defaultBranch  默认分支
 * @param language       主要语言
 * @param createdBy      创建者用户主键
 * @param memberCount    项目成员数量
 * @param createdAt      创建时间
 */
public record ProjectDTO(
        Long id,
        String name,
        String description,
        String defaultBranch,
        String language,
        Long createdBy,
        int memberCount,
        OffsetDateTime createdAt
) {
}
