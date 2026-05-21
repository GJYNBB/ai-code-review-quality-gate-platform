package com.acrqg.platform.code_issue.domain;

/**
 * 问题状态字典（design.md §7.2 / §7.4 / R17.1）。
 *
 * <p>状态机由 B4-A {@code IssueService} 完整实现。本枚举的枚举值对应
 * {@code code_issue.status} CHECK 约束的取值：
 * <ul>
 *   <li>{@link #NEW}             —— 新建（SAST / AI 写入的默认状态）。</li>
 *   <li>{@link #CONFIRMED}       —— 经人工 / Reviewer 确认。</li>
 *   <li>{@link #FALSE_POSITIVE}  —— 标记误报。</li>
 *   <li>{@link #PENDING_VERIFY}  —— 已修复，等待复测。</li>
 *   <li>{@link #CLOSED}          —— 关闭。</li>
 *   <li>{@link #REOPENED}        —— 重新打开。</li>
 * </ul>
 *
 * <p>合法迁移由 B4-A 的 Property 2（design §19）覆盖。
 *
 * <p>Covers: R17.1。
 */
public enum CodeIssueStatus {
    NEW,
    CONFIRMED,
    FALSE_POSITIVE,
    PENDING_VERIFY,
    CLOSED,
    REOPENED;
}
