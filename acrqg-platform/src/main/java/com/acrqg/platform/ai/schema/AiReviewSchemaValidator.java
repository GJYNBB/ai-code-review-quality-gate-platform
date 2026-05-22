package com.acrqg.platform.ai.schema;

import com.acrqg.platform.ai.client.AiIssue;
import com.acrqg.platform.ai.client.AiReviewResponse;
import com.acrqg.platform.ai.exception.SchemaValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * AI 响应 JSON Schema 校验器（design.md §12.2）。
 *
 * <p>由于工程默认依赖中未包含通用 JSON Schema 引擎，这里采用"<b>手写校验</b>"方式实现
 * 与 schema 等价的检查，避免新增第三方依赖。校验内容：
 * <ul>
 *   <li>响应根节点必须是 JSON object；</li>
 *   <li>必有 {@code issues}（数组，最多 50 条）；</li>
 *   <li>每条 issue：
 *     <ul>
 *       <li>{@code filePath}（string，长度 ≥ 1, ≤ 512）必填；</li>
 *       <li>{@code lineNo}（integer，{@code [0, 1000000]}）可选；</li>
 *       <li>{@code severity}（string，枚举 {@code CRITICAL/HIGH/MEDIUM/LOW/INFO}）必填；</li>
 *       <li>{@code ruleCode}（string，长度 ≤ 128）可选；</li>
 *       <li>{@code description}（string，5..4000）必填；</li>
 *       <li>{@code suggestion}（string，5..4000）必填；</li>
 *       <li>{@code confidence}（number，{@code [0, 1]}）必填。</li>
 *     </ul>
 *   </li>
 *   <li>{@code summary}（string，≤ 2000）可选。</li>
 * </ul>
 *
 * <p>校验失败时抛 {@link SchemaValidationException}；{@code violations} 字段
 * 包含全部命中错误（首次发现的多条都返回，便于排障）。
 *
 * <p>本类不做反序列化失败的兜底（JSON 文本格式错误本身在调用方
 * {@link com.fasterxml.jackson.databind.ObjectMapper#readTree} 阶段抛出）；
 * 调用方应先把 JSON 文本解析为 {@link JsonNode} 再喂入本类。
 *
 * <p>Covers: R12.3, R12.4。
 */
@Component
public class AiReviewSchemaValidator {

    /** 严重等级枚举。 */
    public static final Set<String> SEVERITIES =
            Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO");

    /** issues 数组最大长度（与 schema 一致）。 */
    public static final int MAX_ISSUES = 50;
    /** filePath 长度上限。 */
    public static final int MAX_FILE_PATH = 512;
    /** lineNo 上限。 */
    public static final int MAX_LINE_NO = 1_000_000;
    /** ruleCode 长度上限。 */
    public static final int MAX_RULE_CODE = 128;
    /** description / suggestion 长度下/上限。 */
    public static final int MIN_TEXT = 5;
    public static final int MAX_TEXT = 4_000;
    /** summary 长度上限。 */
    public static final int MAX_SUMMARY = 2_000;

    private final ObjectMapper objectMapper;

    public AiReviewSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 校验已解析的 JSON 树。
     *
     * @param root 根节点（必须是 ObjectNode）
     * @return 校验通过后构造的 {@link AiReviewResponse}（issues 列表已就位）
     * @throws SchemaValidationException 校验失败
     */
    public AiReviewResponse validate(JsonNode root) {
        List<String> violations = new ArrayList<>();

        if (root == null || !root.isObject()) {
            throw new SchemaValidationException(
                    "response root must be a JSON object",
                    List.of("$: not an object"));
        }

        // summary（可选）
        String summary = null;
        JsonNode summaryNode = root.get("summary");
        if (summaryNode != null && !summaryNode.isNull()) {
            if (!summaryNode.isTextual()) {
                violations.add("$.summary: must be string");
            } else if (summaryNode.asText().length() > MAX_SUMMARY) {
                violations.add("$.summary: length > " + MAX_SUMMARY);
            } else {
                summary = summaryNode.asText();
            }
        }

        // issues（必填，数组，长度 ≤ MAX_ISSUES）
        JsonNode issuesNode = root.get("issues");
        if (issuesNode == null) {
            violations.add("$.issues: required");
        } else if (!issuesNode.isArray()) {
            violations.add("$.issues: must be array");
        } else if (issuesNode.size() > MAX_ISSUES) {
            violations.add("$.issues: length > " + MAX_ISSUES);
        }

        List<AiIssue> issues = new ArrayList<>();
        if (issuesNode != null && issuesNode.isArray()) {
            for (int i = 0; i < issuesNode.size() && i < MAX_ISSUES; i++) {
                JsonNode item = issuesNode.get(i);
                AiIssue parsed = validateIssue(i, item, violations);
                if (parsed != null) {
                    issues.add(parsed);
                }
            }
        }

        if (!violations.isEmpty()) {
            // 去重，保持稳定顺序
            Set<String> uniq = new LinkedHashSet<>(violations);
            throw new SchemaValidationException(
                    "AI response failed schema validation: " + uniq.size() + " violation(s)",
                    List.copyOf(uniq));
        }

        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            // root 由 ObjectMapper 解析得到，重新序列化通常不会失败；兜底返回 null
            rawJson = null;
        }
        return new AiReviewResponse(summary, issues, rawJson);
    }

    private AiIssue validateIssue(int idx, JsonNode item, List<String> violations) {
        String prefix = "$.issues[" + idx + "]";
        if (item == null || !item.isObject()) {
            violations.add(prefix + ": must be object");
            return null;
        }

        // filePath
        String filePath = null;
        JsonNode fpNode = item.get("filePath");
        if (fpNode == null || fpNode.isNull() || !fpNode.isTextual() || fpNode.asText().isEmpty()) {
            violations.add(prefix + ".filePath: required string");
        } else if (fpNode.asText().length() > MAX_FILE_PATH) {
            violations.add(prefix + ".filePath: length > " + MAX_FILE_PATH);
        } else {
            filePath = fpNode.asText();
        }

        // lineNo (optional)
        Integer lineNo = null;
        JsonNode lnNode = item.get("lineNo");
        if (lnNode != null && !lnNode.isNull()) {
            if (!lnNode.isIntegralNumber()) {
                violations.add(prefix + ".lineNo: must be integer");
            } else {
                long v = lnNode.asLong();
                if (v < 0 || v > MAX_LINE_NO) {
                    violations.add(prefix + ".lineNo: out of [0, " + MAX_LINE_NO + "]");
                } else {
                    lineNo = (int) v;
                }
            }
        }

        // severity
        String severity = null;
        JsonNode sevNode = item.get("severity");
        if (sevNode == null || sevNode.isNull() || !sevNode.isTextual()) {
            violations.add(prefix + ".severity: required string");
        } else {
            String s = sevNode.asText();
            if (!SEVERITIES.contains(s)) {
                violations.add(prefix + ".severity: not in " + SEVERITIES);
            } else {
                severity = s;
            }
        }

        // ruleCode (optional)
        String ruleCode = null;
        JsonNode rcNode = item.get("ruleCode");
        if (rcNode != null && !rcNode.isNull()) {
            if (!rcNode.isTextual()) {
                violations.add(prefix + ".ruleCode: must be string");
            } else if (rcNode.asText().length() > MAX_RULE_CODE) {
                violations.add(prefix + ".ruleCode: length > " + MAX_RULE_CODE);
            } else {
                ruleCode = rcNode.asText();
            }
        }

        // description
        String description = null;
        JsonNode descNode = item.get("description");
        if (descNode == null || descNode.isNull() || !descNode.isTextual()) {
            violations.add(prefix + ".description: required string");
        } else {
            String s = descNode.asText();
            if (s.length() < MIN_TEXT) {
                violations.add(prefix + ".description: length < " + MIN_TEXT);
            } else if (s.length() > MAX_TEXT) {
                violations.add(prefix + ".description: length > " + MAX_TEXT);
            } else {
                description = s;
            }
        }

        // suggestion
        String suggestion = null;
        JsonNode sugNode = item.get("suggestion");
        if (sugNode == null || sugNode.isNull() || !sugNode.isTextual()) {
            violations.add(prefix + ".suggestion: required string");
        } else {
            String s = sugNode.asText();
            if (s.length() < MIN_TEXT) {
                violations.add(prefix + ".suggestion: length < " + MIN_TEXT);
            } else if (s.length() > MAX_TEXT) {
                violations.add(prefix + ".suggestion: length > " + MAX_TEXT);
            } else {
                suggestion = s;
            }
        }

        // confidence
        Double confidence = null;
        JsonNode confNode = item.get("confidence");
        if (confNode == null || confNode.isNull()) {
            violations.add(prefix + ".confidence: required number");
        } else if (!confNode.isNumber()) {
            violations.add(prefix + ".confidence: must be number");
        } else {
            double v = confNode.asDouble();
            if (v < 0.0 || v > 1.0) {
                violations.add(prefix + ".confidence: out of [0, 1]");
            } else {
                confidence = v;
            }
        }

        // 即便部分字段非法，仍构造一个 issue 占位以便上层在去重错误信息后报告全部问题；
        // 但对外 throw 时不返回包含 null 必填字段的 issue。
        if (filePath == null || severity == null || description == null
                || suggestion == null || confidence == null) {
            return null;
        }
        return new AiIssue(filePath, lineNo, severity, ruleCode, description, suggestion, confidence);
    }
}
