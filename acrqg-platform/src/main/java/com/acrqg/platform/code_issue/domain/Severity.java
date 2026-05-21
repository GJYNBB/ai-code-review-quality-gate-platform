package com.acrqg.platform.code_issue.domain;

/**
 * 代码问题严重等级（design.md §7.2 / R12.3 / R14.1）。
 *
 * <p>对应数据库列 {@code code_issue.severity} 的 CHECK 约束取值：
 * <ul>
 *   <li>{@link #CRITICAL} —— 必修复（门禁默认 BLOCKER）；</li>
 *   <li>{@link #HIGH}     —— 高优先；</li>
 *   <li>{@link #MEDIUM}   —— 中等；</li>
 *   <li>{@link #LOW}      —— 低；</li>
 *   <li>{@link #INFO}     —— 信息（建议性）。</li>
 * </ul>
 *
 * <p>{@link #weight()} 返回 design.md §12.5 给出的权重值，用于
 * {@code ai_risk_score} 的计算（R12.6）。
 *
 * <p>Covers: R11, R12.3, R12.6, R14.1。
 */
public enum Severity {

    CRITICAL(100),
    HIGH(70),
    MEDIUM(40),
    LOW(15),
    INFO(5);

    private final int weight;

    Severity(int weight) {
        this.weight = weight;
    }

    /** {@code ai_risk_score} 计算时使用的严重等级权重（design.md §12.5）。 */
    public int weight() {
        return weight;
    }

    /**
     * 大小写不敏感解析；未识别字符串退化为 {@link #INFO}（保守选择，避免解析失败丢失问题）。
     */
    public static Severity from(String raw) {
        if (raw == null) {
            return INFO;
        }
        try {
            return Severity.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignore) {
            return INFO;
        }
    }
}
