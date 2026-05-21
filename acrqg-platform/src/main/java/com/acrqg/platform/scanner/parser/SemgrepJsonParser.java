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
 * Semgrep JSON 输出解析器。
 *
 * <p>Semgrep {@code --json} 输出形如：
 * <pre>{@code
 * {
 *   "results": [
 *     {
 *       "check_id": "python.lang.security.audit.eval-detected.eval-detected",
 *       "path": "app/main.py",
 *       "start": { "line": 10, "col": 1 },
 *       "end":   { "line": 10, "col": 16 },
 *       "extra": {
 *         "severity": "ERROR",
 *         "message": "Detected eval(); avoid using eval()",
 *         "fix": "Use ast.literal_eval() instead"
 *       }
 *     },
 *     ...
 *   ],
 *   "errors": [...],
 *   "version": "..."
 * }
 * }</pre>
 *
 * <p>{@code extra.fix} 在某些规则下存在，作为 {@code suggestion}。
 *
 * <p>Covers: R11.2, R11.3。
 */
@Component
public class SemgrepJsonParser implements ScanResultParser {

    private static final Logger log = LoggerFactory.getLogger(SemgrepJsonParser.class);

    @Override
    public String type() {
        return ResultParserType.SEMGREP_JSON;
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
        if (root == null || !root.isObject()) {
            log.debug("SemgrepJsonParser: root not object, skip");
            return List.of();
        }
        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return List.of();
        }
        List<CodeIssue> issues = new ArrayList<>();
        for (JsonNode item : results) {
            CodeIssue issue = toIssue(item);
            if (issue != null) {
                issues.add(issue);
            }
        }
        return issues;
    }

    private CodeIssue toIssue(JsonNode item) {
        String filePath = textOrNull(item.get("path"));
        String checkId = textOrNull(item.get("check_id"));
        JsonNode start = item.get("start");
        JsonNode extra = item.get("extra");
        if (filePath == null || extra == null || !extra.isObject()) {
            return null;
        }
        String message = textOrNull(extra.get("message"));
        String severityRaw = textOrNull(extra.get("severity"));
        if (message == null || severityRaw == null) {
            return null;
        }
        Severity severity = SeverityMapper.normalize(SeverityMapper.TOOL_SEMGREP, severityRaw);
        Integer lineNo = start != null ? intOrNull(start.get("line")) : null;
        String fix = textOrNull(extra.get("fix"));

        CodeIssue issue = new CodeIssue();
        issue.setFilePath(filePath);
        issue.setLineNo(lineNo);
        issue.setRuleCode(checkId == null ? null : "semgrep:" + checkId);
        issue.setSeverity(severity.name());
        issue.setSource(CodeIssueSource.SAST.name());
        issue.setStatus(CodeIssueStatus.NEW.name());
        issue.setDescription(message);
        issue.setSuggestion(fix);
        return issue;
    }

    private static String readPayload(ScannerOutput output) {
        Path file = output.outputFile();
        if (file != null && Files.exists(file)) {
            try {
                return Files.readString(file);
            } catch (IOException ex) {
                throw new RuntimeException("read semgrep output failed: " + file, ex);
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
