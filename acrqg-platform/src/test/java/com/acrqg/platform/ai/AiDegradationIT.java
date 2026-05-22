package com.acrqg.platform.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.acrqg.platform.ai.service.AiReviewOutcome;
import com.acrqg.platform.ai.service.AiReviewService;
import com.acrqg.platform.infra.crypto.TokenEncryptor;
import com.acrqg.platform.support.PostgresRedisTestBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * AI 降级场景 IT（B6-A.7）。
 *
 * <p>三类降级路径：
 * <ol>
 *   <li><b>超时</b>：WireMock 注入 {@code fixedDelay} = ai timeout + 5s；预期
 *       {@link AiReviewService#execute(Long)} 返回 {@code aiAvailable=false}，且
 *       {@code task_log} 含 WARN 级 "ai service unavailable" 文案。</li>
 *   <li><b>5xx</b>：WireMock 直接返回 503；预期同上路径降级。</li>
 *   <li><b>200 + Schema 不合法</b>：WireMock 返回符合 OpenAI 包装但 issues 字段
 *       结构非法（缺 description 等必填字段）；预期 {@code aiAvailable=true}（按
 *       {@link com.acrqg.platform.ai.service.impl.AiReviewServiceImpl} 中"丢弃响应"
 *       路径），但 {@code aiRiskScore=0}, {@code task_log} 含 WARN 级 "schema" 文案。</li>
 * </ol>
 *
 * <p>三类用例都不抛异常向上，使后续 {@code GateEvaluatingStage} 仍可推进至终态。
 *
 * <p>Covers: R12.4, R12.5, R25.4。
 */
@SpringBootTest(properties = {
        "app.ai.review.timeout-seconds=2"  // 缩短超时以加速测试
})
@ActiveProfiles({"test"})
@TestInstance(Lifecycle.PER_CLASS)
class AiDegradationIT extends PostgresRedisTestBase {

    private static WireMockServer wireMock;

    @Autowired AiReviewService aiReviewService;
    @Autowired DataSource dataSource;
    @Autowired TokenEncryptor tokenEncryptor;

    long adminUserId;
    long projectId;
    long modelId;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options()
                .dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeAll
    void seed() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM \"user\" WHERE username = 'admin'", Long.class);
        this.adminUserId = adminId == null ? 0L : adminId;

        Long pid = jdbc.queryForObject(
                "SELECT id FROM project WHERE name = 'proj-it-ai-degrade'", Long.class);
        if (pid == null) {
            jdbc.update("INSERT INTO project(name, default_branch, language, created_by) "
                    + "VALUES ('proj-it-ai-degrade','main','Java', ?)", this.adminUserId);
            pid = jdbc.queryForObject(
                    "SELECT id FROM project WHERE name = 'proj-it-ai-degrade'", Long.class);
        }
        this.projectId = pid == null ? 0L : pid;

        // 把所有现有 enabled=true 的模型 disable，再插入一个 wiremock 模型作为唯一 enabled
        jdbc.update("UPDATE model_config SET enabled = FALSE WHERE enabled = TRUE");
        Long mid = jdbc.queryForObject(
                "SELECT id FROM model_config WHERE name = 'wiremock-model'", Long.class);
        String encryptedKey = tokenEncryptor.encrypt("sk-test-key-12345678");
        if (mid == null) {
            jdbc.update("""
                    INSERT INTO model_config
                       (name, base_url, api_key_encrypted, timeout_seconds, enabled,
                        created_at, updated_at)
                    VALUES ('wiremock-model', ?, ?, 30, TRUE, NOW(), NOW())
                    """, wireMock.baseUrl(), encryptedKey);
            mid = jdbc.queryForObject(
                    "SELECT id FROM model_config WHERE name = 'wiremock-model'", Long.class);
        } else {
            jdbc.update("""
                    UPDATE model_config
                       SET base_url = ?, api_key_encrypted = ?, enabled = TRUE
                     WHERE id = ?
                    """, wireMock.baseUrl(), encryptedKey, mid);
        }
        this.modelId = mid == null ? 0L : mid;
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    private long createTaskWithDiff() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String taskNo = "RT-AI-DEG-" + System.nanoTime();
        String sha = String.format("%040x", System.nanoTime() & 0xFFFFFFFFFFL);
        jdbc.update("""
                INSERT INTO review_task
                   (task_no, project_id, pr_id, source_branch, target_branch, commit_sha,
                    status, trigger_type, attempt, ai_available, created_by,
                    created_at, updated_at)
                VALUES (?, ?, '1', 'feat/x', 'main', ?,
                        'AI_REVIEWING', 'MANUAL', 1, TRUE, ?, NOW(), NOW())
                """, taskNo, projectId, sha, adminUserId);
        Long tid = jdbc.queryForObject(
                "SELECT id FROM review_task WHERE task_no = ?", Long.class, taskNo);
        long taskId = tid == null ? 0L : tid;

        // 一条 diff_file，让 AiReviewPayload 非空
        jdbc.update("""
                INSERT INTO diff_file
                   (task_id, file_path, change_type, additions, deletions, oversized,
                    patch, created_at)
                VALUES (?, 'src/main/java/X.java', 'MODIFIED', 5, 2, FALSE,
                        '@@ -1 +1 @@\n-old\n+new', NOW())
                """, taskId);
        return taskId;
    }

    private long countTaskLogContaining(long taskId, String level, String snippet) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long n = jdbc.queryForObject(
                "SELECT COUNT(1) FROM task_log "
                + "WHERE task_id = ? AND level = ? AND message ILIKE ?",
                Long.class, taskId, level, "%" + snippet + "%");
        return n == null ? 0L : n;
    }

    @Test
    void timeout_returnsUnavailableAndLogsWarn() {
        long taskId = createTaskWithDiff();
        // 注入 fixedDelay > timeout
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withFixedDelay(7_000)  // > 2s timeout + buffer
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"choices\":[{\"message\":{\"content\":\"{\\\"issues\\\":[]}\"}}]}")));

        AiReviewOutcome outcome = aiReviewService.execute(taskId);
        assertThat(outcome).isNotNull();
        assertThat(outcome.aiAvailable()).as("aiAvailable on timeout").isFalse();
        assertThat(outcome.aiRiskScore()).isZero();
        assertThat(outcome.issuesPersisted()).isZero();

        long warnLogs = countTaskLogContaining(taskId, "WARN", "ai service unavailable");
        assertThat(warnLogs).as("WARN log entry mentioning unavailable").isGreaterThan(0L);
    }

    @Test
    void serverError5xx_returnsUnavailableAndLogsWarn() {
        long taskId = createTaskWithDiff();
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"upstream\"}")));

        AiReviewOutcome outcome = aiReviewService.execute(taskId);
        assertThat(outcome).isNotNull();
        assertThat(outcome.aiAvailable()).isFalse();
        assertThat(outcome.aiRiskScore()).isZero();

        long warnLogs = countTaskLogContaining(taskId, "WARN", "unavailable");
        assertThat(warnLogs).as("WARN log entry").isGreaterThan(0L);
    }

    @Test
    void schemaInvalid_returnsAvailableButZeroScore() {
        long taskId = createTaskWithDiff();
        // 200 + 内容是 OpenAI 包装但 issues[0] 缺必填 description / suggestion / confidence
        String invalidContent = "{\"issues\":[{\"filePath\":\"src/X.java\","
                + "\"severity\":\"INFO\"}]}";
        String body = "{\"choices\":[{\"message\":{\"content\":\""
                + invalidContent.replace("\"", "\\\"") + "\"}}]}";
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));

        AiReviewOutcome outcome = aiReviewService.execute(taskId);
        assertThat(outcome).isNotNull();
        // 设计 §12.4 + AiReviewServiceImpl#finishWithDiscardedResponse：
        // schema 非法时 aiAvailable=true 但 issues=0, score=0
        assertThat(outcome.aiAvailable()).isTrue();
        assertThat(outcome.issuesPersisted()).isZero();
        assertThat(outcome.aiRiskScore()).isZero();

        long warnLogs = countTaskLogContaining(taskId, "WARN", "schema");
        assertThat(warnLogs).as("WARN log mentioning schema").isGreaterThan(0L);
    }
}
