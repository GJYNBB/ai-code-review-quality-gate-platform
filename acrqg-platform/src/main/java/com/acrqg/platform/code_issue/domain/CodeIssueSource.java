package com.acrqg.platform.code_issue.domain;

/**
 * 代码问题来源（design.md §7.2 / R11 / R12 / R17）。
 *
 * <p>对应数据库列 {@code code_issue.source} 的 CHECK 约束取值：
 * <ul>
 *   <li>{@link #SAST}   —— 静态扫描器（B3-D 写入）；</li>
 *   <li>{@link #AI}     —— AI 评审（B3-E 写入）；</li>
 *   <li>{@link #MANUAL} —— 评审人员手动新增（B4-A 提供）。</li>
 * </ul>
 *
 * <p><b>合并约定</b>：B3-D 与 B3-E 都向 {@code code_issue} 表写入；本枚举随两条
 * 分支提交时内容应一致。当合并冲突时，以 B3-D 的版本为准（B3-E 在 worktree
 * 起点先创建本最小定义）。
 *
 * <p>Covers: R11.2, R12.3, R17.1。
 */
public enum CodeIssueSource {

    /** 静态扫描器（checkstyle / eslint / pylint / semgrep）。 */
    SAST,

    /** AI 评审。 */
    AI,

    /** 评审人员手动新增。 */
    MANUAL
}
