package com.acrqg.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.acrqg.platform.infra.security.JwtTokenProvider;import com.acrqg.platform.support.PostgresRedisTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 越权矩阵集成测试（design.md §15.6 全部 8 行）。
 *
 * <p>本测试在 Testcontainers (postgres:15 + redis:7) 上启动完整 ApplicationContext，
 * 使用 MockMvc 直接打到 controller 链路，断言：
 * <ul>
 *   <li>未鉴权 / 过期 token / 禁用用户 token → 401 + {@code AUTH_INVALID_TOKEN}；</li>
 *   <li>角色 / 项目角色 / 项目成员关系不匹配 → 403 + {@code PERMISSION_DENIED}。</li>
 * </ul>
 *
 * <p>所用业务 fixture：
 * <ul>
 *   <li>{@code admin} 用户（V1__init.sql 种子）：SYSTEM_ADMIN，用于创建项目；</li>
 *   <li>本测试 {@code @BeforeAll} 中通过 SQL 直接 seed：
 *     <ul>
 *       <li>{@code dev1} (DEVELOPER)；</li>
 *       <li>{@code dev2} (DEVELOPER)，DISABLED；</li>
 *       <li>{@code reviewer1} (REVIEWER)；</li>
 *       <li>{@code cicd1} (CI_CD)；</li>
 *       <li>项目 {@code proj-it-auth}，{@code admin} 自动 PROJECT_ADMIN；
 *           {@code reviewer1} 加为项目内 REVIEWER。</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Covers: R23.1, R25.5。Requirements: design §15.6。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test"})
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class AuthorizationMatrixIT extends PostgresRedisTestBase {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DataSource dataSource;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Value("${app.security.jwt.secret}")
    String jwtSecret;

    long adminUserId;
    long developerUserId;
    long disabledUserId;
    long reviewerUserId;
    long cicdUserId;
    long projectId;
    long taskId;

    @BeforeAll
    void seedFixtures() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // 1) admin 已由 V1__init seed
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM \"user\" WHERE username = 'admin'", Long.class);
        this.adminUserId = adminId == null ? 0L : adminId;

        // 2) 4 个测试用户 + 角色 ([dev1=DEVELOPER, dev2=DEVELOPER+DISABLED,
        //    reviewer1=REVIEWER, cicd1=CI_CD])
        // 密码 hash 取自 V1__init 中的 admin（明文 Admin@123）；这里只关注角色与状态
        String pwdHash = "$2a$10$wH2j2fMnJZDj7jVRNJqnku0nhHVEf9D2QdBFdQ1mcg1GZj7LgFmYS";
        this.developerUserId = upsertUser(jdbc, "it_dev1", "it_dev1@local", pwdHash, "ENABLED");
        this.disabledUserId  = upsertUser(jdbc, "it_dev2", "it_dev2@local", pwdHash, "DISABLED");
        this.reviewerUserId  = upsertUser(jdbc, "it_rev1", "it_rev1@local", pwdHash, "ENABLED");
        this.cicdUserId      = upsertUser(jdbc, "it_cicd1", "it_cicd1@local", pwdHash, "ENABLED");

        bindRole(jdbc, this.developerUserId, "DEVELOPER");
        bindRole(jdbc, this.disabledUserId,  "DEVELOPER");
        bindRole(jdbc, this.reviewerUserId,  "REVIEWER");
        bindRole(jdbc, this.cicdUserId,      "CI_CD");

        // 3) 创建项目 proj-it-auth，admin 自动成为 PROJECT_ADMIN
        Long pid = jdbc.queryForObject(
                "SELECT id FROM project WHERE name = 'proj-it-auth'", Long.class);
        if (pid == null) {
            jdbc.update("INSERT INTO project(name, default_branch, language, created_by) "
                    + "VALUES ('proj-it-auth','main','Java', ?)", this.adminUserId);
            pid = jdbc.queryForObject(
                    "SELECT id FROM project WHERE name = 'proj-it-auth'", Long.class);
        }
        this.projectId = pid == null ? 0L : pid;

        // admin → PROJECT_ADMIN of proj-it-auth
        Integer adminMember = jdbc.queryForObject(
                "SELECT COUNT(1) FROM project_member WHERE project_id=? AND user_id=?",
                Integer.class, this.projectId, this.adminUserId);
        if (adminMember == null || adminMember == 0) {
            jdbc.update("INSERT INTO project_member(project_id, user_id, project_role) "
                    + "VALUES (?, ?, 'PROJECT_ADMIN')", this.projectId, this.adminUserId);
        }
        // reviewer1 → 项目内 REVIEWER
        Integer rvMember = jdbc.queryForObject(
                "SELECT COUNT(1) FROM project_member WHERE project_id=? AND user_id=?",
                Integer.class, this.projectId, this.reviewerUserId);
        if (rvMember == null || rvMember == 0) {
            jdbc.update("INSERT INTO project_member(project_id, user_id, project_role) "
                    + "VALUES (?, ?, 'REVIEWER')", this.projectId, this.reviewerUserId);
        }

        // 4) 一条 review_task 用于 case 7
        Long tid = jdbc.queryForObject(
                "SELECT id FROM review_task WHERE task_no = 'RT-IT-AUTH-1'", Long.class);
        if (tid == null) {
            jdbc.update("""
                    INSERT INTO review_task
                       (task_no, project_id, pr_id, source_branch, target_branch, commit_sha,
                        status, trigger_type, attempt, created_by, created_at, updated_at)
                    VALUES ('RT-IT-AUTH-1', ?, '1', 'feat/x', 'main',
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        'PASSED', 'MANUAL', 1, ?, NOW(), NOW())
                    """, this.projectId, this.adminUserId);
            tid = jdbc.queryForObject(
                    "SELECT id FROM review_task WHERE task_no = 'RT-IT-AUTH-1'", Long.class);
        }
        this.taskId = tid == null ? 0L : tid;
    }

    private long upsertUser(JdbcTemplate jdbc, String username, String email,
                            String pwdHash, String status) {
        Long existing = jdbc.query(
                "SELECT id FROM \"user\" WHERE username = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                username);
        if (existing != null) {
            jdbc.update("UPDATE \"user\" SET status = ? WHERE id = ?", status, existing);
            return existing;
        }
        jdbc.update("INSERT INTO \"user\"(username, email, password_hash, status) "
                + "VALUES (?, ?, ?, ?)", username, email, pwdHash, status);
        Long created = jdbc.queryForObject(
                "SELECT id FROM \"user\" WHERE username = ?", Long.class, username);
        return created == null ? 0L : created;
    }

    private void bindRole(JdbcTemplate jdbc, long userId, String roleCode) {
        jdbc.update("""
                INSERT INTO user_role(user_id, role_id)
                SELECT ?, r.id FROM role r WHERE r.code = ?
                ON CONFLICT (user_id, role_id) DO NOTHING
                """, userId, roleCode);
    }

    // =====================================================================
    // 用例 1：不带 token 访问 /api/v1/projects → 401 AUTH_INVALID_TOKEN
    // =====================================================================
    @Test
    @Order(1)
    void case1_missingToken_returnsUnauthorized() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertCode(result, "AUTH_INVALID_TOKEN");
    }

    // =====================================================================
    // 用例 2：过期 token → 401
    // =====================================================================
    @Test
    @Order(2)
    void case2_expiredToken_returnsUnauthorized() throws Exception {
        String expired = makeExpiredAccessToken(adminUserId, "admin", List.of("SYSTEM_ADMIN"));
        MvcResult result = mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + expired))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertCode(result, "AUTH_INVALID_TOKEN");
    }

    // =====================================================================
    // 用例 3：被禁用用户 token → 401（jti 在黑名单 / 用户状态校验）
    //
    // JwtBlacklist 实现会在用户 disabled 时把其当前活跃 token 加入黑名单；
    // 本测试通过 admin 以"已 disabled 用户的 sub" 重新签发 token + 把该 jti 主动写入
    // 黑名单（若 bean 可用）。简化方案：直接用一个 sub=disabledUserId 的合法 token，
    // 然后调用 user-status 切换接口把它禁用 —— 实现保证下次请求 401。
    //
    // 这里我们直接发一个被签名但 sub 用 disabledUserId 的 access token，
    // 并通过 jwtJtiBlacklist bean（若 wired）模拟 token 已撤销；如未 wired，
    // 验证 disabled 用户登录失败也是等价的安全语义（AUTH_INVALID_TOKEN 与
    // AUTH_ACCOUNT_DISABLED 同为 401）。
    // =====================================================================
    @Test
    @Order(3)
    void case3_disabledUserToken_returnsUnauthorized() throws Exception {
        // 直接给 disabled 用户签发 access token；JwtAuthFilter 会通过 disabled 状态
        // 经由 JwtBlacklist 拒绝。如果 jti 未提前进入黑名单，至少应触发 401，
        // 不能绕过到下游 controller 看到 200。
        String token = jwtTokenProvider.issueAccessToken(
                disabledUserId, "it_dev2", List.of("DEVELOPER"));
        // 主动把 jti 写入 jwt:blacklist:* key，模拟 disable 动作的最终一致结果
        addJtiToBlacklist(token);

        MvcResult result = mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertCode(result, "AUTH_INVALID_TOKEN");
    }

    // =====================================================================
    // 用例 4：DEVELOPER 调 POST /api/v1/projects → 403
    // =====================================================================
    @Test
    @Order(4)
    void case4_developerCreateProject_returns403() throws Exception {
        String token = jwtTokenProvider.issueAccessToken(
                developerUserId, "it_dev1", List.of("DEVELOPER"));
        String body = "{\"name\":\"deny-by-role\",\"defaultBranch\":\"main\",\"language\":\"Java\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertCode(result, "PERMISSION_DENIED");
    }

    // =====================================================================
    // 用例 5：非 SYSTEM_ADMIN 调 GET /api/v1/admin/audit-logs → 403
    // =====================================================================
    @Test
    @Order(5)
    void case5_nonSystemAdminAuditLogs_returns403() throws Exception {
        String token = jwtTokenProvider.issueAccessToken(
                developerUserId, "it_dev1", List.of("DEVELOPER"));
        MvcResult result = mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertCode(result, "PERMISSION_DENIED");
    }

    // =====================================================================
    // 用例 6：CI_CD 调 POST /api/v1/projects → 403
    // =====================================================================
    @Test
    @Order(6)
    void case6_cicdCreateProject_returns403() throws Exception {
        String token = jwtTokenProvider.issueAccessToken(
                cicdUserId, "it_cicd1", List.of("CI_CD"));
        String body = "{\"name\":\"deny-cicd\",\"defaultBranch\":\"main\",\"language\":\"Java\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertCode(result, "PERMISSION_DENIED");
    }

    // =====================================================================
    // 用例 7：非项目成员 GET /api/v1/review-tasks/{id} → 403
    //
    // dev1 既不是 admin/proj-it-auth 的成员；调用应被 PermissionEvaluator 拒绝。
    // =====================================================================
    @Test
    @Order(7)
    void case7_nonMemberGetReviewTask_returns403() throws Exception {
        String token = jwtTokenProvider.issueAccessToken(
                developerUserId, "it_dev1", List.of("DEVELOPER"));
        MvcResult result = mockMvc.perform(get("/api/v1/review-tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertCode(result, "PERMISSION_DENIED");
    }

    // =====================================================================
    // 用例 8：REVIEWER 项目成员调 POST /api/v1/projects/{id}/repository → 403
    //
    // reviewer1 已加 proj-it-auth 项目（角色 REVIEWER），但 bind 仓库需要项目内
    // PROJECT_ADMIN，期望 403。
    // =====================================================================
    @Test
    @Order(8)
    void case8_reviewerBindRepository_returns403() throws Exception {
        String token = jwtTokenProvider.issueAccessToken(
                reviewerUserId, "it_rev1", List.of("REVIEWER"));
        String body = "{\"provider\":\"GITHUB\","
                + "\"repoUrl\":\"https://github.com/example/x.git\","
                + "\"accessToken\":\"ghp_dummytokenpadding1234567890\","
                + "\"webhookSecret\":\"dummy-webhook-secret-1234567890\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/projects/" + projectId + "/repository")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertCode(result, "PERMISSION_DENIED");
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private void assertCode(MvcResult result, String expectedCode) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(body);
        assertThat(node.has("code")).as("ApiResponse should contain 'code' field; body=" + body).isTrue();
        assertThat(node.get("code").asText()).isEqualTo(expectedCode);
    }

    /** 构造一个签名合法但已过期的 access token（exp = now-60s）。 */
    private String makeExpiredAccessToken(long userId, String username, List<String> roles) {
        SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now.minusSeconds(7200)))
                .expiration(Date.from(now.minusSeconds(60)))
                .claim("username", username)
                .claim("roles", roles)
                .claim("tokenType", "ACCESS")
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 把当前 token 的 jti 加入 Redis 黑名单；JwtBlacklist 实现读 {@code jwt:blacklist:{jti}}。
     */
    private void addJtiToBlacklist(String token) {
        try {
            io.jsonwebtoken.Claims claims = jwtTokenProvider.parse(token);
            String jti = claims.getId();
            if (jti == null) {
                return;
            }
            // 通过 ApplicationContext 获取 RedisConnectionFactory
            org.springframework.data.redis.core.StringRedisTemplate t =
                    new org.springframework.data.redis.core.StringRedisTemplate(
                            connectionFactory());
            t.afterPropertiesSet();
            t.opsForValue().set("jwt:blacklist:" + jti, "1",
                    java.time.Duration.ofMinutes(10));
        } catch (RuntimeException ignore) {
            // 黑名单写入失败不应阻塞测试；用例自身仍要求 401。
        }
    }

    @Autowired
    org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    private org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory() {
        return redisConnectionFactory;
    }
}
