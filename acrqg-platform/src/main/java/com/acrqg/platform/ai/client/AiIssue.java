package com.acrqg.platform.ai.client;

/**
 * AI 评审返回的单条问题（design.md §12.2 schema 中 issues[] 的元素）。
 *
 * <p>所有字段含义遵循 schema：
 * <ul>
 *   <li>{@link #filePath} —— 必填，长度 ≤ 512；</li>
 *   <li>{@link #lineNo} —— 可空，{@code [0, 1000000]}；</li>
 *   <li>{@link #severity} —— 必填，取值 {@code CRITICAL/HIGH/MEDIUM/LOW/INFO}；</li>
 *   <li>{@link #ruleCode} —— 可空，长度 ≤ 128；</li>
 *   <li>{@link #description} —— 必填，长度 5..4000；</li>
 *   <li>{@link #suggestion} —— 必填，长度 5..4000；</li>
 *   <li>{@link #confidence} —— 必填，{@code [0, 1]}。</li>
 * </ul>
 *
 * <p>本 record 不做任何验证（验证由
 * {@link com.acrqg.platform.ai.schema.AiReviewSchemaValidator} 完成），
 * 由 AiReviewClient 直接 Jackson 反序列化得到。
 *
 * <p>Covers: R12.3。
 */
public record AiIssue(
        String filePath,
        Integer lineNo,
        String severity,
        String ruleCode,
        String description,
        String suggestion,
        Double confidence) {
}
