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
 * {@link CpdXmlParser} 单元测试（M11 跟进项）。
 *
 * <p>覆盖：典型 PMD-CPD 输出重复率计算 / 多文件累加 / total-loc 缺失 / XXE 防御。
 *
 * <p>Covers: R14.1, R23.4。
 */
class CpdXmlParserTest {

    private final CpdXmlParser parser = new CpdXmlParser();

    @Test
    void parsesDuplicateRate_typicalReport(@TempDir Path tmp) throws Exception {
        // 一条 duplication：lines=50, files=2 → duplicated=100；total-loc=1000 → 10.00%
        Path xml = tmp.resolve("cpd.xml");
        Files.writeString(xml,
                "<?xml version=\"1.0\"?><pmd-cpd>"
                + "<duplication lines=\"50\" tokens=\"100\">"
                + "<file path=\"A.java\" line=\"1\" endline=\"50\"/>"
                + "<file path=\"B.java\" line=\"100\" endline=\"150\"/>"
                + "<codefragment><![CDATA[hello]]></codefragment>"
                + "</duplication>"
                + "</pmd-cpd>", StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve(CpdXmlParser.TOTAL_LOC_FILE), "1000",
                StandardCharsets.UTF_8);

        BigDecimal pct = parser.parseDuplicateRatePercent(xml);

        assertThat(pct).isEqualByComparingTo("10.00");
    }

    @Test
    void parsesDuplicateRate_multipleBlocks(@TempDir Path tmp) throws Exception {
        // 两条 duplication：(20×3=60) + (10×2=20) = 80；total-loc=400 → 20.00%
        Path xml = tmp.resolve("cpd.xml");
        Files.writeString(xml,
                "<pmd-cpd>"
                + "<duplication lines=\"20\" tokens=\"50\">"
                + "<file path=\"A.java\"/><file path=\"B.java\"/><file path=\"C.java\"/>"
                + "</duplication>"
                + "<duplication lines=\"10\" tokens=\"30\">"
                + "<file path=\"D.java\"/><file path=\"E.java\"/>"
                + "</duplication>"
                + "</pmd-cpd>", StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve(CpdXmlParser.TOTAL_LOC_FILE), "400",
                StandardCharsets.UTF_8);

        assertThat(parser.parseDuplicateRatePercent(xml)).isEqualByComparingTo("20.00");
    }

    @Test
    void emptyReport_returnsZero(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("cpd.xml");
        Files.writeString(xml, "<pmd-cpd></pmd-cpd>", StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve(CpdXmlParser.TOTAL_LOC_FILE), "1000",
                StandardCharsets.UTF_8);

        assertThat(parser.parseDuplicateRatePercent(xml)).isEqualByComparingTo("0.00");
    }

    @Test
    void missingXml_throwsReportNotFound(@TempDir Path tmp) {
        assertThatThrownBy(() -> parser.parseDuplicateRatePercent(tmp.resolve("missing.xml")))
                .isInstanceOf(ReportNotFoundException.class);
    }

    @Test
    void missingTotalLocFile_throwsReportNotFound(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("cpd.xml");
        Files.writeString(xml, "<pmd-cpd></pmd-cpd>", StandardCharsets.UTF_8);
        // total-loc.txt 故意不创建

        assertThatThrownBy(() -> parser.parseDuplicateRatePercent(xml))
                .isInstanceOf(ReportNotFoundException.class)
                .hasMessageContaining("total-loc.txt");
    }

    @Test
    void invalidTotalLoc_throwsReportParse(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("cpd.xml");
        Files.writeString(xml, "<pmd-cpd></pmd-cpd>", StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve(CpdXmlParser.TOTAL_LOC_FILE), "abc",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parseDuplicateRatePercent(xml))
                .isInstanceOf(ReportParseException.class)
                .hasMessageContaining("integer");
    }

    @Test
    void doctypeXxe_isRejected(@TempDir Path tmp) throws Exception {
        // XXE 防御：DOCTYPE 触发 SAXParseException → ReportParseException
        Path xml = tmp.resolve("cpd.xml");
        Files.writeString(xml,
                "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\"> ]>"
                + "<pmd-cpd></pmd-cpd>", StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve(CpdXmlParser.TOTAL_LOC_FILE), "1000",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parseDuplicateRatePercent(xml))
                .isInstanceOf(ReportParseException.class);
    }
}
