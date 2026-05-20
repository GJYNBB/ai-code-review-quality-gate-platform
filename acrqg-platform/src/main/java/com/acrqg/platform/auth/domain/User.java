package com.acrqg.platform.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 平台用户领域对象（DO），对应数据库表 {@code "user"}（V1__init.sql）。
 *
 * <p>{@code USER} 是 PostgreSQL 保留字，必须使用双引号包裹；通过
 * {@link TableName#value()} 显式声明 {@code "\"user\""} 让 MyBatis-Plus
 * 在生成的 SQL 中正确引用。
 *
 * <p>表结构（design.md §7.2）：
 * <pre>
 * CREATE TABLE "user" (
 *   id              BIGSERIAL    PRIMARY KEY,
 *   username        VARCHAR(64)  NOT NULL,
 *   email           VARCHAR(128) NOT NULL,
 *   password_hash   VARCHAR(120) NOT NULL,
 *   status          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
 *   created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
 *   updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
 * );
 * </pre>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@link #passwordHash} —— BCrypt 哈希；禁止在响应或日志中输出（R1.6 / R23.3），
 *       由 {@code ResponseMaskingAspect} + {@code MaskingLogbackEncoder} 双重保险。</li>
 *   <li>{@link #status} —— 字符串形式存储（与 CHECK 约束保持一致），Service 层
 *       通过 {@link UserStatus#name()} 与 {@link UserStatus#valueOf(String)} 在枚举与字符串
 *       之间转换。</li>
 *   <li>{@link #roles} —— <b>非持久化字段</b>（{@code @TableField(exist=false)}）。
 *       仅在 {@code UserMapper.selectWithRolesByUsername / selectWithRolesById}
 *       的连接查询中由 ResultMap 填充，便于一次查询拿到用户 + 角色列表，避免 N+1。</li>
 * </ul>
 *
 * <p>Covers: R1.1, R1.6, R3.1, R3.4。
 */
@TableName(value = "\"user\"", autoResultMap = true)
public class User {

    /** 主键，{@code BIGSERIAL}，由数据库自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户名，组织内唯一（uk_user_username）。 */
    @TableField("username")
    private String username;

    /** 邮箱，组织内唯一（uk_user_email）。 */
    @TableField("email")
    private String email;

    /**
     * 密码 BCrypt 哈希。{@code ResponseMaskingAspect} 会在 Controller 返回前
     * 把名为 {@code passwordHash} 的字段掩码为 {@code "****"}。
     */
    @TableField("password_hash")
    private String passwordHash;

    /** 状态字符串（{@code ENABLED} / {@code DISABLED}）。 */
    @TableField("status")
    private String status;

    /** 创建时间，由 DB DEFAULT NOW() 写入。 */
    @TableField("created_at")
    private OffsetDateTime createdAt;

    /** 最近更新时间，由 {@code trg_user_updated} 触发器自动刷新。 */
    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    /**
     * 关联的全局角色编码列表（如 {@code SYSTEM_ADMIN}）。
     *
     * <p>非持久化字段；仅在 UserMapper 的连接查询中由 ResultMap 填充。
     */
    @TableField(exist = false)
    private List<String> roles = new ArrayList<>();

    public User() {
        // Default constructor for MyBatis-Plus
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles == null ? new ArrayList<>() : roles;
    }
}
