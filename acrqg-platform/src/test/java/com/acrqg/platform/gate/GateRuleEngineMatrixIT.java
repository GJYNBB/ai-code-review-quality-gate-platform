package com.acrqg.platform.gate;

import static org.assertj.core.api.Assertions.assertThat;

import com.acrqg.platform.gate.dto.GateResultDTO;
import com.acrqg.platform.gate.engine.GateRuleEngine;
import com.acrqg.platform.support.PostgresRedisTestBase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 门禁规则引擎独立用例集（B6-A.6）：6 metric × 5 operator × {BLOCKER, WARN} = 60 用例。
 *
 * <p>为每条用例独立构造一组（review_task + code_issue + quality_gate + gate_rule）
 * fixture，调用 {@link GateRuleEngine#evaluate(Long)} 后断言：
 * <ol>
 *   <li>{@code summary} 中 actual 与该 metric 的种子值一致；</li>
 *   <li>规则的 passed 字段与 expected 一致；</li>
 *   <li>聚合状态：BLOCKER 失败 → {@code FAILED}；其它 → {@code PASSED}；</li>
 *   <li>score 公式：BLOCKER 失败扣 20，WARN 失败扣 5，[0,100] 截断。</li>
 * </ol>
 *
 * <p>种子值采用"恰好等于阈值"的策略，便于覆盖 {@code ==} / {@code <=} / {@code >=} 通过路径，
 * 同时让 {@code <} / {@code >} 路径必然失败：
 * <pre>
 *   critical_issue_count   = 2      threshold = 2
 *   security_issue_count   = 1      threshold = 1
 *   new_issue_count        = 3      threshold = 3
 *   ai_risk_score          = 50     threshold = 50
 *   test_coverage          = 0      threshold = 0   （占位 collector 返回 0）
 *   duplicate_rate         = 0      threshold = 0   （占位 collector 返回 0）
 * </pre>
 *
 * <p>每个用例期望的 (passed, status, score) 推导如下：
 * <pre>
 *   operator   passed?   status (BLOCKER)   status (WARN)   score (B/W)
 *   <=         true      PASSED             PASSED          100/100
 *   >=         true      PASSED             PASSED          100/100
 *   <          false     FAILED             PASSED          80/95
 *   >          false     FAILED             PASSED          80/95
 *   ==         true      PASSED             PASSED          100/100
 * </pre>
 *
 * <p>Covers: R13.6, R14.1, R14.2, R14.3, R14.4, R14.5, R14.8, R25.3。
 */
@SpringBootTest
@ActiveProfiles({"test"})
@TestInstance(Lifecycle.PER_CLASS)
class GateRuleEngineMatrixIT extends PostgresRedisTestBase {

    @Autowired GateRuleEngine engine;
    @Autowired DataSource dataSource;

    long adminUserId;
    long projectId;

    /** metric 名称 → 种子值（与 collector 期望返回值一致）。 */
    static final Map<String, String> METRIC_SEED = Map.of(
            "critical_issue_count", "2",
            "security_issue_count", "1",
            "new_issue_count", "3",
            "ai_risk_score", "50",
            "test_coverage", "0",
            "duplicate_rate", "0");

    static final List<String> OPERATORS = List.of("<=", ">=", "<", ">", "==");
    static final List<String> SEVERITIES = List.of("BLOCKER", "WARN");

    @BeforeAll
    void setupBase() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM \"user\" WHERE username = 'admin'", Long.class);
        this.adminUserId = adminId == null ? 0L : adminId;

        Long pid = jdbc.queryForObject(
                "SELECT id FROM project WHERE name = 'proj-it-gate-matrix'", Long.class);
        if (pid == null) {
            jdbc.update("INSERT INTO project(name, default_branch, language, created_by) "
                    + "VALUES ('proj-it-gate-matrix','main','Java', ?)", this.adminUserId);
            pid = jdbc.queryForObject(
                    "SELECT id FROM project WHERE name = 'proj-it-gate-matrix'", Long.class);
        }
        this.projectId = pid == null ? 0L : pid;
    }

    @ParameterizedTest(name = "[{index}] metric={0} op={1} sev={2}")
    @MethodSource("matrix")
    void evaluate_matchesExpectedOutcome(String metric, String operator, String severity) {
        // 1) 创建独立任务 + 该 metric 的种子数据
        long taskId = createTaskAndSeedMetricValue(metric);

        // 2) 构造一个仅包含一条规则的 quality_gate（disable 旧的 enabled 版本）
        long gateId = createGateWithSingleRule(metric, operator, severity);

        // 3) 调用引擎
        GateResultDTO result = engine.evaluate(taskId);
        assertThat(result).as("evaluate should not return null").isNotNull();

        // 4) 推导 expected
        String actualSeed = METRIC_SEED.get(metric);
        boolean expectedPassed = switch (operator) {
            case "<=", ">=", "==" -> true;
            case "<", ">" -> false;
            default -> throw new IllegalStateException("unexpected: " + operator);
        };
        String expectedStatus = expectedPassed
                ? "PASSED"
                : ("BLOCKER".equals(severity) ? "FAILED" : "PASSED");
        int expectedScore;
        if (expectedPassed) {
            expectedScore = 100;
        } else if ("BLOCKER".equals(severity)) {
            expectedScore = 100 - 20;
        } else {
            expectedScore = 100 - 5;
        }

        assertThat(result.status()).as("status").isEqualTo(expectedStatus);
        assertThat(result.score()).as("score").isEqualTo(expectedScore);
        // summary.failedRules / passedRules 至少各 1 条 / 0 条对应
        if (expectedPassed) {
            assertThat(result.summary().passedRules())
                    .as("passedRules contains the rule")
                    .anyMatch(r -> metric.equals(r.metric())
                            && operator.equals(r.operator())
                            && actualSeed.equals(r.threshold()));
        } else {
            assertThat(result.summary().failedRules())
                    .as("failedRules contains the rule")
                    .anyMatch(r -> metric.equals(r.metric())
                            && operator.equals(r.operator())
                            && actualSeed.equals(r.threshold()));
        }
    }

    static Stream<Arguments> matrix() {
        List<Arguments> args = new ArrayList<>(60);
        for (String metric : METRIC_SEED.keySet()) {
            for (String op : OPERATORS) {
                for (String sev : SEVERITIES) {
                    args.add(Arguments.of(metric, op, sev));
                }
            }
        }
        return args.stream();
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private long createTaskAndSeedMetricValue(String metric) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // 唯一 taskNo（每用例独立）：使用 metric + 当前纳秒
        String taskNo = "RT-MATRIX-" + metric.toUpperCase()
                + "-" + System.nanoTime();
        String sha = String.format("%040x", System.nanoTime() & 0xFFFFFFFFFFL);
        Integer aiScore = "ai_risk_score".equals(metric) ? 50 : 0;
        boolean aiAvailable = "ai_risk_score".equals(metric);
        jdbc.update("""
                INSERT INTO review_task
                   (task_no, project_id, pr_id, source_branch, target_branch, commit_sha,
                    status, trigger_type, attempt, ai_risk_score, ai_available,
                    created_by, created_at, updated_at)
                VALUES (?, ?, '1', 'feat/x', 'main', ?,
                        'GATE_EVALUATING', 'MANUAL', 1, ?, ?,
                        ?, NOW(), NOW())
                """,
                taskNo, projectId, sha, aiScore, aiAvailable, adminUserId);
        Long taskId = jdbc.queryForObject(
                "SELECT id FROM review_task WHERE task_no = ?", Long.class, taskNo);
        long tid = taskId == null ? 0L : taskId;

        // 按 metric 类型 seed code_issue
        switch (metric) {
            case "critical_issue_count" -> {
                // 期望 count=2：插入 2 条 CRITICAL，状态 NEW（COUNT 排除 FALSE_POSITIVE）
                seedIssue(jdbc, tid, "CRITICAL", "NEW", "ai-rule-1");
                seedIssue(jdbc, tid, "CRITICAL", "NEW", "ai-rule-2");
            }
            case "security_issue_count" -> {
                // count=1：rule_code 以 security. 开头
                seedIssue(jdbc, tid, "HIGH", "NEW", "security.SQLI-001");
            }
            case "new_issue_count" -> {
                // count=3：状态 NEW
                seedIssue(jdbc, tid, "MEDIUM", "NEW", "code-style-1");
                seedIssue(jdbc, tid, "MEDIUM", "NEW", "code-style-2");
                seedIssue(jdbc, tid, "MEDIUM", "NEW", "code-style-3");
            }
            case "ai_risk_score" -> {
                // 已通过 review_task.ai_risk_score=50 写入；不需 issue
            }
            case "test_coverage", "duplicate_rate" -> {
                // 占位 collector 默认返回 0；不需种子
            }
            default -> throw new IllegalArgumentException("unknown metric: " + metric);
        }
        return tid;
    }

    private void seedIssue(JdbcTemplate jdbc, long taskId, String severity, String status,
                           String ruleCode) {
        jdbc.update("""
                INSERT INTO code_issue
                   (task_id, file_path, line_no, rule_code, source, severity, status,
                    description, suggestion, created_at, updated_at)
                VALUES (?, 'src/test/X.java', 1, ?, 'AI', ?, ?,
                        'matrix-fixture description', 'matrix-fixture suggestion',
                        NOW(), NOW())
                """, taskId, ruleCode, severity, status);
    }

    /**
     * 创建一个新 quality_gate（version=N+1, enabled=true），同时把同项目下旧的
     * enabled 版本翻为 disabled；写入一条 gate_rule。
     */
    private long createGateWithSingleRule(String metric, String operator, String severity) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // 1) disable 旧 enabled 版本
        jdbc.update("UPDATE quality_gate SET enabled = FALSE "
                + "WHERE project_id = ? AND enabled = TRUE", projectId);

        // 2) 计算下一个 version
        Integer nextVer = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) + 1 FROM quality_gate WHERE project_id = ?",
                Integer.class, projectId);
        int version = nextVer == null ? 1 : nextVer;
        String name = "matrix-" + metric + "-" + operator + "-" + severity + "-v" + version;

        jdbc.update("""
                INSERT INTO quality_gate
                   (project_id, name, version, enabled, created_by, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, ?, NOW(), NOW())
                """, projectId, name, version, adminUserId);
        Long gid = jdbc.queryForObject(
                "SELECT id FROM quality_gate WHERE project_id = ? AND version = ?",
                Long.class, projectId, version);
        long gateId = gid == null ? 0L : gid;

        // 3) 插入一条 gate_rule（threshold = METRIC_SEED 值）
        String threshold = METRIC_SEED.get(metric);
        jdbc.update("""
                INSERT INTO gate_rule
                   (gate_id, metric, operator, threshold, severity, enabled, sort_order)
                VALUES (?, ?, ?, ?, ?, TRUE, 0)
                """, gateId, metric, operator, threshold, severity);
        return gateId;
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> dummyDetail() {
        return new LinkedHashMap<>();
    }
}
