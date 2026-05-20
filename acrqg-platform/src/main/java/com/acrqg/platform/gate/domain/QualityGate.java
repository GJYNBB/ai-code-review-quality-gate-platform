package com.acrqg.platform.gate.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 质量门禁版本 DO，对应 {@code quality_gate} 表（V21__m07_quality_gate.sql）。
 *
 * <p>表结构（design.md §7.2）：
 * <pre>
 * CREATE TABLE quality_gate (
 *   id         BIGSERIAL    PRIMARY KEY,
 *   project_id BIGINT       NOT NULL REFERENCES project(id) ON DELETE CASCADE,
 *   name       VARCHAR(128) NOT NULL,
 *   version    INT          NOT NULL,
 *   enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
 *   created_by BIGINT       NOT NULL REFERENCES "user"(id),
 *   created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
 *   CONSTRAINT uk_gate_project_version UNIQUE (project_id, version)
 * );
 * </pre>
 *
 * <p>关键约束：
 * <ul>
 *   <li>{@code (project_id, version)} 联合唯一（uk_gate_project_version）；</li>
 *   <li>同一项目同一时刻仅一条 {@code enabled=TRUE}（部分唯一索引
 *       {@code uk_quality_gate_one_enabled}）；</li>
 *   <li>{@code created_at} 由 DB 端 {@code DEFAULT NOW()} 兜底，应用层不强制填充。</li>
 * </ul>
 *
 * <p>Covers: R13.1, R13.2, R13.4。
 */
@TableName(value = "quality_gate", autoResultMap = true)
public class QualityGate {

    /** 主键，{@code BIGSERIAL}，由数据库自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 项目主键。 */
    @TableField("project_id")
    private Long projectId;

    /** 门禁名称（用户可读）。 */
    @TableField("name")
    private String name;

    /** 版本号，与 {@code projectId} 联合唯一。新版本递增 {@code max(version)+1}。 */
    @TableField("version")
    private Integer version;

    /** 是否当前启用版本；同 {@code projectId} 仅允许一条 TRUE。 */
    @TableField("enabled")
    private Boolean enabled;

    /** 创建者用户主键。 */
    @TableField("created_by")
    private Long createdBy;

    /** 创建时间。 */
    @TableField("created_at")
    private OffsetDateTime createdAt;

    public QualityGate() {
        // for MyBatis-Plus
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
}
