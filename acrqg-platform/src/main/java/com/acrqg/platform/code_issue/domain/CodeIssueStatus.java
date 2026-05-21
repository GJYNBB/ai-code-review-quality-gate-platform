package com.acrqg.platform.code_issue.domain;

/**
 * 代码问题状态（design.md §7.2 / R17.1）。
 *
 * <p>对应数据库列 {@code code_issue.status} 的 CHECK 约束取值：
 * <ul>
 *   <li>{@link #NEW}             —— 新创建（默认）；</li>
 *   <li>{@link #CONFIRMED}       —— 已确认；</li>
 *   <li>{@link #FALSE_POSITIVE}  —— 误报；</li>
 *   <li>{@link #PENDING_VERIFY}  —— 修复待验证；</li>
 *   <li>{@link #CLOSED}          —— 已关闭；</li>
 *   <li>{@link #REOPENED}        —— 重新打开。</li>
 * </ul>
 *
 * <p>合法迁移由 B4-A 模块的 {@code IssueStatusStateMachine} 控制；本枚举只承载取值。
 *
 * <p>Covers: R17.1。
 */
public enum CodeIssueStatus {
    NEW,
    CONFIRMED,
    FALSE_POSITIVE,
    PENDING_VERIFY,
    CLOSED,
    REOPENED
}
