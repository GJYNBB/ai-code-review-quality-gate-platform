package com.acrqg.platform.scanner;

import com.acrqg.platform.code_issue.domain.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把各扫描器的原始严重等级归一化为 {@link Severity}（design.md §11.2 / R11.3）。
 *
 * <p>映射表：
 * <table border="1">
 *   <tr><th>工具</th><th>原始</th><th>归一化</th></tr>
 *   <tr><td>Checkstyle</td><td>error</td><td>HIGH</td></tr>
 *   <tr><td>Checkstyle</td><td>warning</td><td>MEDIUM</td></tr>
 *   <tr><td>Checkstyle</td><td>info</td><td>INFO</td></tr>
 *   <tr><td>ESLint</td><td>2</td><td>HIGH</td></tr>
 *   <tr><td>ESLint</td><td>1</td><td>MEDIUM</td></tr>
 *   <tr><td>ESLint</td><td>0</td><td>INFO</td></tr>
 *   <tr><td>Pylint</td><td>fatal/F</td><td>CRITICAL</td></tr>
 *   <tr><td>Pylint</td><td>error/E</td><td>HIGH</td></tr>
 *   <tr><td>Pylint</td><td>warning/W</td><td>MEDIUM</td></tr>
 *   <tr><td>Pylint</td><td>convention/C, refactor/R</td><td>LOW</td></tr>
 *   <tr><td>Semgrep</td><td>ERROR</td><td>CRITICAL</td></tr>
 *   <tr><td>Semgrep</td><td>WARNING</td><td>HIGH</td></tr>
 *   <tr><td>Semgrep</td><td>INFO</td><td>MEDIUM</td></tr>
 *   <tr><td>未知</td><td>—</td><td>INFO + WARN log</td></tr>
 * </table>
 *
 * <p>{@code tool} 入参不区分大小写；常用取值：{@code "checkstyle"} / {@code "eslint"}
 * / {@code "pylint"} / {@code "semgrep"}。{@code raw} 入参为 {@code null} 或空时
 * 直接返回 {@link Severity#INFO} 并记录 WARN（认为是工具异常输出）。
 *
 * <p>Covers: R11.3。
 */
public final class SeverityMapper {

    private static final Logger log = LoggerFactory.getLogger(SeverityMapper.class);

    /** 工具名常量，与 {@code scanner_config.name} 一致。 */
    public static final String TOOL_CHECKSTYLE = "checkstyle";
    public static final String TOOL_ESLINT = "eslint";
    public static final String TOOL_PYLINT = "pylint";
    public static final String TOOL_SEMGREP = "semgrep";

    private SeverityMapper() {
        // utility
    }

    /**
     * 归一化映射。
     *
     * @param tool 工具名（不区分大小写）
     * @param raw  原始严重等级字面量（不区分大小写）
     * @return {@link Severity}；未知时为 {@link Severity#INFO} 并打 WARN
     */
    public static Severity normalize(String tool, String raw) {
        if (raw == null || raw.isBlank()) {
            log.warn("SeverityMapper: empty raw severity for tool={}", tool);
            return Severity.INFO;
        }
        String t = tool == null ? "" : tool.trim().toLowerCase();
        String r = raw.trim().toLowerCase();
        Severity result = switch (t) {
            case TOOL_CHECKSTYLE -> mapCheckstyle(r);
            case TOOL_ESLINT -> mapEslint(r);
            case TOOL_PYLINT -> mapPylint(r);
            case TOOL_SEMGREP -> mapSemgrep(r);
            default -> null;
        };
        if (result != null) {
            return result;
        }
        log.warn("SeverityMapper: unknown severity tool={} raw={}, fallback to INFO", tool, raw);
        return Severity.INFO;
    }

    private static Severity mapCheckstyle(String r) {
        return switch (r) {
            case "error" -> Severity.HIGH;
            case "warning", "warn" -> Severity.MEDIUM;
            case "info" -> Severity.INFO;
            default -> null;
        };
    }

    private static Severity mapEslint(String r) {
        // ESLint severity: 0=off, 1=warn, 2=error；同时也可能字面量出现 "2" / "1" / "0"
        return switch (r) {
            case "2", "error" -> Severity.HIGH;
            case "1", "warn", "warning" -> Severity.MEDIUM;
            case "0", "off" -> Severity.INFO;
            default -> null;
        };
    }

    private static Severity mapPylint(String r) {
        // Pylint type: fatal/F, error/E, warning/W, convention/C, refactor/R
        return switch (r) {
            case "fatal", "f" -> Severity.CRITICAL;
            case "error", "e" -> Severity.HIGH;
            case "warning", "warn", "w" -> Severity.MEDIUM;
            case "convention", "c", "refactor", "r" -> Severity.LOW;
            default -> null;
        };
    }

    private static Severity mapSemgrep(String r) {
        return switch (r) {
            case "error" -> Severity.CRITICAL;
            case "warning", "warn" -> Severity.HIGH;
            case "info" -> Severity.MEDIUM;
            default -> null;
        };
    }
}
