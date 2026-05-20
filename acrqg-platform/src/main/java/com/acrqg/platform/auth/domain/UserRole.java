package com.acrqg.platform.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户与角色的多对多关联领域对象（DO），对应表 {@code user_role}（V1__init.sql）。
 *
 * <p>表结构（design.md §7.2）：
 * <pre>
 * CREATE TABLE user_role (
 *   id      BIGSERIAL PRIMARY KEY,
 *   user_id BIGINT    NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
 *   role_id BIGINT    NOT NULL REFERENCES role(id),
 *   CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
 * );
 * </pre>
 *
 * <p>Service 层调用 {@link com.acrqg.platform.auth.repository.UserRoleMapper#insert}
 * 写入；删除场景通过 {@link com.acrqg.platform.auth.repository.UserRoleMapper#deleteByUserAndRole}
 * 单行删除。
 *
 * <p>Covers: R2.1, R3.4。
 */
@TableName(value = "user_role", autoResultMap = true)
public class UserRole {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户主键。 */
    @TableField("user_id")
    private Long userId;

    /** 角色主键（外键到 {@code role.id}）。 */
    @TableField("role_id")
    private Long roleId;

    public UserRole() {
        // Default constructor for MyBatis-Plus
    }

    public UserRole(Long userId, Long roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
}
