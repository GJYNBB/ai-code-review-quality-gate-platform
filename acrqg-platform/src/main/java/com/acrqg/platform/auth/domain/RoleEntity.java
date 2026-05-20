package com.acrqg.platform.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 角色字典领域对象（DO），对应数据库表 {@code role}（V1__init.sql）。
 *
 * <p><b>类名说明</b>：刻意命名为 {@code RoleEntity} 而非 {@code Role}，
 * 以避免与全局枚举 {@link com.acrqg.platform.infra.permission.Role} 重名。
 * 在 {@code @RequirePermission(role = Role.SYSTEM_ADMIN)} 等业务场景中，
 * 调用方更频繁使用枚举，而 DO 仅在 Mapper 层使用，让位是合理的。
 *
 * <p>表结构（design.md §7.2）：
 * <pre>
 * CREATE TABLE role (
 *   id          BIGSERIAL    PRIMARY KEY,
 *   code        VARCHAR(32)  NOT NULL,   -- DEVELOPER / REVIEWER / PROJECT_ADMIN / SYSTEM_ADMIN / CI_CD
 *   name        VARCHAR(64)  NOT NULL,
 *   description VARCHAR(255),
 *   CONSTRAINT uk_role_code UNIQUE (code)
 * );
 * </pre>
 *
 * <p>Covers: R1.6, R2, R3.1。
 */
@TableName(value = "role", autoResultMap = true)
public class RoleEntity {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 角色编码（与 {@link com.acrqg.platform.infra.permission.Role} 枚举名一致）。 */
    @TableField("code")
    private String code;

    /** 角色名称（中文显示用）。 */
    @TableField("name")
    private String name;

    /** 角色描述。 */
    @TableField("description")
    private String description;

    public RoleEntity() {
        // Default constructor for MyBatis-Plus
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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
}
