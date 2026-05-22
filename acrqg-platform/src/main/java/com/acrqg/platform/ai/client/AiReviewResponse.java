package com.acrqg.platform.ai.client;

import java.util.Collections;
import java.util.List;

/**
 * AI 评审客户端返回结果。
 *
 * <p>已剥离 OpenAI 包装层（{@code choices[0].message.content} → 解析为 JSON →
 * 抽取 issues[] / summary）；service 层只关心 {@link #issues} 列表。
 *
 * <p>{@link #summary} 在 design.md §12.2 schema 中是可选字段，本 DTO 同样保留为
 * 可空字符串，便于未来在报告页展示"AI 总结"时直接消费。
 *
 * <p>{@link #rawJson} 保留模型返回的原始 JSON 文本（已通过 Schema 校验），
 * 用于审计 / 写 task_log.detail；当客户端走降级路径时，本字段为
 * {@code null}。
 *
 * @param summary 模型概述（可空）
 * @param issues  问题列表，永不为 {@code null}（空响应即空列表）
 * @param rawJson 原始 JSON 字符串（可空）
 */
public record AiReviewResponse(
        String summary,
        List<AiIssue> issues,
        String rawJson) {

    public AiReviewResponse {
        issues = issues == null ? Collections.emptyList() : List.copyOf(issues);
    }

    /** 空响应（issues 列表为空）。 */
    public static AiReviewResponse empty() {
        return new AiReviewResponse(null, Collections.emptyList(), null);
    }
}
