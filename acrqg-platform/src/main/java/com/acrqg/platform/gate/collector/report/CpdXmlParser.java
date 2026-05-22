package com.acrqg.platform.gate.collector.report;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.springframework.stereotype.Component;

/**
 * PMD-CPD XML 报告解析器（M11 跟进项）。
 *
 * <p>PMD-CPD 默认 XML 结构：
 * <pre>{@code
 * <pmd-cpd>
 *   <duplication lines="50" tokens="100">
 *     <file path="..." line="1" endline="50"/>
 *     <file path="..." line="100" endline="150"/>
 *     <codefragment><![CDATA[ ... ]]></codefragment>
 *   </duplication>
 *   ...
 * </pmd-cpd>
 * }</pre>
 *
 * <p>计算口径（与 SonarQube 对齐）：
 * <ul>
 *   <li>每条 {@code <duplication lines=N>} 由 K 个文件共享，记重复行数 {@code N × K}；</li>
 *   <li>累加得到 {@code duplicatedLines}；</li>
 *   <li>从同目录下的 {@code total-loc.txt}（单行整数）读取总 LOC；</li>
 *   <li>{@code duplicate_rate% = duplicatedLines × 100 / totalLoc}（保留 2 位小数）。</li>
 * </ul>
 *
 * <p>{@code total-loc.txt} 缺失时抛 {@link ReportNotFoundException} → collector
 * 退化 placeholder。XML 解析使用 JAXP SAX，禁用外部实体解析以避免 XXE。
 *
 * <p>Covers: R14.1, R23.4（XML 解析安全）。
 */
@Component
public class CpdXmlParser {

    /** total-loc.txt 默认文件名，与 cpd.xml 同目录。 */
    public static final String TOTAL_LOC_FILE = "total-loc.txt";

    /**
     * 解析给定 XML 文件并返回重复率百分比（0..100，保留 2 位小数）。
     *
     * @throws ReportNotFoundException XML 或 total-loc.txt 文件不存在
     * @throws ReportParseException    XML 解析失败 / total-loc.txt 非整数 / 总 LOC ≤ 0
     */
    public BigDecimal parseDuplicateRatePercent(Path xmlPath) {
        if (xmlPath == null || !Files.exists(xmlPath)) {
            throw new ReportNotFoundException("cpd xml not found: " + xmlPath);
        }
        if (!Files.isRegularFile(xmlPath) || !Files.isReadable(xmlPath)) {
            throw new ReportParseException("cpd xml not readable: " + xmlPath);
        }
        Path locPath = xmlPath.resolveSibling(TOTAL_LOC_FILE);
        if (!Files.exists(locPath)) {
            throw new ReportNotFoundException("cpd total-loc.txt not found: " + locPath);
        }

        long totalLoc;
        try {
            String s = Files.readString(locPath, StandardCharsets.UTF_8).trim();
            totalLoc = Long.parseLong(s);
        } catch (IOException e) {
            throw new ReportParseException("read total-loc.txt failed: " + locPath, e);
        } catch (NumberFormatException e) {
            throw new ReportParseException("total-loc.txt not a valid integer: " + locPath, e);
        }
        if (totalLoc <= 0) {
            throw new ReportParseException("total-loc.txt non-positive: " + totalLoc);
        }

        DuplicationCounter counter = new DuplicationCounter();
        try (InputStream in = Files.newInputStream(xmlPath)) {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            // XXE hardening
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setNamespaceAware(false);
            SAXParser parser = factory.newSAXParser();
            parser.parse(in, counter);
        } catch (IOException | SAXException | javax.xml.parsers.ParserConfigurationException e) {
            throw new ReportParseException("parse cpd xml failed: " + xmlPath, e);
        }

        long duplicated = counter.duplicatedLines;
        BigDecimal pct = BigDecimal.valueOf(duplicated)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalLoc), 2, RoundingMode.HALF_UP);
        // clamp to [0,100] in pathological cases (lines counted twice etc.)
        if (pct.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (pct.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }
        return pct;
    }

    /** SAX 处理器：累加每条 duplication 的 {@code lines × files} 到总重复行数。 */
    private static final class DuplicationCounter extends DefaultHandler {
        long duplicatedLines = 0;
        private long currentLines = 0;
        private int currentFiles = 0;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) {
            if ("duplication".equalsIgnoreCase(qName)) {
                String lines = attrs.getValue("lines");
                try {
                    currentLines = (lines == null) ? 0 : Long.parseLong(lines);
                } catch (NumberFormatException e) {
                    currentLines = 0;
                }
                currentFiles = 0;
            } else if ("file".equalsIgnoreCase(qName)) {
                currentFiles++;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if ("duplication".equalsIgnoreCase(qName) && currentFiles > 0 && currentLines > 0) {
                duplicatedLines += currentLines * currentFiles;
                currentLines = 0;
                currentFiles = 0;
            }
        }
    }
}
