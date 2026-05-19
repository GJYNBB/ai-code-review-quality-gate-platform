package com.acrqg.platform.project.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 项目成员领域对象（DO），对应数据库表 {@code project_member}（V11__m02_project.sql）。
 *
 * <p>表结构（design.md §7.2）：
 * <pre>
 * CREATE TABLE project_member (
 *   id           BIGSERIAL    PRIMARY KEY,
 *   project_id   BIGINT       NOT NULL REFERENCES project(id) ON DELETE CASCADE,
 *   user_id      BIGINT       NOT NULL REFERENCES "user"(id),
 *   project_role VARCHAR(32)  NOT NULL CHECK (project_role IN ('DEVELOPER','REVIEWER','PROJECT_ADMIN')),
 *   joined_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
 *   CONSTRAINT uk_project_member UNIQUE (project_id, user_id)
 * );
 * </pre>
 *
 * <p>{@code projectRole} 字段以字符串形式存储，与 DB CHECK 约束一一对应；
 * Service 层在写入前应通过
 * {@link com.acrqg.platform.infra.permission.ProjectRole#name()} 转换。
 *
 * <p>Covers: R6.1, R6.4。
 */
@TableName(value = "project_member", autoResultMap = true)
public class ProjectMember {

    /** 主键，{@code BIGSERIAL}，由数据库自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 项目主键。 */
    @TableField("project_id")
    private Long projectId;

    /** 用户主键。 */
    @TableField("user_id")
    private Long userId;

    /** 项目内角色（DEVELOPER / REVIEWER / PROJECT_ADMIN）。 */
    @TableField("project_role")
    private String projectRole;

    /** 加入时间。INSERT 时若为 {@code null} 由 DB {@code DEFAULT NOW()} 兜底。 */
    @TableField("joined_at")
    private OffsetDateTime joinedAt;

    public ProjectMember() {
        // Default constructor for MyBatis-Plus
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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
