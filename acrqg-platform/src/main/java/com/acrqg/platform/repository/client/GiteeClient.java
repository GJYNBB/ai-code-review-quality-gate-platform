package com.acrqg.platform.repository.client;

import com.acrqg.platform.common.util.JsonUtils;
import com.acrqg.platform.diff.domain.ChangeType;
import com.acrqg.platform.repository.domain.Provider;
import com.acrqg.platform.repository.dto.ConnectivityResultDTO;
import com.acrqg.platform.repository.dto.DiffFetchRequest;
import com.acrqg.platform.repository.dto.DiffFilePayload;
import com.acrqg.platform.repository.dto.DiffPayload;
import com.acrqg.platform.repository.dto.RepositoryTestRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Gitee REST v5 实现（design.md §6.2 / §6.4 / §11）。
 *
 * <p>Endpoint:
 * <ul>
 *   <li>{@link #ping}      —— {@code GET /repos/{owner}/{repo}}；</li>
 *   <li>{@link #fetchDiff} —— {@code GET /repos/{owner}/{repo}/pulls/{number}/files}，
 *       响应 JSON 数组，与 GitHub 同形（{@code filename / previous_filename /
 *       status / patch / additions / deletions / sha}）。</li>
 * </ul>
 *
 * <p>Header: {@code Authorization: token {accessToken}}（Gitee 私有仓库的标准
 * 鉴权方式；也支持 {@code access_token} query 参数，但为避免 token 出现在 URL
 * 与日志，本实现统一选 header 方案）。
 *
 * <p>分页：Gitee 的 {@code pulls/{n}/files} 接口未文档化分页参数；当前实现仅请求
 * 一次，假定单 PR 文件数 ≤ 默认上限。如未来需要分页，可参考 GitHub Link 头方案
 * （Gitee 也返回 {@code Link} 头）；本实现预留 {@code MAX_PAGES} 在父类。
 *
 * <p>HTTP 状态映射：4xx / 5xx 与网络异常一律抛 {@link DiffFetchException}（R10.4）。
 *
 * <p>Covers: R5.1, R5.2, R10.1, R10.4, R23.3。
 */
@Component
public class GiteeClient extends AbstractProviderClient {

    /** Gitee.com REST v5 根。 */
    private static final String API_ROOT = "https://gitee.com/api/v5";

    @Override
    public String name() {
        return Provider.GITEE.name();
    }

    @Override
    public ConnectivityResultDTO ping(RepositoryTestRequest req) {
        if (req == null) {
            return ConnectivityResultDTO.unreachable("invalid request");
        }
        final RepoUrlParser.Parsed parsed;
        try {
            parsed = RepoUrlParser.parse(req.repoUrl());
        } catch (IllegalArgumentException ex) {
            return ConnectivityResultDTO.unreachable("unreachable: " + ex.getMessage());
        }
        final String url = API_ROOT + "/repos/" + parsed.owner() + "/" + parsed.repo();
        return runPing(() -> restClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "token " + req.accessToken())
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public DiffPayload fetchDiff(DiffFetchRequest req) {
        if (req == null) {
            throw new DiffFetchException(null, null, null, "DiffFetchRequest is null");
        }
        if (req.prId() == null || req.prId().isBlank()) {
            throw new DiffFetchException(req.provider(), req.repoUrl(), null,
                    "Gitee fetchDiff requires prId");
        }
        final RepoUrlParser.Parsed parsed;
        try {
            parsed = RepoUrlParser.parse(req.repoUrl());
        } catch (IllegalArgumentException ex) {
            throw new DiffFetchException(req.provider(), req.repoUrl(), req.prId(),
                    "invalid repoUrl: " + ex.getMessage(), ex);
        }

        String url = API_ROOT + "/repos/" + parsed.owner() + "/" + parsed.repo()
                + "/pulls/" + req.prId() + "/files";
        ResponseEntity<String> resp;
        try {
            resp = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "token " + req.accessToken())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .toEntity(String.class);
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            throw new DiffFetchException(req.provider(), req.repoUrl(), req.prId(),
                    "HTTP " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw new DiffFetchException(req.provider(), req.repoUrl(), req.prId(),
                    "network error: " + (ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage()),
                    ex);
        } catch (RuntimeException ex) {
            throw new DiffFetchException(req.provider(), req.repoUrl(), req.prId(),
                    "fetchDiff failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage()),
                    ex);
        }

        JsonNode root;
        try {
            root = JsonUtils.tree(resp.getBody() == null ? "[]" : resp.getBody());
        } catch (RuntimeException ex) {
            throw new DiffFetchException(req.provider(), req.repoUrl(), req.prId(),
                    "invalid JSON response from Gitee", ex);
        }
        if (!root.isArray()) {
            throw new DiffFetchException(req.provider(), req.repoUrl(), req.prId(),
                    "expected JSON array from Gitee /pulls/{prId}/files");
        }

        List<DiffFilePayload> all = new ArrayList<>();
        for (JsonNode node : root) {
            all.add(toFilePayload(node));
        }
        return new DiffPayload(all);
    }

    private static DiffFilePayload toFilePayload(JsonNode node) {
        String filename = textOrNull(node, "filename");
        String previousFilename = textOrNull(node, "previous_filename");
        String status = textOrNull(node, "status");
        String patch = textOrNull(node, "patch");
        int additions = node.path("additions").asInt(0);
        int deletions = node.path("deletions").asInt(0);
        String sha = textOrNull(node, "sha");
        ChangeType type = ChangeType.fromGithubLike(status);
        return new DiffFilePayload(
                filename,
                type == ChangeType.RENAMED ? previousFilename : null,
                type.name(),
                patch,
                additions,
                deletions,
                sha);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText();
    }
}
