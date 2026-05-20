package com.acrqg.platform.project.repository;

import java.time.OffsetDateTime;

/**
 * 项目成员列表行视图（包含 {@code username} 快照）。
 *
 * <p>用于 {@link ProjectMemberMapper#listMembers} 的结果映射。Service 层
 * 负责把它转换为 {@link com.acrqg.platform.project.dto.ProjectMemberDTO}。
 */
public class ProjectMemberRow {

    private Long userId;
    private String username;
    private String projectRole;
    private OffsetDateTime joinedAt;

    public ProjectMemberRow() {
        // for MyBatis
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getProjectRole() {
        return projectRole;
    }

    public void setProjectRole(String projectRole) {
        this.projectRole = projectRole;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
