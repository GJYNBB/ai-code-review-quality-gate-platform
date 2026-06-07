package com.acrqg.platform.repository.client;

import com.acrqg.platform.common.util.JsonUtils;
import com.acrqg.platform.diff.domain.ChangeType;
import com.acrqg.platform.infra.net.OutboundUrlGuard;
import com.acrqg.platform.repository.domain.Provider;
import com.acrqg.platform.repository.dto.CommitStatusRequest;
import com.acrqg.platform.repository.dto.CommitStatusState;
import com.acrqg.platform.repository.dto.ConnectivityResultDTO;
import com.acrqg.platform.repository.dto.DiffFetchRequest;
import com.acrqg.platform.repository.dto.DiffFilePayload;
import com.acrqg.platform.repository.dto.DiffPayload;
import com.acrqg.platform.repository.dto.RepositoryTestRequest;
import com.acrqg.platform.writeback.exception.WritebackException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * GitLab REST v4 实现（design.md §6.2 / §6.4 / §11）。
 *
 * <p>Endpoint:
 * <ul>
 *   <li>{@link #ping}      —— {@code GET /api/v4/projects/{urlEncodedFullPath}}；</li>
 *   <li>{@link #fetchDiff} —— {@code GET /api/v4/projects/{urlEncodedFullPath}/merge_requests/{iid}/changes}；
 *       响应是单对象，{@code .changes[]} 数组中每个元素含 {@code old_path / new_path / new_file /
 *       deleted_file / renamed_file / diff} 字段。</li>
 * </ul>
 *
 * <p>Header: {@code PRIVATE-TOKEN: {token}}。GitLab 同时支持 OAuth Bearer，
 * 但 personal access token 通过 {@code PRIVATE-TOKEN} 头是最常见用法。
 *
 * <p>响应映射：
 * <pre>
 *   new_path                              → filePath
 *   old_path                              → oldPath（仅 RENAMED）
 *   new_file/deleted_file/renamed_file    → ChangeType（fromGitlabFlags）
 *   diff                                  → patch
 *   additions/deletions                   → 由 patch 文本派生（GitLab REST v4 不直接返回数值）
 *                                            => 在 ProviderClient 层先置 0；
 *                                            真实数值由 DiffParser 解析 patch 时计算（DiffParser
 *                                            会无视此字段直接解析）
 *   sha                                   → null（GitLab changes 接口不返回 blob SHA）
 * </pre>
 *
 * <p>使用 {@code repoUrl} 中的 host 而非硬编码 gitlab.com，以兼容自建 GitLab。
 *
 * <p>HTTP 状态映射：4xx / 5xx 与网络异常一律抛 {@link DiffFetchException}（R10.4）。
 *
 * <p>Covers: R5.1, R5.2, R10.1, R10.4, R23.3。
 */
@Component
public class GitlabClient extends AbstractProviderClient {

    @Override
    public String name() {
        return Provider.GITLAB.name();
    }

    @Override
    public ConnectivityResultDTO ping(RepositoryTestRequest req) {
        if (req == null) {
            return ConnectivityResultDTO.unreachable("invalid request");
        }
        final RepoUrlParser.Parsed parsed;
        try {
            parsed = RepoUrlParser.parse(req.repoUrl());
            OutboundUrlGuard.requirePublicHost(parsed.host(), "GitLab repository URL");
        } catch (IllegalArgumentException ex) {
            return ConnectivityResultDTO.unreachable("unreachable: " + ex.getMessage());
        }
        // GitLab 要求 fullPath 整体 URL 编码（slash → %2F）
        String encoded = URLEncoder.encode(parsed.fullPath(), StandardCharsets.UTF_8);
        final String url = "https://" + parsed.host() + "/api/v4/projects/" + encoded;
        return runPing(() -> restClient.get()
                .uri(url)
                .header("PRIVATE-TOKEN", req.accessToken())
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
                    "GitLab fetchDiff requires prId (MR iid)");
        }
        final RepoUrlParser.Parsed parsed;
        try {
            parsed = RepoUrlParser.parse(req.repoUrl());
            OutboundUrlGuard.requirePublicHost(parsed.host(), "GitLab repository URL");
        } catch (IllegalArgumentException ex) {
            throw new DiffFetchException(req.provider(), req.repoUrl(), req.prId(),
                    "invalid repoUrl: " + ex.getMessage(), ex);
        }

        String encoded = URLEncoder.encode(parsed.fullPath(), StandardCharsets.UTF_8);
        String url = "https://" + parsed.host() + "/api/v4/projects/" + encoded
                + "/merge_requests/" + req.prId() + "/changes";

        ResponseEntity<String> resp;
        try {
            resp = restClient.get()
                    .uri(url)
                    .header("PRIVATE-TOKEN", req.accessToken())
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
            root = JsonUtils.tree(resp.getBody() == null ? "{}" : resp.getBody());
        } catch (RuntimeException ex) {
            throw new DiffFetchException(req.provider(), req.repoUrl(), req.prId(),
                    "invalid JSON response from GitLab", ex);
        }
        JsonNode changes = root.path("changes");
        if (!changes.isArray()) {
            throw new DiffFetchException(req.provider(), req.repoUrl(), req.prId(),
                    "missing 'changes' array in GitLab response");
        }

        List<DiffFilePayload> all = new ArrayList<>();
        for (JsonNode node : changes) {
            all.add(toFilePayload(node));
        }
        return new DiffPayload(all);
    }

    private static DiffFilePayload toFilePayload(JsonNode node) {
        String oldPath = textOrNull(node, "old_path");
        String newPath = textOrNull(node, "new_path");
        boolean newFile = node.path("new_file").asBoolean(false);
        boolean deletedFile = node.path("deleted_file").asBoolean(false);
        boolean renamedFile = node.path("renamed_file").asBoolean(false);
        String diff = textOrNull(node, "diff");

        ChangeType type = ChangeType.fromGitlabFlags(newFile, deletedFile, renamedFile);
        // DELETED 时 GitLab new_path 仍为旧路径占位，但语义保留为"被删除的路径"
        String filePath = newPath != null ? newPath : oldPath;
        // additions / deletions 不在 GitLab REST v4 此接口；DiffParser 会自行解析 diff 文本
        return new DiffFilePayload(
                filePath,
                type == ChangeType.RENAMED ? oldPath : null,
                type.name(),
                diff,
                0,
                0,
                null);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText();
    }

    // ---------------------------------------------------------------------
    // postCommitStatus（B4-E.4）
    // ---------------------------------------------------------------------

    @Override
    public void postCommitStatus(CommitStatusRequest req, String decryptedToken) {
        if (req == null) {
            throw new WritebackException(null, null, 0, "CommitStatusRequest is null");
        }
        if (req.commitSha() == null || req.commitSha().isBlank()) {
            throw new WritebackException(req.provider(), null, 0,
                    "GitLab postCommitStatus requires commitSha");
        }
        final RepoUrlParser.Parsed parsed;
        try {
            parsed = RepoUrlParser.parse(req.repoUrl());
            OutboundUrlGuard.requirePublicHost(parsed.host(), "GitLab repository URL");
        } catch (IllegalArgumentException ex) {
            throw new WritebackException(req.provider(), req.commitSha(), 0,
                    "invalid repoUrl: " + ex.getMessage(), ex);
        }

        String encoded = URLEncoder.encode(parsed.fullPath(), StandardCharsets.UTF_8);
        final String url = "https://" + parsed.host() + "/api/v4/projects/" + encoded
                + "/statuses/" + req.commitSha();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", toGitlabState(req.state()));
        if (req.description() != null && !req.description().isEmpty()) {
            body.put("description", truncate(req.description(), 255));
        }
        if (req.targetUrl() != null && !req.targetUrl().isEmpty()) {
            body.put("target_url", req.targetUrl());
        }
        body.put("name", req.context() == null ? "acrqg/quality-gate" : req.context());
        String json = JsonUtils.toJson(body);

        try {
            restClient.post()
                    .uri(url)
                    .header("PRIVATE-TOKEN", decryptedToken)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            throw new WritebackException(req.provider(), req.commitSha(), ex.getStatusCode().value(),
                    "HTTP " + ex.getStatusCode().value(), ex);
        } catch (HttpServerErrorException ex) {
            throw new WritebackException(req.provider(), req.commitSha(), ex.getStatusCode().value(),
                    "HTTP " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw new WritebackException(req.provider(), req.commitSha(), 0,
                    "network error: " + (ex.getMessage() == null ? ex.getClass().getSimpleName()
                            : ex.getMessage()),
                    ex);
        } catch (RuntimeException ex) {
            throw new WritebackException(req.provider(), req.commitSha(), 0,
                    "postCommitStatus failed: " + (ex.getMessage() == null
                            ? ex.getClass().getSimpleName() : ex.getMessage()),
                    ex);
        }
    }

    /** GitLab 取值：pending / running / success / failed / canceled / skipped。 */
    private static String toGitlabState(CommitStatusState state) {
        if (state == null) {
            return "pending";
        }
        return switch (state) {
            case PENDING -> "pending";
            case SUCCESS -> "success";
            // GitLab 没有专门的 ERROR / FAILURE 区分，统一映射为 failed
            case FAILURE, ERROR -> "failed";
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
