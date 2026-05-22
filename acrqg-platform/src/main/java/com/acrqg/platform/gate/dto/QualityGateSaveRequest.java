package com.acrqg.platform.gate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 保存质量门禁版本的请求体（design.md §8.4）。
 *
 * <p>对应 design.md：
 * <pre>
 * public record QualityGateSaveRequest(
 *     @NotBlank @Size(max=128) String name,
 *     @NotEmpty @Valid List&lt;GateRuleDTO&gt; rules
 * ) {}
 * </pre>
 *
 * <p>对应 {@code POST /api/v1/projects/{id}/quality-gate}。语义：每次保存生成
 * 新版本（{@code version = max(version)+1}），保存成功后旧的
 * {@code enabled=TRUE} 版本被翻为 {@code FALSE}，新版本 {@code enabled=TRUE}
 * （R13.4）。整个流程在同一事务内完成。
 *
 * <p>{@link #rules} 通过 {@code @NotEmpty} 强制至少一条；列表内每条规则附带
 * {@code @Valid} 触发嵌套校验。规则的 {@code metric} / {@code operator} /
 * {@code severity} 取值集合在 Service 层校验，以便携带规则索引信息（R13.3）。
 *
 * <p>Covers: R13.1, R13.2, R13.3。
 *
 * @param name  门禁名称（非空，最长 128）
 * @param rules 规则列表（至少一条；每条经 Bean Validation + Service 集合校验）
 */
public record QualityGateSaveRequest(
        @NotBlank @Size(max = 128) String name,
        @NotEmpty @Valid List<GateRuleDTO> rules
) {
}
