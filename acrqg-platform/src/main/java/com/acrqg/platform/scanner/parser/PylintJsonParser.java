package com.acrqg.platform.scanner.parser;

import com.acrqg.platform.code_issue.domain.CodeIssue;
import com.acrqg.platform.code_issue.domain.CodeIssueSource;
import com.acrqg.platform.code_issue.domain.CodeIssueStatus;
import com.acrqg.platform.code_issue.domain.Severity;
import com.acrqg.platform.common.util.JsonUtils;
import com.acrqg.platform.scanner.SeverityMapper;
import com.acrqg.platform.scanner.process.ScannerOutput;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pylint JSON 输出解析器。
 *
 * <p>Pylint {@code --output-format=json} 输出形如：
 * <pre>{@code
 * [
 *   {
 *     "type": "convention",
 *     "module": "foo.bar",
 *     "obj": "Bar",
 *     "line": 5,
 *     "column": 0,
 *     "path": "foo/bar.py",
 *     "symbol": "missing-class-docstring",
 *     "message": "Missing class docstring",
 *     "message-id": "C0115"
 *   },
 *   ...
 * ]
 * }</pre>
 *
 * <p>{@code message-id} 优先作为 {@code ruleCode}（如 {@code pylint:C0115}）；
 * 缺失时退回 {@code symbol}（如 {@code pylint:missing-class-docstring}）。
 *
 * <p>Covers: R11.2, R11.3。
 */
@Component
public class PylintJsonParser implements ScanResultParser {

    private static final Logger log = LoggerFactory.getLogger(PylintJsonParser.class);

    @Override
    public String type() {
        return ResultParserType.PYLINT_JSON;
    }

    @Override
    public List<CodeIssue> parse(ScannerOutput output) {
        if (output == null) {
            return List.of();
        }
        String content = readPayload(output);
        if (content == null || content.isBlank()) {
            return List.of();
        }
        JsonNode root = JsonUtils.tree(content);
        if (root == null || !root.isArray()) {
            log.debug("PylintJsonParser: root not array, skip");
            return List.of();
        }
        List<CodeIssue> issues = new ArrayList<>();
        for (JsonNode item : root) {
            CodeIssue issue = toIssue(item);
            if (issue != null) {
                issues.add(issue);
            }
        }
        return issues;
    }

    private CodeIssue toIssue(JsonNode item) {
        String filePath = textOrNull(item.get("path"));
        String message = textOrNull(item.get("message"));
        String typeRaw = textOrNull(item.get("type"));
        if (filePath == null || message == null || typeRaw == null) {
            return null;
        }
        Severity severity = SeverityMapper.normalize(SeverityMapper.TOOL_PYLINT, typeRaw);
        Integer lineNo = intOrNull(item.get("line"));
        String messageId = textOrNull(item.get("message-id"));
        String symbol = textOrNull(item.get("symbol"));
        String ruleCode;
        if (messageId != null && !messageId.isBlank()) {
            ruleCode = "pylint:" + messageId;
        } else if (symbol != null && !symbol.isBlank()) {
            ruleCode = "pylint:" + symbol;
        } else {
            ruleCode = "pylint:" + typeRaw;
        }

        CodeIssue issue = new CodeIssue();
        issue.setFilePath(filePath);
        issue.setLineNo(lineNo);
        issue.setRuleCode(ruleCode);
        issue.setSeverity(severity.name());
        issue.setSource(CodeIssueSource.SAST.name());
        issue.setStatus(CodeIssueStatus.NEW.name());
        issue.setDescription(message);
        return issue;
    }

    private static String readPayload(ScannerOutput output) {
        Path file = output.outputFile();
        if (file != null && Files.exists(file)) {
            try {
                return Files.readString(file);
            } catch (IOException ex) {
                throw new RuntimeException("read pylint output failed: " + file, ex);
            }
        }
        return output.stdout();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private static Integer intOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.canConvertToInt()) {
            return null;
        }
        return node.asInt();
    }
}
