package com.acrqg.platform.admin.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 系统参数 DO，对应 {@code system_param} 表（V12__m10_admin.sql）。
 *
 * <p>字段与 design.md §7.2 一致：
 * <pre>
 * CREATE TABLE system_param (
 *   id          BIGSERIAL    PRIMARY KEY,
 *   param_key   VARCHAR(128) NOT NULL UNIQUE,
 *   param_value VARCHAR(1024) NOT NULL,
 *   description VARCHAR(255),
 *   sensitive   BOOLEAN      NOT NULL DEFAULT FALSE,
 *   updated_by  BIGINT REFERENCES "user"(id),
 *   updated_at  TIMESTAMPTZ
 * );
 * </pre>
 *
 * <p>{@link #sensitive} 为 {@code true} 时：
 * <ul>
 *   <li>{@link #paramValue} 列存储 AES-GCM 加密后的密文（由
 *       {@link com.acrqg.platform.infra.crypto.TokenEncryptor} 完成）；</li>
 *   <li>{@code AdminService.getParam} / {@code listParams} 返回时通过
 *       {@code MaskUtils.maskFully} 替换为 {@code "****"}；</li>
 *   <li>审计 detail 中变更前后值同样使用掩码（R22.5）。</li>
 * </ul>
 *
 * <p>Covers: R21.4, R21.5, R22.1, R22.5, R23.3, R24.3。
 */
@TableName(value = "system_param", autoResultMap = true)
public class SystemParam {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("param_key")
    private String paramKey;

    @TableField("param_value")
    private String paramValue;

    @TableField("description")
    private String description;

    @TableField("sensitive")
    private Boolean sensitive;

    @TableField("updated_by")
    private Long updatedBy;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    public SystemParam() {
        // for MyBatis-Plus
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getParamKey() {
        return paramKey;
    }

    public void setParamKey(String paramKey) {
        this.paramKey = paramKey;
    }

    public String getParamValue() {
        return paramValue;
    }

    public void setParamValue(String paramValue) {
        this.paramValue = paramValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getSensitive() {
        return sensitive;
    }

    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
