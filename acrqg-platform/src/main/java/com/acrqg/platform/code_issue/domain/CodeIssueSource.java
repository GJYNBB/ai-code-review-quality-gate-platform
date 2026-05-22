package com.acrqg.platform.code_issue.domain;

/**
 * 问题来源字典（design.md §7.2 / R11.2 / R12.3 / R16.2）。
 *
 * <ul>
 *   <li>{@link #SAST}   —— 静态扫描产生（B3-D 静态扫描适配层写入）。</li>
 *   <li>{@link #AI}     —— AI 评审产生（B3-E AI 评审写入）。</li>
 *   <li>{@link #MANUAL} —— 人工补录（B4-A IssueService 写入）。</li>
 * </ul>
 *
 * <p>Covers: R11.2, R12.3, R16.2。
 */
public enum CodeIssueSource {
    SAST,
    AI,
    MANUAL;
}
