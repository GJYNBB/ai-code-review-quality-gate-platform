package com.acrqg.platform.gate.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 质量门禁版本对外视图（design.md §8.4）。
 *
 * <p>由 {@code QualityGateService.save} / {@code getEnabled} / {@code getVersion}
 * 返回，通过 {@code ApiResponse<QualityGateDTO>} 包装。同时也是
 * {@code listVersions} 列表项与 {@code getDefaultTemplate} 模板视图的载体。
 *
 * <p>当作为<b>默认模板</b>返回时（{@link com.acrqg.platform.gate.service.QualityGateService#getDefaultTemplate()}），
 * {@code id} / {@code projectId} / {@code version} / {@code createdBy} /
 * {@code createdAt} 字段均为 {@code null}，{@code name} 为 {@code "默认模板"}，
 * {@code enabled} 为 {@code false}（模板未启用），{@code rules} 为 design §13.5
 * 列出的 3 条预设规则。
 *
 * <p>{@code rules} 列表中的 {@link GateRuleDTO#enabled} 在响应中始终非空：
 * 即便保存请求传 {@code null}，Service 在转换返回 DTO 时也会主动填为 {@code true}。
 *
 * <p>Covers: R13.1, R13.2, R13.4, R13.5。
 *
 * @param id         门禁主键（模板时 {@code null}）
 * @param projectId  项目主键（模板时 {@code null}）
 * @param name       门禁名称
 * @param version    版本号（模板时 {@code null}）
 * @param enabled    是否启用版本（模板时 {@code false}）
 * @param createdBy  创建者用户主键（模板时 {@code null}）
 * @param createdAt  创建时间（模板时 {@code null}）
 * @param rules      规则列表
 */
public record QualityGateDTO(
        Long id,
        Long projectId,
        String name,
        Integer version,
        boolean enabled,
        Long createdBy,
        OffsetDateTime createdAt,
        List<GateRuleDTO> rules
) {
}
