package com.acrqg.platform.project.repository;

import java.time.OffsetDateTime;

/**
 * Mapper 列表查询行视图，包含 {@code project} 主表字段 +
 * 子查询得到的 {@code memberCount}。
 *
 * <p>专用于 {@link ProjectMapper#pageList} 与
 * {@link ProjectMapper#selectWithMemberCount} 的结果映射，避免污染
 * 领域对象 {@link com.acrqg.platform.project.domain.Project} 增加非持久化字段。
 *
 * <p>Service 层负责把 {@code ProjectListRow} 转换为
 * {@link com.acrqg.platform.project.dto.ProjectDTO}。
 */
public class ProjectListRow {

    private Long id;
    private String name;
    private String description;
    private String defaultBranch;
    private String language;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private int memberCount;

    public ProjectListRow() {
        // for MyBatis
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }
}
