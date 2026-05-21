package com.acrqg.platform.repository.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 仓库绑定领域对象（DO），对应数据库表 {@code repository_binding}
 * （V20__m02_repository.sql）。
 *
 * <p>表结构来自 design.md §7.2：
 * <pre>
 * CREATE TABLE repository_binding (
 *   id                       BIGSERIAL PRIMARY KEY,
 *   project_id               BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
 *   provider                 VARCHAR(16)  NOT NULL CHECK (provider IN ('GITHUB','GITLAB','GITEE')),
 *   repo_url                 VARCHAR(512) NOT NULL,
 *   access_token_encrypted   VARCHAR(1024) NOT NULL,
 *   webhook_secret_encrypted VARCHAR(1024) NOT NULL,
 *   webhook_url              VARCHAR(512) NOT NULL,
 *   status                   VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
 *   last_checked_at          TIMESTAMPTZ,
 *   created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   CONSTRAINT uk_repository_binding_project UNIQUE (project_id)
 * );
 * </pre>
 *
 * <p>{@link #accessTokenEncrypted} / {@link #webhookSecretEncrypted} 字段持久化的是
 * 经 {@link com.acrqg.platform.infra.crypto.TokenEncryptor TokenEncryptor} 加密
 * 后的 base64 密文，<b>禁止</b>在任何接口响应或日志中暴露（R5.4 / R23.3）。
 *
 * <p>时间戳同 {@code Project} 一样使用 {@link OffsetDateTime} 对齐
 * {@code TIMESTAMPTZ}；INSERT 时若为 {@code null} 由 DB {@code DEFAULT NOW()} 兜底，
 * UPDATE 时由 {@code trg_repository_binding_updated} 触发器自动刷新。
 *
 * <p>Covers: R5.1, R5.2, R5.3, R5.4, R5.5, R5.6。
 */
@TableName(value = "repository_binding", autoResultMap = true)
public class RepositoryBinding {

    /** 主键，{@code BIGSERIAL}，由数据库自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 项目主键。一项目一绑定（uk_repository_binding_project）。 */
    @TableField("project_id")
    private Long projectId;

    /** 代码平台代码（GITHUB / GITLAB / GITEE）。 */
    @TableField("provider")
    private String provider;

    /** 仓库原始 URL。 */
    @TableField("repo_url")
    private String repoUrl;

    /** AES-GCM 加密后的 access token base64 密文。 */
    @TableField("access_token_encrypted")
    private String accessTokenEncrypted;

    /** AES-GCM 加密后的 webhook secret base64 密文。 */
    @TableField("webhook_secret_encrypted")
    private String webhookSecretEncrypted;

    /** 平台 Webhook 接收 URL。 */
    @TableField("webhook_url")
    private String webhookUrl;

    /** 绑定状态：ACTIVE / INACTIVE。 */
    @TableField("status")
    private String status;

    /** 最近 ping 成功时间；首次绑定后写入。 */
    @TableField("last_checked_at")
    private OffsetDateTime lastCheckedAt;

    /** 创建时间。 */
    @TableField("created_at")
    private OffsetDateTime createdAt;

    /** 最近更新时间。 */
    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    public RepositoryBinding() {
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getAccessTokenEncrypted() {
        return accessTokenEncrypted;
    }

    public void setAccessTokenEncrypted(String accessTokenEncrypted) {
        this.accessTokenEncrypted = accessTokenEncrypted;
    }

    public String getWebhookSecretEncrypted() {
        return webhookSecretEncrypted;
    }

    public void setWebhookSecretEncrypted(String webhookSecretEncrypted) {
        this.webhookSecretEncrypted = webhookSecretEncrypted;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(OffsetDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
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
