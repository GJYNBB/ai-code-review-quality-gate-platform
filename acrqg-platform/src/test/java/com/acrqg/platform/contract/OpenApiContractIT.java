package com.acrqg.platform.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.acrqg.platform.support.PostgresRedisTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.flipkart.zjsonpatch.JsonDiff;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * OpenAPI 契约基线 IT（B6-A.9）。
 *
 * <p>启动 Spring Boot（{@code RANDOM_PORT}），抓取 {@code /v3/api-docs} 与
 * {@code docs/openapi-baseline.json} 对比：
 * <ul>
 *   <li>聚焦比较 {@code paths} + {@code components.schemas} 两段；其它字段（如
 *       {@code info.version} / {@code servers}）变化不视作回归；</li>
 *   <li>不一致时打印 RFC-6902 JSON Patch diff，让排查更直观；</li>
 *   <li>支持通过系统属性 {@code -DupdateBaseline=true} 把当前抓取写回 baseline。</li>
 * </ul>
 *
 * <p>注意：{@code paths} 比较忽略路径中的 {@code paths.<route>.<method>.operationId}
 * 等可能由 framework 自动生成而难以稳定比对的字段；只关心：
 * <ul>
 *   <li>是否新增 / 删除路径；</li>
 *   <li>是否新增 / 删除方法；</li>
 *   <li>是否新增 / 删除 schema。</li>
 * </ul>
 *
 * <p>Covers: R25.2。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test"})
class OpenApiContractIT extends PostgresRedisTestBase {

    @LocalServerPort
    int port;

    /** 系统属性：{@code -DupdateBaseline=true} 时把当前抓取写回 baseline 文件。 */
    static final String UPDATE_BASELINE_PROP = "updateBaseline";

    /** baseline 文件相对项目根的固定路径。 */
    static final Path BASELINE_PATH =
            Paths.get("..", "docs", "openapi-baseline.json").normalize();

    @Test
    void runtimeApiDocsMatchesBaseline() throws IOException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // 1) 抓取运行时 /v3/api-docs
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v3/api-docs"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).as("/v3/api-docs HTTP status").isEqualTo(200);
        JsonNode runtime = mapper.readTree(resp.body());

        // 2) 读取 baseline
        Path baseline = locateBaseline();
        assertThat(baseline).as("baseline file should exist at " + baseline.toAbsolutePath())
                .exists();
        JsonNode baselineNode = mapper.readTree(baseline.toFile());

        // 3) 提取关注字段进行对比：paths + components.schemas
        JsonNode runtimePaths = runtime.path("paths");
        JsonNode runtimeSchemas = runtime.path("components").path("schemas");
        JsonNode baselinePaths = baselineNode.path("paths");
        JsonNode baselineSchemas = baselineNode.path("components").path("schemas");

        // 4) updateBaseline 模式：直接覆盖 baseline 文件并跳过断言
        if (Boolean.parseBoolean(System.getProperty(UPDATE_BASELINE_PROP, "false"))) {
            Files.writeString(baseline, mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(runtime), StandardCharsets.UTF_8);
            return;
        }

        // 5) 比较 paths
        JsonNode pathsDiff = JsonDiff.asJson(baselinePaths, runtimePaths);
        if (pathsDiff.size() > 0) {
            String prettyDiff = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(pathsDiff);
            assertThat(false)
                    .as("OpenAPI paths drift detected. Run with -DupdateBaseline=true after"
                            + " review.\n--- Diff (RFC-6902) ---\n" + prettyDiff)
                    .isTrue();
        }

        // 6) 比较 schemas
        JsonNode schemasDiff = JsonDiff.asJson(baselineSchemas, runtimeSchemas);
        if (schemasDiff.size() > 0) {
            String prettyDiff = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(schemasDiff);
            assertThat(false)
                    .as("OpenAPI schemas drift detected. Run with -DupdateBaseline=true after"
                            + " review.\n--- Diff (RFC-6902) ---\n" + prettyDiff)
                    .isTrue();
        }
    }

    /**
     * 定位 baseline 文件路径，兼容以下两种工作目录：
     * <ul>
     *   <li>从 worktree 根（含 {@code docs/}）启动；</li>
     *   <li>从 {@code acrqg-platform/} 子目录启动（默认 mvn 工作目录）。</li>
     * </ul>
     */
    private Path locateBaseline() {
        Path candidate1 = Paths.get("docs", "openapi-baseline.json");
        if (Files.exists(candidate1)) {
            return candidate1;
        }
        Path candidate2 = Paths.get("..", "docs", "openapi-baseline.json").normalize();
        if (Files.exists(candidate2)) {
            return candidate2;
        }
        // 兜底：返回 candidate2，让 assertion 报告"file should exist"
        return candidate2;
    }
}
