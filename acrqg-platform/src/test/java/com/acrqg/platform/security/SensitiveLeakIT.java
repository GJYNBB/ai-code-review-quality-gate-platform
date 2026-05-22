package com.acrqg.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.acrqg.platform.infra.security.JwtTokenProvider;
import com.acrqg.platform.support.PostgresRedisTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 敏感字段泄露探测集成测试（B6-A.4）。
 *
 * <p>遍历控制器关键 GET endpoint，断言响应 JSON body 中：
 * <ol>
 *   <li>命中敏感字段名 {@code password / passwordHash / accessToken / apiKey /
 *       webhookSecret / accessTokenEncrypted / apiKeyEncrypted} 时，对应字段值必须为
 *       {@code "****"}（{@link com.acrqg.platform.common.util.MaskUtils#FULL_MASK}）；</li>
 *   <li><b>任何</b>字段值不得匹配 token 正则
 *       {@code (AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9]{32,}|gh[pousr]_[A-Za-z0-9]{36,})}。</li>
 * </ol>
 *
 * <p>覆盖 endpoints：
 * <ul>
 *   <li>{@code GET /api/v1/projects/{id}/repository}（仓库绑定）；</li>
 *   <li>{@code GET /api/v1/admin/model-configs}（AI 模型配置）；</li>
 *   <li>{@code GET /api/v1/admin/scanners}（扫描器列表）；</li>
 *   <li>{@code GET /api/v1/users}（用户列表）；</li>
 *   <li>{@code GET /api/v1/admin/audit-logs}（审计日志列表）。</li>
 * </ul>
 *
 * <p>Covers: R23.2, R23.3, R23.5。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test"})
@TestInstance(Lifecycle.PER_CLASS)
class SensitiveLeakIT extends PostgresRedisTestBase {

    /** Token 正则：AKIA / sk-... / gh{p|o|u|s|r}_... */
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9]{32,}|gh[pousr]_[A-Za-z0-9]{36,})");

    /** 命中即必须为 "****" 的敏感字段名（不区分大小写）。 */
    private static final Set<String> SENSITIVE_KEYS_LOWER = Set.of(
            "password",
            "passwordhash",
            "accesstoken",
            "apikey",
            "webhooksecret",
            "accesstokenencrypted",
            "apikeyencrypted");

    private static final String FULL_MASK = "****";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DataSource dataSource;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    long adminUserId;
    long projectId;

    @BeforeAll
    void seed() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM \"user\" WHERE username = 'admin'", Long.class);
        this.adminUserId = adminId == null ? 0L : adminId;

        // 创建项目 + admin 加为 PROJECT_ADMIN（admin 既是 SYSTEM_ADMIN 也加项目内权限）
        Long pid = jdbc.queryForObject(
                "SELECT id FROM project WHERE name = 'proj-it-leak'", Long.class);
        if (pid == null) {
            jdbc.update("INSERT INTO project(name, default_branch, language, created_by) "
                    + "VALUES ('proj-it-leak','main','Java', ?)", this.adminUserId);
            pid = jdbc.queryForObject(
                    "SELECT id FROM project WHERE name = 'proj-it-leak'", Long.class);
        }
        this.projectId = pid == null ? 0L : pid;
        Integer adminMember = jdbc.queryForObject(
                "SELECT COUNT(1) FROM project_member WHERE project_id=? AND user_id=?",
                Integer.class, this.projectId, this.adminUserId);
        if (adminMember == null || adminMember == 0) {
            jdbc.update("INSERT INTO project_member(project_id, user_id, project_role) "
                    + "VALUES (?, ?, 'PROJECT_ADMIN')", this.projectId, this.adminUserId);
        }

        // 仓库绑定 fixture：写入"明文 token"无关，因为 service 层永远不会把这些列
        // 暴露给 DTO；但我们需要表里有数据让 GET 接口返回非空 body。
        Integer rb = jdbc.queryForObject(
                "SELECT COUNT(1) FROM repository_binding WHERE project_id=?",
                Integer.class, this.projectId);
        if (rb == null || rb == 0) {
            // 写入 *_encrypted 列（design.md §7.2）；直接写假 base64 表示已加密
            jdbc.update("""
                    INSERT INTO repository_binding
                       (project_id, provider, repo_url, access_token_encrypted,
                        webhook_secret_encrypted, webhook_url, status, created_at, updated_at)
                    VALUES (?, 'GITHUB', 'https://github.com/acrqg/sample.git',
                            'BASE64_CIPHER_PAYLOAD_ACCESS', 'BASE64_CIPHER_PAYLOAD_HOOK',
                            'https://it.local/api/v1/webhooks/git', 'ACTIVE', NOW(), NOW())
                    """, this.projectId);
        }

        // 模型配置 fixture：apiKey 加密落库，name="leak-model"
        Integer mc = jdbc.queryForObject(
                "SELECT COUNT(1) FROM model_config WHERE name = 'leak-model'", Integer.class);
        if (mc == null || mc == 0) {
            jdbc.update("""
                    INSERT INTO model_config
                       (name, base_url, api_key_encrypted, timeout_seconds, enabled,
                        created_at, updated_at)
                    VALUES ('leak-model', 'https://api.openai.com',
                            'BASE64_CIPHER_PAYLOAD_APIKEY', 60, FALSE, NOW(), NOW())
                    """);
        }
    }

    private String adminToken() {
        return jwtTokenProvider.issueAccessToken(
                adminUserId, "admin", List.of("SYSTEM_ADMIN"));
    }

    @Test
    void getRepositoryBinding_doesNotLeakSecrets() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/" + projectId + "/repository")
                        .header("Authorization", "Bearer " + adminToken()))
                .andReturn();
        assertNoLeak("/projects/{id}/repository", result);
    }

    @Test
    void listModelConfigs_doesNotLeakSecrets() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/model-configs")
                        .header("Authorization", "Bearer " + adminToken()))
                .andReturn();
        assertNoLeak("/admin/model-configs", result);
    }

    @Test
    void listScanners_doesNotLeakSecrets() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/scanners")
                        .header("Authorization", "Bearer " + adminToken()))
                .andReturn();
        assertNoLeak("/admin/scanners", result);
    }

    @Test
    void listUsers_doesNotLeakSecrets() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken()))
                .andReturn();
        assertNoLeak("/users", result);
    }

    @Test
    void listAuditLogs_doesNotLeakSecrets() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken()))
                .andReturn();
        assertNoLeak("/admin/audit-logs", result);
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private void assertNoLeak(String label, MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 任何 200 / 4xx / 5xx 都按响应主体扫描；endpoint 鉴权失败时 body 不应包含敏感
        // 数据，断言仍然成立。
        if (body == null || body.isEmpty()) {
            return;
        }
        JsonNode root = objectMapper.readTree(body);
        Set<String> violations = new LinkedHashSet<>();
        scan(root, "$", violations);
        assertThat(violations)
                .as("sensitive leak detected at %s; body=%s", label, truncate(body, 1024))
                .isEmpty();
    }

    /** 深度优先遍历 JSON 树，命中规则即追加 violation。 */
    private void scan(JsonNode node, String path, Set<String> violations) {
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(node, path));
        while (!stack.isEmpty()) {
            Frame f = stack.pop();
            JsonNode n = f.node;
            String p = f.path;
            if (n == null || n.isNull()) {
                continue;
            }
            if (n.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = n.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    String childPath = p + "." + e.getKey();
                    if (isSensitiveKey(e.getKey()) && !isMaskedScalar(e.getValue())) {
                        violations.add("sensitive field '" + e.getKey()
                                + "' is not masked at " + childPath);
                    }
                    stack.push(new Frame(e.getValue(), childPath));
                }
            } else if (n.isArray()) {
                for (int i = 0; i < n.size(); i++) {
                    stack.push(new Frame(n.get(i), p + "[" + i + "]"));
                }
            } else if (n.isTextual()) {
                String text = n.asText();
                if (TOKEN_PATTERN.matcher(text).find()) {
                    violations.add("token-like value matched at " + p + ": "
                            + truncate(text, 128));
                }
            }
        }
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        return SENSITIVE_KEYS_LOWER.contains(key.toLowerCase(Locale.ROOT));
    }

    /** 命中敏感字段后认为合规的取值：null / 字符串等于 "****"。 */
    private static boolean isMaskedScalar(JsonNode value) {
        if (value == null || value.isNull()) {
            return true;
        }
        if (value.isObject() || value.isArray()) {
            // 复合结构（少见）—— 视为非标量字段，递归扫描自然处理；这里返回 true 不报错，
            // 子节点上的敏感键会被进一步验证。
            return true;
        }
        if (value.isTextual()) {
            return FULL_MASK.equals(value.asText());
        }
        // 数字 / 布尔等不是字符串密文，但也不是 "****"；视为不合规
        return false;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private record Frame(JsonNode node, String path) { }
}
