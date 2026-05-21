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
 * ESLint JSON 输出解析器。
 *
 * <p>ESLint {@code --format json} 输出形如：
 * <pre>{@code
 * [
 *   {
 *     "filePath": "/abs/path/file.js",
 *     "messages": [
 *       {
 *         "ruleId": "no-unused-vars",
 *         "severity": 1,
 *         "message": "'foo' is defined but never used.",
 *         "line": 12,
 *         "column": 5
 *       }
 *     ],
 *     ...
 *   },
 *   ...
 * ]
 * }</pre>
 *
 * <p>解析时缺 {@code message} / {@code severity} 的记录跳过，其他保留。
 * {@code ruleId} 可能为 {@code null}（如 ESLint 解析错误），此时 {@code ruleCode}
 * 字段填 {@code "eslint:syntax"}。
 *
 * <p>Covers: R11.2, R11.3。
 */
@Component
public class EsLintJsonParser implements ScanResultParser {

    private static final Logger log = LoggerFactory.getLogger(EsLintJsonParser.class);

    @Override
    public String type() {
        return ResultParserType.ESLINT_JSON;
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
            log.debug("EsLintJsonParser: root not array, skip");
            return List.of();
        }
        List<CodeIssue> issues = new ArrayList<>();
        for (JsonNode fileNode : root) {
            String filePath = textOrNull(fileNode.get("filePath"));
            JsonNode messages = fileNode.get("messages");
            if (filePath == null || messages == null || !messages.isArray()) {
                continue;
            }
            for (JsonNode msg : messages) {
                CodeIssue issue = toIssue(filePath, msg);
                if (issue != null) {
                    issues.add(issue);
                }
            }
        }
        return issues;
    }

    private CodeIssue toIssue(String filePath, JsonNode msg) {
        String message = textOrNull(msg.get("message"));
        if (message == null || message.isBlank()) {
            return null;
        }
        String severityRaw;
        JsonNode sev = msg.get("severity");
        if (sev == null || sev.isNull()) {
            return null;
        }
        if (sev.isNumber()) {
            severityRaw = String.valueOf(sev.intValue());
        } else {
            severityRaw = sev.asText();
        }
        Severity severity = SeverityMapper.normalize(SeverityMapper.TOOL_ESLINT, severityRaw);
        String ruleId = textOrNull(msg.get("ruleId"));
        Integer lineNo = intOrNull(msg.get("line"));

        CodeIssue issue = new CodeIssue();
        issue.setFilePath(filePath);
        issue.setLineNo(lineNo);
        issue.setRuleCode(ruleId == null ? "eslint:syntax" : "eslint:" + ruleId);
        issue.setSeverity(severity.name());
        issue.setSource(CodeIssueSource.SAST.name());
        issue.setStatus(CodeIssueStatus.NEW.name());
        issue.setDescription(message);
        // ESLint 输出可包含 fix.text，但常见且可用字段是 messageId/desc，仅保留 message 即可
        return issue;
    }

    private static String readPayload(ScannerOutput output) {
        Path file = output.outputFile();
        if (file != null && Files.exists(file)) {
            try {
                return Files.readString(file);
            } catch (IOException ex) {
                throw new RuntimeException("read eslint output failed: " + file, ex);
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
