package com.acrqg.platform.ai.prompt;

import com.acrqg.platform.admin.domain.SystemParam;
import com.acrqg.platform.admin.repository.SystemParamMapper;
import com.acrqg.platform.ai.client.AiReviewRequest;
import com.acrqg.platform.ai.filter.AiReviewPayload;
import com.acrqg.platform.ai.filter.FilteredPayload;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 评审 Prompt 构造器（design.md §12.1）。
 *
 * <p>组装两段消息：
 * <ul>
 *   <li><b>system</b>：模型角色定义 + 必须输出严格 JSON 的硬约束；</li>
 *   <li><b>user</b>：项目语言、关注门禁指标、变更文件清单、已脱敏 diff 文本，
 *       以及对模型的输出要求（issues 0..50 条、severity 枚举、confidence 0..1）。</li>
 * </ul>
 *
 * <h3>输入截断</h3>
 * <p>把脱敏后的所有 patch 串接为一个文本块；当总长度超过系统参数
 * {@code ai.review.maxInputChars}（默认 30000）时进行<b>尾部截断</b>，并在末尾追加
 * {@code "...[TRUNCATED]"} 标记，避免模型在不知情情况下处理半截上下文。
 *
 * <p>{@code ai.review.maxInputChars} 由 system_param 表读取；缺失或越界时退化为
 * 默认值。截断阈值仅作用于 user 段中的 diff 块，system 段不受影响。
 *
 * <p>线程安全：本类无可变状态，从 {@link SystemParamMapper} 读取参数时为只读，
 * 可被 worker 线程池并发调用。
 *
 * <p>Covers: R12.1。
 */
@Component
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    /** system_param key：单次发送给 AI 的最大输入字符数。 */
    public static final String PARAM_MAX_INPUT_CHARS = "ai.review.maxInputChars";

    /** 默认上限（约相当于 GPT-4 mini 的 token 容量上限的安全空间）。 */
    public static final int DEFAULT_MAX_INPUT_CHARS = 30_000;

    /** 截断标记。 */
    public static final String TRUNCATE_MARKER = "...[TRUNCATED]";

    private final SystemParamMapper systemParamMapper;

    public PromptBuilder(SystemParamMapper systemParamMapper) {
        this.systemParamMapper = systemParamMapper;
    }

    /**
     * 构造 {@link AiReviewRequest}。
     *
     * @param filtered     已脱敏的载荷
     * @param baseUrl      模型基址（来自 {@code model_config.base_url}）
     * @param modelName    模型名称（来自 {@code model_config.name}）
     * @param timeoutSeconds 超时（来自 system_param {@code ai.review.timeout.seconds}）
     * @return 可直接喂给 {@link com.acrqg.platform.ai.client.AiReviewClient#review} 的请求
     */
    public AiReviewRequest build(FilteredPayload filtered,
                                  String baseUrl,
                                  String modelName,
                                  int timeoutSeconds) {
        if (filtered == null) {
            throw new IllegalArgumentException("filtered payload is required");
        }
        AiReviewPayload payload = filtered.payload();
        String language = payload.language() == null ? "" : payload.language();

        int maxChars = readMaxInputCharsOrDefault();

        String system = buildSystem(language);
        String user = buildUser(payload, maxChars);

        return new AiReviewRequest(system, user, baseUrl, modelName, timeoutSeconds);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    static String buildSystem(String language) {
        return "You are a senior code reviewer for " + safe(language) + " projects. "
                + "You MUST respond with VALID JSON ONLY, conforming to the provided JSON Schema. "
                + "Do not output anything outside the JSON. "
                + "The JSON object MUST have an \"issues\" array (0..50 items). "
                + "Each issue MUST include filePath, severity (CRITICAL|HIGH|MEDIUM|LOW|INFO), "
                + "description (>=5 chars), suggestion (>=5 chars) and confidence (0..1).";
    }

    /**
     * 构造 user 段。会把所有 file 的 patch 串接到 {@code <<<DIFF ... >>>DIFF} 块中，
     * 超过 {@code maxChars} 时尾部截断并追加 {@link #TRUNCATE_MARKER}。
     */
    static String buildUser(AiReviewPayload payload, int maxChars) {
        StringBuilder sb = new StringBuilder(2_048);
        sb.append("项目语言: ").append(safe(payload.language())).append('\n');
        sb.append("关注门禁指标: ").append(joinMetrics(payload.gateMetrics())).append('\n');
        sb.append("变更文件清单:\n").append(buildFileList(payload.files())).append('\n');
        sb.append('\n');
        sb.append("请基于以下变更代码进行评审，给出潜在问题、原因、修复建议、严重等级、置信度。\n");
        sb.append('\n');
        sb.append("变更代码片段（已脱敏）:\n");
        sb.append("<<<DIFF\n");

        String diff = buildDiff(payload.files());
        if (diff.length() > maxChars) {
            int keep = Math.max(0, maxChars - TRUNCATE_MARKER.length() - 1);
            sb.append(diff, 0, keep).append('\n').append(TRUNCATE_MARKER).append('\n');
        } else {
            sb.append(diff).append('\n');
        }
        sb.append(">>>DIFF\n");
        sb.append('\n');
        sb.append("请按 JSON Schema 输出：issues 至少 0 条至多 50 条，"
                + "severity 仅限 [CRITICAL, HIGH, MEDIUM, LOW, INFO]，confidence 取值 0~1。");
        return sb.toString();
    }

    private static String buildFileList(List<AiReviewPayload.FileEntry> files) {
        if (files.isEmpty()) {
            return "(无变更文件)";
        }
        List<String> lines = new ArrayList<>(files.size());
        for (AiReviewPayload.FileEntry f : files) {
            String patchInfo = (f.patch() == null || f.patch().isEmpty())
                    ? "(skipped)"
                    : (f.patch().length() + " chars");
            lines.add("- " + f.filePath() + " | " + patchInfo);
        }
        return String.join("\n", lines);
    }

    private static String buildDiff(List<AiReviewPayload.FileEntry> files) {
        if (files.isEmpty()) {
            return "(no diff)";
        }
        StringBuilder sb = new StringBuilder(8_192);
        for (AiReviewPayload.FileEntry f : files) {
            if (f.patch() == null || f.patch().isEmpty()) {
                continue;
            }
            sb.append("--- file: ").append(f.filePath()).append(" ---\n");
            sb.append(f.patch());
            if (!f.patch().endsWith("\n")) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String joinMetrics(List<String> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return "(默认全量)";
        }
        return String.join(", ", metrics);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    int readMaxInputCharsOrDefault() {
        try {
            SystemParam sp = systemParamMapper.selectByKey(PARAM_MAX_INPUT_CHARS);
            if (sp == null || sp.getParamValue() == null) {
                return DEFAULT_MAX_INPUT_CHARS;
            }
            int v = Integer.parseInt(sp.getParamValue().trim());
            if (v <= 0) {
                return DEFAULT_MAX_INPUT_CHARS;
            }
            return v;
        } catch (RuntimeException ex) {
            log.warn("readMaxInputCharsOrDefault fallback: {}", ex.toString());
            return DEFAULT_MAX_INPUT_CHARS;
        }
    }
}
