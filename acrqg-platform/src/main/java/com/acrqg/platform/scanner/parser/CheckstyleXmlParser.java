package com.acrqg.platform.scanner.parser;

import com.acrqg.platform.code_issue.domain.CodeIssue;
import com.acrqg.platform.code_issue.domain.CodeIssueSource;
import com.acrqg.platform.code_issue.domain.CodeIssueStatus;
import com.acrqg.platform.code_issue.domain.Severity;
import com.acrqg.platform.scanner.SeverityMapper;
import com.acrqg.platform.scanner.process.ScannerOutput;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Checkstyle XML 输出解析器。
 *
 * <p>预期格式：
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <checkstyle version="...">
 *   <file name="/path/to/Foo.java">
 *     <error line="12" column="5" severity="warning"
 *            message="Line is longer than 80 characters" source="com.puppycrawl.tools.checkstyle.checks.LineLength"/>
 *     ...
 *   </file>
 *   ...
 * </checkstyle>
 * }</pre>
 *
 * <p>缺失关键字段（{@code line} / {@code severity} / {@code message}）时跳过该
 * {@code <error>}，但其他记录继续解析。XML 结构整体非法时抛
 * {@link RuntimeException}（由 Adapter 包装为 ScannerProcessException）。
 *
 * <p>Covers: R11.2, R11.3。
 */
@Component
public class CheckstyleXmlParser implements ScanResultParser {

    private static final Logger log = LoggerFactory.getLogger(CheckstyleXmlParser.class);

    @Override
    public String type() {
        return ResultParserType.CHECKSTYLE_XML;
    }

    @Override
    public List<CodeIssue> parse(ScannerOutput output) {
        if (output == null) {
            return List.of();
        }
        byte[] bytes = readPayload(output);
        if (bytes == null || bytes.length == 0) {
            return List.of();
        }
        Document doc = parseXml(bytes);
        NodeList fileNodes = doc.getElementsByTagName("file");
        List<CodeIssue> issues = new ArrayList<>();
        for (int i = 0; i < fileNodes.getLength(); i++) {
            Element fileEl = (Element) fileNodes.item(i);
            String filePath = fileEl.getAttribute("name");
            NodeList errors = fileEl.getElementsByTagName("error");
            for (int j = 0; j < errors.getLength(); j++) {
                Element err = (Element) errors.item(j);
                CodeIssue issue = toIssue(filePath, err);
                if (issue != null) {
                    issues.add(issue);
                }
            }
        }
        return issues;
    }

    private CodeIssue toIssue(String filePath, Element err) {
        String severityRaw = err.getAttribute("severity");
        String message = err.getAttribute("message");
        String source = err.getAttribute("source");
        String lineStr = err.getAttribute("line");
        if (severityRaw == null || severityRaw.isBlank()
                || message == null || message.isBlank()) {
            log.debug("CheckstyleXmlParser: skip incomplete error in {}", filePath);
            return null;
        }
        Severity severity = SeverityMapper.normalize(SeverityMapper.TOOL_CHECKSTYLE, severityRaw);
        Integer lineNo = parseInt(lineStr);
        CodeIssue issue = new CodeIssue();
        issue.setFilePath(filePath);
        issue.setLineNo(lineNo);
        issue.setRuleCode(source == null || source.isBlank() ? null : source);
        issue.setSeverity(severity.name());
        issue.setSource(CodeIssueSource.SAST.name());
        issue.setStatus(CodeIssueStatus.NEW.name());
        issue.setDescription(message);
        return issue;
    }

    private static byte[] readPayload(ScannerOutput output) {
        Path file = output.outputFile();
        if (file != null && Files.exists(file)) {
            try {
                return Files.readAllBytes(file);
            } catch (IOException ex) {
                throw new RuntimeException("read checkstyle output failed: " + file, ex);
            }
        }
        String stdout = output.stdout();
        return stdout == null ? new byte[0] : stdout.getBytes(StandardCharsets.UTF_8);
    }

    private static Document parseXml(byte[] bytes) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // disable external entities for safety
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                return db.parse(in);
            }
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new RuntimeException("parse checkstyle xml failed: " + ex.getMessage(), ex);
        }
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
