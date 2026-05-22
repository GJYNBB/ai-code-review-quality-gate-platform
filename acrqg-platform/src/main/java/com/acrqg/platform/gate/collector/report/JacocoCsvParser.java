package com.acrqg.platform.gate.collector.report;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * JaCoCo CSV 报告解析器（M11 跟进项）。
 *
 * <p>JaCoCo 默认 CSV 列：{@code GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,
 * INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,
 * COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED}。
 *
 * <p>本解析器汇总所有数据行的 LINE_MISSED / LINE_COVERED，按公式：
 * {@code coverage% = LINE_COVERED * 100 / (LINE_COVERED + LINE_MISSED)}
 * 与 design.md 全工程语句覆盖率门槛口径一致；保留 2 位小数。
 *
 * <p>选择 LINE 而非 INSTRUCTION 的原因：design.md §25.1 与 JaCoCo
 * 默认 maven-plugin check rule 都使用 LINE，便于与 CI 校验对齐。
 *
 * <p>异常处理：
 * <ul>
 *   <li>文件不存在 → 抛 {@link ReportNotFoundException}（调用方退化为 placeholder）；</li>
 *   <li>文件存在但格式异常 → 抛 {@link ReportParseException}（调用方记 WARN）；</li>
 *   <li>分母为 0 → 视为"无可统计代码"，返回 100（避免误判为低覆盖）。</li>
 * </ul>
 *
 * <p>Covers: R14.1, R25.1。
 */
@Component
public class JacocoCsvParser {

    /** JaCoCo CSV 中行覆盖的两个列下标（从 0 开始）。 */
    static final int COL_LINE_MISSED = 7;
    static final int COL_LINE_COVERED = 8;
    /** 期望的最小列数，少于该值视为格式异常。 */
    static final int MIN_COLUMNS = 13;

    /**
     * 解析给定 CSV 文件并返回行覆盖率（0..100，保留 2 位小数）。
     *
     * @throws ReportNotFoundException 文件不存在
     * @throws ReportParseException    文件存在但格式不符合预期
     */
    public BigDecimal parseLineCoveragePercent(Path csvPath) {
        if (csvPath == null || !Files.exists(csvPath)) {
            throw new ReportNotFoundException("jacoco csv not found: " + csvPath);
        }
        if (!Files.isRegularFile(csvPath) || !Files.isReadable(csvPath)) {
            throw new ReportParseException("jacoco csv not readable: " + csvPath);
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ReportParseException("read jacoco csv failed: " + csvPath, e);
        }

        if (lines.isEmpty()) {
            throw new ReportParseException("jacoco csv is empty: " + csvPath);
        }

        long missed = 0;
        long covered = 0;
        boolean header = true;
        int row = 0;
        for (String raw : lines) {
            row++;
            if (raw == null || raw.isBlank()) {
                continue;
            }
            if (header) {
                header = false; // 第一行为表头，跳过
                continue;
            }
            String[] cols = raw.split(",", -1);
            if (cols.length < MIN_COLUMNS) {
                throw new ReportParseException("jacoco csv row " + row
                        + " has " + cols.length + " columns, expected >= " + MIN_COLUMNS);
            }
            try {
                missed += Long.parseLong(cols[COL_LINE_MISSED].trim());
                covered += Long.parseLong(cols[COL_LINE_COVERED].trim());
            } catch (NumberFormatException e) {
                throw new ReportParseException("jacoco csv row " + row
                        + " line counters not numeric", e);
            }
        }

        long total = missed + covered;
        if (total == 0) {
            // 无可统计代码：视为 100%（避免误判低覆盖）
            return BigDecimal.valueOf(100);
        }
        return BigDecimal.valueOf(covered)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .round(new MathContext(5));
    }
}
