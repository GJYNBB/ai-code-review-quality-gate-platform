package com.acrqg.platform.code_issue.domain;

/**
 * 问题严重等级（design.md §11.2 / R11.3）。
 *
 * <p>对应 {@code code_issue.severity} 列的 CHECK 约束取值，
 * 由 {@link com.acrqg.platform.scanner.SeverityMapper} 把各扫描器原始等级
 * 归一化到本枚举：
 * <ul>
 *   <li>{@link #CRITICAL} —— Semgrep ERROR / Pylint fatal/F；</li>
 *   <li>{@link #HIGH}     —— Checkstyle error、ESLint 2、Pylint error/E、Semgrep WARNING；</li>
 *   <li>{@link #MEDIUM}   —— Checkstyle warning、ESLint 1、Pylint warning/W、Semgrep INFO；</li>
 *   <li>{@link #LOW}      —— Pylint convention/C、refactor/R；</li>
 *   <li>{@link #INFO}     —— Checkstyle info、ESLint 0、未知映射兜底。</li>
 * </ul>
 *
 * <p>Covers: R11.3。
 */
public enum Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO;
}
