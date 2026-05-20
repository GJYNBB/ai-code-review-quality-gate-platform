package com.acrqg.platform.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 项目领域对象（DO），对应数据库表 {@code project}（V11__m02_project.sql）。
 *
 * <p>表结构来自 design.md §7.2：
 * <pre>
 * CREATE TABLE project (
 *   id              BIGSERIAL    PRIMARY KEY,
 *   name            VARCHAR(128) NOT NULL,
 *   description     VARCHAR(512),
 *   default_branch  VARCHAR(128) NOT NULL DEFAULT 'main',
 *   language        VARCHAR(32)  NOT NULL,
 *   created_by      BIGINT       NOT NULL REFERENCES "user"(id),
 *   created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
 *   updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
 *   CONSTRAINT uk_project_name UNIQUE (name)
 * );
 * </pre>
 *
 * <p>字段填充策略：
 * <ul>
 *   <li>{@code createdAt} / {@code updatedAt}：使用 {@code OffsetDateTime}，对齐
 *       PostgreSQL {@code TIMESTAMPTZ}。{@code AuditMetaObjectHandler}
 *       （{@link com.acrqg.platform.infra.config.MyBatisPlusConfig})
 *       仅处理 {@code LocalDateTime} 字段，因此本 DO 不声明
 *       {@code @TableField(fill=...)}；INSERT 时若字段为 {@code null}，
 *       由 DB 端 {@code DEFAULT NOW()} 兜底；UPDATE 时由
 *       {@code trg_project_updated} 触发器自动刷新 {@code updated_at}。</li>
 * </ul>
 *
 * <p>Covers: R4.1, R4.2, R4.3。
 */
@TableName(value = "project", autoResultMap = true)
public class Project {

    /** 主键，{@code BIGSERIAL}，由数据库自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 项目名称，组织内唯一（uk_project_name）。 */
    @TableField("name")
    private String name;

    /** 项目描述，可空。 */
    @TableField("description")
    private String description;

    /** 默认分支，常见值为 {@code main} / {@code master}。 */
    @TableField("default_branch")
    private String defaultBranch;

    /** 主要语言：Java / Python / JavaScript / TypeScript / Go。 */
    @TableField("language")
    private String language;

    /** 创建者用户主键。 */
    @TableField("created_by")
    private Long createdBy;

    /** 创建时间。 */
    @TableField("created_at")
    private OffsetDateTime createdAt;

    /** 最近更新时间。 */
    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    public Project() {
        // Default constructor for MyBatis-Plus
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
}
