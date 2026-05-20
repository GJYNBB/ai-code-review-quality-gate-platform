package com.acrqg.platform.gate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 门禁规则 DTO（design.md §8.4）。
 *
 * <p>对应 design.md：
 * <pre>
 * public record GateRuleDTO(Long id, String metric, String operator,
 *                           String threshold, String severity, boolean enabled) {}
 * </pre>
 *
 * <p>本实现把 {@code enabled} 设为 {@link Boolean}（装箱）而非 {@code boolean}：
 * 这样在保存请求中允许调用方省略该字段（{@code null}），由 Service 层默认视为
 * {@code true}（与 DB 列 {@code DEFAULT TRUE} 一致）；同时返回响应中保留
 * 非空（Service 在转换时主动填充）。
 *
 * <p>Bean Validation 仅校验字段格式与字符串非空 / 长度；
 * <b>取值集合（metric / operator / severity）的合法性由 Service 层校验</b>，
 * 一旦命中非法值抛 {@code BusinessException(GATE_RULE_INVALID)} 并在 details
 * 数组中携带规则索引（R13.3）。这是因为：
 * <ol>
 *   <li>Bean Validation 失败统一映射为 {@code VALIDATION_ERROR}，
 *       与 {@code GATE_RULE_INVALID} 错误码语义不同；</li>
 *   <li>Service 层校验便于在 details 中精准定位 {@code rules[i].metric} 的索引；</li>
 *   <li>DB 端 CHECK 约束作为最后防线，但应用层应在 SQL 之前先拦截。</li>
 * </ol>
 *
 * <p>Covers: R13.1, R13.2, R13.3, R13.6。
 *
 * @param id        规则主键（保存时通常传 {@code null}；服务端忽略并由 DB 自增）
 * @param metric    指标名（6 选 1，由 Service 校验）
 * @param operator  比较运算符（6 选 1，由 Service 校验）
 * @param threshold 阈值字符串（数值 / 百分比文本）
 * @param severity  失败级别（{@code BLOCKER} / {@code WARN}，由 Service 校验）
 * @param enabled   是否启用（{@code null} 视为 {@code true}）
 */
public record GateRuleDTO(
        Long id,
        @NotBlank @Size(max = 64) String metric,
        @NotBlank @Size(max = 4) String operator,
        @NotBlank @Size(max = 32) String threshold,
        @NotBlank @Size(max = 8) String severity,
        Boolean enabled
) {
}
