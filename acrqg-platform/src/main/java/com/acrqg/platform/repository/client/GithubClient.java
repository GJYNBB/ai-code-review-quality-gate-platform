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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * GitHub REST v3 实现（design.md §6.2 / §6.4 / §11）。
 *
 * <p>Endpoint:
 * <ul>
 *   <li>{@link #ping}      —— {@code GET /repos/{owner}/{repo}}；</li>
 *   <li>{@link #fetchDiff} —— {@code GET /repos/{owner}/{repo}/pulls/{prId}/files}，
 *       分页迭代（{@code page=&per_page=100}），通过 {@code Link} 头的
 *       {@code rel="next"} 探测下一页。最多迭代 {@link #MAX_PAGES} 页。</li>
 * </ul>
 *
 * <p>Header:
 * <ul>
 *   <li>{@code Authorization: Bearer {token}}；</li>
 *   <li>{@code Accept: application/vnd.github+json}；</li>
 *   <li>{@code X-GitHub-Api-Version: 2022-11-28}。</li>
 * </ul>
 *
 * <p>响应映射：
 * <pre>
 *   filename          → filePath
 *   previous_filename → oldPath（仅 RENAMED）
 *   status            → ChangeType.fromGithubLike(status)
 *   patch             → patch（可能为 null：二进制 / 大文件）
 *   additions         → additions
 *   deletions         → deletions
 *   sha               → sha
 * </pre>
 *
 * <p>HTTP 状态映射：4xx / 5xx 与网络异常一律抛 {@link DiffFetchException}（R10.4）。
 *
 * <p>Covers: R5.1, R5.2, R10.1, R10.4, R23.3。
 */
@Component
public class GithubClient extends AbstractProviderClient {

    /** GitHub.com REST API 根。GitHub Enterprise 自建实例的 host 由 repoUrl 推导，此处不做支持。 */
    private static final String API_ROOT = "https://api.github.com";

    /** 单页大小，GitHub 对 pulls/files 接口最大 per_page=100。 */
    private static final int PER_PAGE = 100;

    /** Link 头中 rel="next" 的链接 URL 提取正则。 */
    private static final Pattern LINK_NEXT_PATTERN =
            Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"next\"");

    @Override
    public String name() {
        return Provider.GITHUB.name();
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
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + req.accessToken())
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
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
                    "GitHub fetchDiff requires prId");
        }
        final RepoUrlParser.Parsed parsed;
        try {
            parsed = RepoUrlParser.parse(req.repoUrl());
        } catch (IllegalArgumentException ex) {
            throw new DiffFetchException(req.provider(), req.repoUrl(), req.prId(),
                    "invalid repoUrl: " + ex.getMessage(), ex);
        }

        String currentUrl = API_ROOT + "/repos/" + parsed.owner() + "/" + parsed.repo()
                + "/pulls/" + req.prId() + "/files?page=1&per_page=" + PER_PAGE;
        List<DiffFilePayload> all = new ArrayList<>();

        for (int page = 0; page < MAX_PAGES && currentUrl != null; page++) {
            ResponseEntity<String> resp;
            try {
                resp = restClient.get()
                        .uri(currentUrl)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + req.accessToken())
                        .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
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
                        "invalid JSON response from GitHub", ex);
            }
            if (!root.isArray()) {
                throw new DiffFetchException(req.provider(), req.repoUrl(), req.prId(),
                        "expected JSON array from GitHub /pulls/{prId}/files");
            }
            for (JsonNode node : root) {
                all.add(toFilePayload(node));
            }

            currentUrl = nextLink(resp.getHeaders().getFirst(HttpHeaders.LINK));
        }

        if (currentUrl != null) {
            // 仍然有下一页但已达到上限：保守处理，记 WARN 但不抛异常，避免极端规模 PR 导致整任务失败
            log.warn("GitHub fetchDiff hit MAX_PAGES={} for repo={} pr={}; truncating",
                    MAX_PAGES, parsed.fullPath(), req.prId());
        }

        return new DiffPayload(all);
    }

    /** 把 GitHub /pulls/{prId}/files 单个文件节点映射为 {@link DiffFilePayload}。 */
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

    /** 从 Link 头中解析 rel="next" 的下一页 URL；不存在返回 {@code null}。 */
    static String nextLink(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) {
            return null;
        }
        Matcher m = LINK_NEXT_PATTERN.matcher(linkHeader);
        return m.find() ? m.group(1) : null;
    }
}
