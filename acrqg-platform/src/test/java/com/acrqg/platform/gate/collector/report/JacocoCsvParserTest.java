package com.acrqg.platform.gate.collector.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link JacocoCsvParser} 单元测试（M11 跟进项）。
 *
 * <p>覆盖：典型 JaCoCo 输出行覆盖率计算 / 表头跳过 / 全 0 / 文件缺失 / 格式错误。
 *
 * <p>Covers: R14.1, R25.1。
 */
class JacocoCsvParserTest {

    private final JacocoCsvParser parser = new JacocoCsvParser();

    private static final String HEADER = "GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,"
            + "BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,COMPLEXITY_MISSED,"
            + "COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED";

    @Test
    void parsesLineCoverage_typicalReport(@TempDir Path tmp) throws Exception {
        // 200 covered + 50 missed + 100 covered + 50 missed → 300 / 400 = 75.00%
        Path csv = tmp.resolve("jacoco.csv");
        Files.writeString(csv, String.join("\n",
                HEADER,
                "g,p,A,10,20,1,2,50,200,1,1,1,1",
                "g,p,B,5,10,0,1,50,100,1,1,1,1"
        ), StandardCharsets.UTF_8);

        BigDecimal pct = parser.parseLineCoveragePercent(csv);

        assertThat(pct).isEqualByComparingTo("75.00");
    }

    @Test
    void parsesLineCoverage_allCovered_returns100(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("jacoco.csv");
        Files.writeString(csv, String.join("\n",
                HEADER,
                "g,p,A,0,10,0,2,0,200,1,1,1,1"
        ), StandardCharsets.UTF_8);

        assertThat(parser.parseLineCoveragePercent(csv)).isEqualByComparingTo("100.00");
    }

    @Test
    void emptyCounters_returns100(@TempDir Path tmp) throws Exception {
        // 没有数据行：避免误判低覆盖
        Path csv = tmp.resolve("jacoco.csv");
        Files.writeString(csv, HEADER, StandardCharsets.UTF_8);

        assertThat(parser.parseLineCoveragePercent(csv)).isEqualByComparingTo("100");
    }

    @Test
    void missingFile_throwsReportNotFound(@TempDir Path tmp) {
        Path csv = tmp.resolve("missing.csv");

        assertThatThrownBy(() -> parser.parseLineCoveragePercent(csv))
                .isInstanceOf(ReportNotFoundException.class);
    }

    @Test
    void malformedRow_throwsReportParse(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("jacoco.csv");
        Files.writeString(csv, String.join("\n",
                HEADER,
                "too,few,columns"
        ), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parseLineCoveragePercent(csv))
                .isInstanceOf(ReportParseException.class)
                .hasMessageContaining("expected >= 13");
    }

    @Test
    void nonNumericCounter_throwsReportParse(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("jacoco.csv");
        Files.writeString(csv, String.join("\n",
                HEADER,
                "g,p,A,10,20,1,2,oops,200,1,1,1,1"
        ), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parseLineCoveragePercent(csv))
                .isInstanceOf(ReportParseException.class)
                .hasMessageContaining("not numeric");
    }
}
