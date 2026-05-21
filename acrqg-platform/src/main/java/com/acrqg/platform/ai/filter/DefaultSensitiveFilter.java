package com.acrqg.platform.ai.filter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link SensitiveFilter} 的默认实现（design.md §12.3 / R12.2 / R23.4）。
 *
 * <h3>三道闸</h3>
 * <ol>
 *   <li><b>路径白名单</b>：文件路径匹配以下任一规则即整文件跳过（patch 置空）：
 *     <ul>
 *       <li>{@code .env}（精确）、{@code *.env.*}（如 {@code .env.local}）；</li>
 *       <li>{@code *.pem}、{@code *.key}、{@code *.crt}、{@code *.p12}、
 *           {@code *.jks}（密钥 / 证书后缀）；</li>
 *       <li>{@code secrets/**}（任意层级 secrets 目录下文件）；</li>
 *       <li>{@code config/secret-*}（config 下以 secret- 开头的文件）。</li>
 *     </ul>
 *   </li>
 *   <li><b>Token 正则替换</b>：把以下模式替换为 {@code ***REDACTED***}：
 *     <ul>
 *       <li>AWS Access Key：{@code AKIA[0-9A-Z]{16}}</li>
 *       <li>OpenAI Key：{@code sk-[A-Za-z0-9]{32,}}</li>
 *       <li>GitHub Token：{@code gh[pousr]_[A-Za-z0-9]{36,}}</li>
 *       <li>通用密钥短语：
 *           {@code (?i)(password|secret|token|api[_-]?key)\s*[:=]\s*['"][^'"]+['"]}</li>
 *     </ul>
 *   </li>
 *   <li><b>哈希前后比对</b>：原始 patch 串接体与脱敏后串接体的 SHA-256；
 *       命中至少一条过滤规则但哈希未变化时抛
 *       {@link SensitiveFilterFailureException}。</li>
 * </ol>
 *
 * <h3>线程安全</h3>
 * <p>所有正则模式与路径模式在静态初始化时编译一次；{@link #filter(AiReviewPayload)}
 * 不持有可变状态，可被 worker 线程池并发调用。
 *
 * <p>Covers: R12.2, R23.4。
 */
@Component
public class DefaultSensitiveFilter implements SensitiveFilter {

    private static final Logger log = LoggerFactory.getLogger(DefaultSensitiveFilter.class);

    /** 替换字面量。 */
    public static final String REDACTED = "***REDACTED***";

    // ---------------------------------------------------------------------
    // Token 正则集合（按可识别性从强到弱）
    // ---------------------------------------------------------------------

    /** AWS Access Key ID：{@code AKIA} + 16 位大写字母数字。 */
    public static final Pattern P_AWS = Pattern.compile("AKIA[0-9A-Z]{16}");

    /** OpenAI / 类似 API Key：{@code sk-} + 32+ 位字母数字。 */
    public static final Pattern P_OPENAI = Pattern.compile("sk-[A-Za-z0-9]{32,}");

    /** GitHub Token：{@code gh[pousr]_} + 36+ 位字母数字。 */
    public static final Pattern P_GITHUB = Pattern.compile("gh[pousr]_[A-Za-z0-9]{36,}");

    /** 通用密钥短语：{@code password/secret/token/api_key = "..."}。 */
    public static final Pattern P_GENERIC = Pattern.compile(
            "(?i)(password|secret|token|api[_-]?key)\\s*[:=]\\s*['\"][^'\"]+['\"]");

    /** 公开常量列表，便于属性测试 / 单测共享同一份模式集合。 */
    public static final List<Pattern> TOKEN_PATTERNS = List.of(P_AWS, P_OPENAI, P_GITHUB, P_GENERIC);

    // ---------------------------------------------------------------------
    // 路径白名单（编译为正则）
    //   * → 单段（不含 /）任意字符；
    //   ** → 跨段（含 /）任意字符。
    //   实现使用预定义正则即可，无需通用 glob 引擎。
    // ---------------------------------------------------------------------

    /** 与路径匹配（大小写不敏感）即整文件跳过。 */
    public static final List<Pattern> PATH_WHITELIST = List.of(
            // .env 或路径中以 /.env 结尾
            Pattern.compile("(?i)(^|.*/)\\.env$"),
            // *.env.*  例如 .env.local / app.env.production
            Pattern.compile("(?i)(^|.*/)[^/]*\\.env\\.[^/]+$"),
            // 后缀型密钥 / 证书：*.pem / *.key / *.crt / *.p12 / *.jks
            Pattern.compile("(?i)(^|.*/)[^/]+\\.(pem|key|crt|p12|jks)$"),
            // secrets/** 任意层级
            Pattern.compile("(?i)(^|.*/)secrets/.+$"),
            // config/secret-* 任意文件名
            Pattern.compile("(?i)(^|.*/)config/secret-[^/]+$")
    );

    @Override
    public FilteredPayload filter(AiReviewPayload raw) {
        if (raw == null) {
            throw new IllegalArgumentException("raw is required");
        }

        List<AiReviewPayload.FileEntry> files = raw.files();

        // 第一阶段：构造脱敏后的 file 列表，统计是否命中。
        boolean pathHit = false;
        boolean regexHit = false;
        List<AiReviewPayload.FileEntry> filtered = new ArrayList<>(files.size());
        for (AiReviewPayload.FileEntry f : files) {
            if (matchesPathWhitelist(f.filePath())) {
                pathHit = true;
                // 整文件跳过：保留路径占位（便于 prompt 标注），patch 置空
                filtered.add(new AiReviewPayload.FileEntry(f.filePath(), "", f.oversized()));
                continue;
            }
            String original = f.patch();
            String redacted = redactText(original);
            if (!redacted.equals(original)) {
                regexHit = true;
            }
            filtered.add(new AiReviewPayload.FileEntry(f.filePath(), redacted, f.oversized()));
        }
        boolean wasFiltered = pathHit || regexHit;

        AiReviewPayload filteredPayload = new AiReviewPayload(
                raw.taskId(), raw.language(), raw.gateMetrics(), filtered);

        // 第二阶段：哈希前后比对（仅在 wasFiltered 时检查）。
        if (wasFiltered) {
            String hashRaw = hash(raw);
            String hashFiltered = hash(filteredPayload);
            if (hashRaw.equals(hashFiltered)) {
                log.error("SensitiveFilter post-redaction hash unchanged: taskId={} pathHit={} regexHit={}",
                        raw.taskId(), pathHit, regexHit);
                throw new SensitiveFilterFailureException(
                        "filter expected to mutate payload but hash unchanged (taskId="
                                + raw.taskId() + ")");
            }
        }

        return new FilteredPayload(filteredPayload, wasFiltered);
    }

    // ---------------------------------------------------------------------
    // 工具方法（包级可见，便于属性测试 / 单测调用）
    // ---------------------------------------------------------------------

    /** 路径是否命中白名单（任意 PATH_WHITELIST 匹配即返回 true）。 */
    public static boolean matchesPathWhitelist(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        // 标准化分隔符为 /，避免 Windows 反斜杠
        String normalized = path.replace('\\', '/');
        for (Pattern p : PATH_WHITELIST) {
            if (p.matcher(normalized).matches()) {
                return true;
            }
        }
        return false;
    }

    /** 把所有 Token 正则替换为 {@link #REDACTED}；不可变操作，原 text 不变。 */
    public static String redactText(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String result = text;
        for (Pattern p : TOKEN_PATTERNS) {
            Matcher m = p.matcher(result);
            if (m.find()) {
                result = m.replaceAll(Matcher.quoteReplacement(REDACTED));
            }
        }
        return result;
    }

    /** 任一文件命中路径白名单或正则匹配；用于早期诊断。 */
    public static boolean anyHit(AiReviewPayload raw) {
        if (raw == null || raw.files() == null) {
            return false;
        }
        for (AiReviewPayload.FileEntry f : raw.files()) {
            if (matchesPathWhitelist(f.filePath())) {
                return true;
            }
            if (containsTokenPattern(f.patch())) {
                return true;
            }
        }
        return false;
    }

    /** {@link #TOKEN_PATTERNS} 任一在 text 中匹配。 */
    public static boolean containsTokenPattern(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (Pattern p : TOKEN_PATTERNS) {
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算 payload 的稳定 SHA-256 哈希：把所有文件的 (路径 + "\n" + patch + "\n")
     * 串接后取摘要。同一组 (path, patch) 在同一顺序下产生同一哈希。
     */
    public static String hash(AiReviewPayload payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (AiReviewPayload.FileEntry f : payload.files()) {
                md.update(f.filePath().getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
                md.update(f.patch().getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 在 JDK 标准库中必然存在；此分支理论上不可达
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
