package com.acrqg.platform.repository.domain;

/**
 * 代码托管平台枚举。
 *
 * <p>对应 design.md §6.2 与 §7.2 中 {@code repository_binding.provider} 列的
 * CHECK 约束，仅允许下列三种取值：
 *
 * <ul>
 *   <li>{@link #GITHUB} —— github.com / GitHub Enterprise，REST v3。</li>
 *   <li>{@link #GITLAB} —— gitlab.com / 自建 GitLab，REST v4。</li>
 *   <li>{@link #GITEE} —— gitee.com，REST v5。</li>
 * </ul>
 *
 * <p>对应 RFC：DTO 字段 {@code provider} 直接使用 {@link #name()} 字符串
 * 与 DB 列保持一致；前端按字符串显示 / 比较。
 *
 * <p>Covers: R5.1, R5.2, R10.1, R20.1。
 */
public enum Provider {

    /** github.com / GitHub Enterprise（REST v3）。 */
    GITHUB,

    /** gitlab.com 或自建 GitLab（REST v4）。 */
    GITLAB,

    /** gitee.com（REST v5）。 */
    GITEE;

    /** 与 DB CHECK 约束 / DTO 字符串一致的代码。 */
    public String code() {
        return name();
    }
}
