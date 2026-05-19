package com.acrqg.platform.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 审计日志领域对象（DO），对应数据库表 {@code audit_log}（V1__init.sql）。
 *
 * <p>表结构来自 design.md §7.2 与 V1__init.sql：
 * <pre>
 * CREATE TABLE audit_log (
 *   id                BIGSERIAL    PRIMARY KEY,
 *   operator_id       BIGINT       REFERENCES "user"(id),
 *   operator_username VARCHAR(64),
 *   action            VARCHAR(64)  NOT NULL,
 *   resource_type     VARCHAR(64),
 *   resource_id       VARCHAR(64),
 *   ip                VARCHAR(45),
 *   detail            JSONB,
 *   created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
 * );
 * </pre>
 *
 * <p><b>Append-only 双重保险</b>：数据库层通过
 * {@code reject_audit_modify()} + {@code trg_audit_no_update / trg_audit_no_delete}
 * 触发器拒绝任何 UPDATE / DELETE（R22.4）；Mapper 层（{@link
 * com.acrqg.platform.audit.repository.AuditLogMapper}）也不暴露 update / delete
 * 方法作为第二道防线。
 *
 * <p><b>detail 字段</b>：以 PostgreSQL JSONB 存储，但 MyBatis-Plus 直连 JSONB 需要
 * 自定义 TypeHandler；为避免在 B1-B 引入不必要的依赖，本 DO 将 {@code detail}
 * 序列化为 {@link String}（JSON 字符串），由 Service 层在写入前调用
 * {@link com.acrqg.platform.common.util.JsonUtils#toJson(Object)}，在读取时转回
 * {@code Map<String,Object>}。这样保持 Mapper 简洁同时仍能在 PostgreSQL 端享受
 * JSONB 的查询能力（如 GIN 索引、{@code ->>} 操作符）。
 *
 * <p>Covers: R22.1, R22.3, R22.4, R22.5。
 */
@TableName(value = "audit_log", autoResultMap = true)
public class AuditLog {

    /** 主键，{@code BIGSERIAL}，由数据库自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作者用户主键，可为空（系统级操作如 PLATFORM_INIT）。 */
    @TableField("operator_id")
    private Long operatorId;

    /** 操作者用户名快照，可为空。审计需要保留快照以避免用户被删除后无法追溯。 */
    @TableField("operator_username")
    private String operatorUsername;

    /** 动作名（如 LOGIN_SUCCESS / PROJECT_CREATED 等），非空。 */
    @TableField("action")
    private String action;

    /** 资源类型（USER / PROJECT / REPOSITORY / QUALITY_GATE / SYSTEM ...）。 */
    @TableField("resource_type")
    private String resourceType;

    /** 资源 ID 字符串（统一 String 以兼容非数值主键场景）。 */
    @TableField("resource_id")
    private String resourceId;

    /** 客户端 IP（IPv4/IPv6，最长 45 字符）。 */
    @TableField("ip")
    private String ip;

    /** 操作明细 JSON 字符串，已对敏感字段掩码（R22.5 / R23.3）。 */
    @TableField(value = "detail")
    private String detailJson;

    /** 创建时间，由数据库 {@code DEFAULT NOW()} 写入；INSERT 不显式赋值时由 DB 兜底。 */
    @TableField("created_at")
    private OffsetDateTime createdAt;

    public AuditLog() {
        // Default constructor for MyBatis-Plus
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorUsername() {
        return operatorUsername;
    }

    public void setOperatorUsername(String operatorUsername) {
        this.operatorUsername = operatorUsername;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
