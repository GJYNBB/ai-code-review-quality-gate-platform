package com.acrqg.platform.gate.dto;

import java.math.BigDecimal;

/**
 * 单条门禁规则评估结果 DTO。
 *
 * <p>对应 {@link com.acrqg.platform.gate.domain.RuleEval}，用于前端报告页展示
 * 失败 / 通过规则明细（design §8.4 / R14.8）。
 *
 * <p>Covers: R14.2, R14.3, R14.4, R14.8。
 *
 * @param metric    指标名
 * @param operator  比较运算符
 * @param threshold 阈值字符串
 * @param severity  失败级别（{@code BLOCKER} / {@code WARN}）
 * @param actual    采集到的实际值
 * @param passed    {@code true} 表示规则通过
 */
public record RuleEvalDTO(
        String metric,
        String operator,
        String threshold,
        String severity,
        BigDecimal actual,
        boolean passed
) {
}
