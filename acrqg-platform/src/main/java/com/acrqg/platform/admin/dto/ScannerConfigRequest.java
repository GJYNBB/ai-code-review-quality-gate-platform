package com.acrqg.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 扫描器配置 upsert 请求体。
 *
 * <p>对应 {@code POST /api/v1/admin/scanners}：以 {@code name} 为业务键 upsert——
 * 已存在则更新其余字段（{@code language}/{@code enabled}/{@code command}/
 * {@code resultParserType}），不存在则插入。
 *
 * <p>字段约束：
 * <ul>
 *   <li>{@code name}：1..64，业务键；</li>
 *   <li>{@code language}：1..32，建议 {@code java}/{@code javascript}/
 *       {@code typescript}/{@code python}/{@code any} 等；</li>
 *   <li>{@code command}：1..1024，模板字符串，可含 {@code {workdir}}/
 *       {@code {file}}/{@code {output}} 占位；</li>
 *   <li>{@code resultParserType}：1..32，建议为 {@code CHECKSTYLE_XML}/
 *       {@code ESLINT_JSON}/{@code PYLINT_JSON}/{@code SEMGREP_JSON} 之一，
 *       非枚举校验是因 B3-D 静态扫描适配器允许后续扩展类型。</li>
 *   <li>{@code enabled}：可空，{@code null} 视为 {@code true}（默认启用）。</li>
 * </ul>
 *
 * <p>Covers: R21.3。
 *
 * @param name             扫描器名称（业务键）
 * @param language         适用语言
 * @param enabled          是否启用（{@code null} 视为 true）
 * @param command          命令模板
 * @param resultParserType 结果解析类型
 */
public record ScannerConfigRequest(
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Size(max = 32) String language,
        Boolean enabled,
        @NotBlank @Size(max = 1024) String command,
        @NotBlank @Size(max = 32) String resultParserType
) {
}
