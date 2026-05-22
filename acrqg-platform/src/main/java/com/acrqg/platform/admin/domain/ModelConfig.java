package com.acrqg.platform.admin.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * AI 模型配置 DO，对应 {@code model_config} 表（V12__m10_admin.sql）。
 *
 * <p>字段与 design.md §7.2 / §6.10 保持一致：
 * <pre>
 * CREATE TABLE model_config (
 *   id                  BIGSERIAL    PRIMARY KEY,
 *   name                VARCHAR(64)  NOT NULL,
 *   base_url            VARCHAR(255) NOT NULL,
 *   api_key_encrypted   VARCHAR(1024) NOT NULL,
 *   timeout_seconds     INT          NOT NULL DEFAULT 60 CHECK (10..300),
 *   enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
 *   created_at / updated_at  TIMESTAMPTZ
 * );
 * </pre>
 *
 * <p>{@link #apiKeyEncrypted} 字段持久化的是经
 * {@link com.acrqg.platform.infra.crypto.TokenEncryptor} 加密后的 base64 密文；
 * <b>禁止</b>在 {@code ModelConfigDTO} 等响应体中直接暴露该字段，必须由
 * {@code AdminService} 改写为 {@code apiKeyMasked="****"}。
 *
 * <p>时间戳同 {@code Project} 一样使用 {@link OffsetDateTime} 对齐
 * {@code TIMESTAMPTZ}；INSERT 由 DB 端 {@code DEFAULT NOW()} 兜底，UPDATE 由
 * {@code trg_model_config_updated} 触发器自动刷新。
 *
 * <p>Covers: R21.1, R21.2, R23.2。
 */
@TableName(value = "model_config", autoResultMap = true)
public class ModelConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("base_url")
    private String baseUrl;

    @TableField("api_key_encrypted")
    private String apiKeyEncrypted;

    @TableField("timeout_seconds")
    private Integer timeoutSeconds;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    public ModelConfig() {
        // for MyBatis-Plus
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

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKeyEncrypted() {
        return apiKeyEncrypted;
    }

    public void setApiKeyEncrypted(String apiKeyEncrypted) {
        this.apiKeyEncrypted = apiKeyEncrypted;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
