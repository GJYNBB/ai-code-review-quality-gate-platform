package com.acrqg.platform.admin.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 静态扫描器配置 DO，对应 {@code scanner_config} 表（V12__m10_admin.sql）。
 *
 * <p>字段与 design.md §7.2 一致：
 * <pre>
 * CREATE TABLE scanner_config (
 *   id                 BIGSERIAL    PRIMARY KEY,
 *   name               VARCHAR(64)  NOT NULL UNIQUE,
 *   language           VARCHAR(32)  NOT NULL,
 *   enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
 *   command            VARCHAR(1024) NOT NULL,
 *   result_parser_type VARCHAR(32)  NOT NULL,
 *   created_at / updated_at  TIMESTAMPTZ
 * );
 * </pre>
 *
 * <p>{@link #command} 是模板字符串，可包含 {@code {workdir}} / {@code {file}} /
 * {@code {output}} 占位，由 B3-D 静态扫描适配器在执行前替换。
 *
 * <p>{@link #resultParserType} 取值：{@code CHECKSTYLE_XML} / {@code ESLINT_JSON} /
 * {@code PYLINT_JSON} / {@code SEMGREP_JSON}，与 V12 种子数据一致。
 *
 * <p>Covers: R21.3。
 */
@TableName(value = "scanner_config", autoResultMap = true)
public class ScannerConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("language")
    private String language;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("command")
    private String command;

    @TableField("result_parser_type")
    private String resultParserType;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    public ScannerConfig() {
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

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getResultParserType() {
        return resultParserType;
    }

    public void setResultParserType(String resultParserType) {
        this.resultParserType = resultParserType;
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
