package com.acrqg.platform.webhook.parser;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.repository.domain.Provider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Webhook 事件解析器（B3-B.2）。
 *
 * <p>把三家代码托管平台（GitHub / GitLab / Gitee）下发的 webhook payload 统一
 * 解析成 {@link ParsedEvent}：
 *
 * <h3>GitHub</h3>
 * <ul>
 *   <li>事件类型由 {@code X-GitHub-Event} 决定：
 *     <ul>
 *       <li>{@code "ping"} → {@link ParsedEvent.EventType#PING}</li>
 *       <li>{@code "pull_request"}：进一步看 body {@code action} 字段
 *           （{@code "opened"} / {@code "reopened"} → PR_OPENED，
 *           {@code "synchronize"} → PR_SYNC，其他 → OTHER）</li>
 *       <li>{@code "push"} → PUSH</li>
 *       <li>其余 → OTHER</li>
 *     </ul>
 *   </li>
 *   <li>事件 id：{@code X-GitHub-Delivery}（UUID）；缺失退化为生成新 UUID。</li>
 *   <li>repositoryId：{@code repository.id}；repoUrl：{@code repository.html_url}。</li>
 *   <li>PR：{@code pull_request.number} → prId，
 *       {@code pull_request.head.sha} → commitSha，
 *       {@code pull_request.head.ref} → sourceBranch，
 *       {@code pull_request.base.ref} → targetBranch。</li>
 *   <li>push：顶层 {@code after} → commitSha，
 *       顶层 {@code ref}（{@code refs/heads/xxx}）去前缀 → sourceBranch。</li>
 * </ul>
 *
 * <h3>GitLab</h3>
 * <ul>
 *   <li>事件类型由 {@code X-Gitlab-Event} 决定：
 *     {@code "Merge Request Hook"} 进一步看 {@code object_attributes.action}
 *     （{@code "open"} / {@code "reopen"} → PR_OPENED，{@code "update"} → PR_SYNC，
 *     其余 → OTHER）；{@code "Push Hook"} → PUSH；{@code "System Hook"} 等 → OTHER。</li>
 *   <li>事件 id：{@code X-Gitlab-Event-UUID}（GitLab 12.3+）；缺失退化为新 UUID。</li>
 *   <li>repositoryId：{@code project.id}；repoUrl：
 *       {@code project.web_url}（缺失退化到 {@code project.git_http_url}）。</li>
 *   <li>MR：{@code object_attributes.iid} → prId，
 *       {@code object_attributes.last_commit.id} → commitSha，
 *       {@code object_attributes.source_branch / target_branch}。</li>
 * </ul>
 *
 * <h3>Gitee</h3>
 * <ul>
 *   <li>事件类型由 {@code X-Gitee-Event} 决定：
 *     {@code "Pull Request Hook"} 看 {@code action}
 *     （{@code "open"} → PR_OPENED，{@code "update"} → PR_SYNC，其余 → OTHER）；
 *     {@code "Push Hook"} → PUSH；其余 → OTHER。</li>
 *   <li>事件 id：{@code X-Gitee-Timestamp} 头 + body 的 SHA-256 短哈希；
 *     缺失时退化为 body 哈希；body 为空时退化为新 UUID。</li>
 *   <li>repositoryId：优先 {@code repository.id}，缺失则
 *     {@code repository.path_with_namespace}；repoUrl：{@code repository.html_url}。</li>
 *   <li>PR：{@code pull_request.number / head.sha / head.ref / base.ref}。</li>
 * </ul>
 *
 * <p>未知 / 无法解析的事件统一返回 {@code eventType=OTHER} 并尽量带上
 * 已知字段，让上层 {@code WebhookService} 短路返回 ignored 而不是 500。
 *
 * <p>Covers: R7.3, R7.5。
 */
@Component
public class WebhookEventParser {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventParser.class);

    private static final String HEADER_GITHUB_EVENT = "X-GitHub-Event";
    private static final String HEADER_GITHUB_DELIVERY = "X-GitHub-Delivery";
    private static final String HEADER_GITLAB_EVENT = "X-Gitlab-Event";
    private static final String HEADER_GITLAB_EVENT_UUID = "X-Gitlab-Event-UUID";
    private static final String HEADER_GITEE_EVENT = "X-Gitee-Event";
    private static final String HEADER_GITEE_TIMESTAMP = "X-Gitee-Timestamp";

    private final ObjectMapper objectMapper;

    public WebhookEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析单次 webhook 请求。
     *
     * @param provider 代码托管平台（由控制器根据头部推断）
     * @param rawBody  原始请求体；可能为空（PING 事件）
     * @param headers  请求头集合
     * @return 解析结果，{@code eventType=OTHER} 表示未识别但已成功返回（不是失败）
     * @throws BusinessException 解析过程出现严重 IO 错误（payload 不是合法 JSON 但
     *                           头部声称是 PR/MR 事件）时抛出
     *                           {@link ErrorCode#VALIDATION_ERROR}
     */
    public ParsedEvent parse(Provider provider, String rawBody, HttpHeaders headers) {
        if (provider == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "provider 不能为空");
        }
        HttpHeaders safeHeaders = headers == null ? new HttpHeaders() : headers;
        String body = rawBody == null ? "" : rawBody;
        return switch (provider) {
            case GITHUB -> parseGithub(body, safeHeaders);
            case GITLAB -> parseGitlab(body, safeHeaders);
            case GITEE -> parseGitee(body, safeHeaders);
        };
    }

    // ====================================================================
    // GitHub
    // ====================================================================

    private ParsedEvent parseGithub(String body, HttpHeaders headers) {
        String evt = headers.getFirst(HEADER_GITHUB_EVENT);
        String delivery = firstNonBlank(headers.getFirst(HEADER_GITHUB_DELIVERY), UUID.randomUUID().toString());

        if ("ping".equalsIgnoreCase(evt)) {
            JsonNode root = readTreeQuietly(body);
            String repoId = textOrNull(root, "repository", "id");
            String repoUrl = textOrNull(root, "repository", "html_url");
            return new ParsedEvent(Provider.GITHUB,
                    repoId, repoUrl, delivery, null, null, null, null,
                    ParsedEvent.EventType.PING);
        }

        JsonNode root = readTree(body, Provider.GITHUB);
        String repoId = textOrNull(root, "repository", "id");
        String repoUrl = textOrNull(root, "repository", "html_url");

        if ("pull_request".equalsIgnoreCase(evt)) {
            String action = textOrNull(root, "action");
            ParsedEvent.EventType type = mapPrAction(action,
                    "opened", "reopened", "synchronize");
            String prId = textOrNull(root, "pull_request", "number");
            String commitSha = textOrNull(root, "pull_request", "head", "sha");
            String src = textOrNull(root, "pull_request", "head", "ref");
            String tgt = textOrNull(root, "pull_request", "base", "ref");
            return new ParsedEvent(Provider.GITHUB,
                    repoId, repoUrl, delivery, prId, commitSha, src, tgt, type);
        }

        if ("push".equalsIgnoreCase(evt)) {
            String commitSha = textOrNull(root, "after");
            String ref = textOrNull(root, "ref");
            String src = stripRefsHeads(ref);
            return new ParsedEvent(Provider.GITHUB,
                    repoId, repoUrl, delivery, null, commitSha, src, null,
                    ParsedEvent.EventType.PUSH);
        }

        return new ParsedEvent(Provider.GITHUB,
                repoId, repoUrl, delivery, null, null, null, null,
                ParsedEvent.EventType.OTHER);
    }

    // ====================================================================
    // GitLab
    // ====================================================================

    private ParsedEvent parseGitlab(String body, HttpHeaders headers) {
        String evt = headers.getFirst(HEADER_GITLAB_EVENT);
        String delivery = firstNonBlank(headers.getFirst(HEADER_GITLAB_EVENT_UUID),
                UUID.randomUUID().toString());

        JsonNode root = readTree(body, Provider.GITLAB);
        String repoId = textOrNull(root, "project", "id");
        String repoUrl = firstNonNull(
                textOrNull(root, "project", "web_url"),
                textOrNull(root, "project", "git_http_url"),
                textOrNull(root, "repository", "homepage"));

        if ("Merge Request Hook".equalsIgnoreCase(evt)) {
            String action = textOrNull(root, "object_attributes", "action");
            ParsedEvent.EventType type = mapPrAction(action,
                    "open", "reopen", "update");
            String prId = textOrNull(root, "object_attributes", "iid");
            String commitSha = textOrNull(root, "object_attributes", "last_commit", "id");
            String src = textOrNull(root, "object_attributes", "source_branch");
            String tgt = textOrNull(root, "object_attributes", "target_branch");
            return new ParsedEvent(Provider.GITLAB,
                    repoId, repoUrl, delivery, prId, commitSha, src, tgt, type);
        }

        if ("Push Hook".equalsIgnoreCase(evt)) {
            String commitSha = textOrNull(root, "after");
            String ref = textOrNull(root, "ref");
            String src = stripRefsHeads(ref);
            return new ParsedEvent(Provider.GITLAB,
                    repoId, repoUrl, delivery, null, commitSha, src, null,
                    ParsedEvent.EventType.PUSH);
        }

        // GitLab 没有显式 ping，但前端一些"测试 webhook"功能会下发空体；
        // 这里仍然返回 OTHER 让上层短路。
        return new ParsedEvent(Provider.GITLAB,
                repoId, repoUrl, delivery, null, null, null, null,
                ParsedEvent.EventType.OTHER);
    }

    // ====================================================================
    // Gitee
    // ====================================================================

    private ParsedEvent parseGitee(String body, HttpHeaders headers) {
        String evt = headers.getFirst(HEADER_GITEE_EVENT);
        String timestamp = headers.getFirst(HEADER_GITEE_TIMESTAMP);
        String delivery = computeGiteeEventId(timestamp, body);

        JsonNode root = readTree(body, Provider.GITEE);
        String repoId = firstNonNull(
                textOrNull(root, "repository", "id"),
                textOrNull(root, "repository", "path_with_namespace"),
                textOrNull(root, "repository", "full_name"));
        String repoUrl = firstNonNull(
                textOrNull(root, "repository", "html_url"),
                textOrNull(root, "repository", "url"));

        if ("Pull Request Hook".equalsIgnoreCase(evt)) {
            String action = textOrNull(root, "action");
            ParsedEvent.EventType type = mapPrAction(action,
                    "open", "reopen", "update");
            String prId = textOrNull(root, "pull_request", "number");
            String commitSha = textOrNull(root, "pull_request", "head", "sha");
            String src = textOrNull(root, "pull_request", "head", "ref");
            String tgt = textOrNull(root, "pull_request", "base", "ref");
            return new ParsedEvent(Provider.GITEE,
                    repoId, repoUrl, delivery, prId, commitSha, src, tgt, type);
        }

        if ("Push Hook".equalsIgnoreCase(evt)) {
            String commitSha = textOrNull(root, "after");
            String ref = textOrNull(root, "ref");
            String src = stripRefsHeads(ref);
            return new ParsedEvent(Provider.GITEE,
                    repoId, repoUrl, delivery, null, commitSha, src, null,
                    ParsedEvent.EventType.PUSH);
        }

        return new ParsedEvent(Provider.GITEE,
                repoId, repoUrl, delivery, null, null, null, null,
                ParsedEvent.EventType.OTHER);
    }

    // ====================================================================
    // helpers
    // ====================================================================

    /**
     * 把 PR/MR action 映射到 {@link ParsedEvent.EventType}。
     *
     * @param action       上游 action 字段值
     * @param openValue    开启动作字面量（如 GitHub 的 {@code "opened"}，GitLab/Gitee 的 {@code "open"}）
     * @param reopenValue  重新开启字面量
     * @param updateValue  同步 / 推送字面量
     */
    private static ParsedEvent.EventType mapPrAction(String action,
                                                     String openValue,
                                                     String reopenValue,
                                                     String updateValue) {
        if (action == null) {
            return ParsedEvent.EventType.OTHER;
        }
        if (openValue.equalsIgnoreCase(action) || reopenValue.equalsIgnoreCase(action)) {
            return ParsedEvent.EventType.PR_OPENED;
        }
        if (updateValue.equalsIgnoreCase(action)) {
            return ParsedEvent.EventType.PR_SYNC;
        }
        return ParsedEvent.EventType.OTHER;
    }

    /** 计算 Gitee 事件 id：{@code timestamp + ":" + sha256(body)[0..16]}。 */
    private static String computeGiteeEventId(String timestamp, String body) {
        String hash = sha256ShortHex(body);
        if (timestamp != null && !timestamp.isBlank()) {
            return timestamp + ":" + hash;
        }
        if (hash != null) {
            return hash;
        }
        return UUID.randomUUID().toString();
    }

    /** 计算 body SHA-256 的前 16 个 hex 字符；body 为 {@code null} 时返回 {@code null}。 */
    private static String sha256ShortHex(String body) {
        if (body == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(body.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(digest);
            return hex.substring(0, Math.min(16, hex.length()));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JCE 必需算法，正常环境永远不会到达此分支
            return null;
        }
    }

    /** 去除 {@code refs/heads/} 前缀。 */
    private static String stripRefsHeads(String ref) {
        if (ref == null) {
            return null;
        }
        String prefix = "refs/heads/";
        if (ref.startsWith(prefix)) {
            return ref.substring(prefix.length());
        }
        return ref;
    }

    /**
     * 安全读取 JSON 字符串为 Jackson 树；非法 JSON 抛 {@link BusinessException}。
     *
     * <p>仅在事件确认是 PR/MR/PUSH 等需要 body 字段的类型时调用——PING 事件
     * 走 {@link #readTreeQuietly}。
     */
    private JsonNode readTree(String body, Provider provider) {
        try {
            if (body == null || body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (IOException e) {
            log.warn("webhook payload not valid JSON for provider {}: {}", provider, e.toString());
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "webhook payload is not valid JSON");
        }
    }

    /** 与 {@link #readTree} 相同但不抛异常；非法 JSON 返回空对象。 */
    private JsonNode readTreeQuietly(String body) {
        try {
            if (body == null || body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (IOException e) {
            return objectMapper.createObjectNode();
        }
    }

    /** 沿路径取出 text 或 numeric 字段；任意一段缺失返回 {@code null}。 */
    private static String textOrNull(JsonNode node, String... path) {
        JsonNode cur = node;
        for (String seg : path) {
            if (cur == null || cur.isNull() || cur.isMissingNode()) {
                return null;
            }
            cur = cur.get(seg);
        }
        if (cur == null || cur.isNull() || cur.isMissingNode()) {
            return null;
        }
        if (cur.isTextual()) {
            String s = cur.asText();
            return s == null || s.isEmpty() ? null : s;
        }
        if (cur.isNumber() || cur.isBoolean()) {
            return cur.asText();
        }
        return null;
    }

    private static String firstNonBlank(String first, String fallback) {
        return (first == null || first.isBlank()) ? fallback : first;
    }

    private static String firstNonNull(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
