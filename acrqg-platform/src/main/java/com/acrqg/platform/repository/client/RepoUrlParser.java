package com.acrqg.platform.repository.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * 仓库 URL 解析工具：从 {@code https://host/owner/repo[.git]} 形式中
 * 抽取 host / owner / repo / fullPath 字段，供 {@link GithubClient} /
 * {@link GitlabClient} / {@link GiteeClient} 拼接 REST API URL。
 *
 * <p>支持的输入示例：
 * <ul>
 *   <li>{@code https://github.com/octocat/Hello-World} → owner=octocat / repo=Hello-World</li>
 *   <li>{@code https://github.com/octocat/Hello-World.git} → 同上（去掉 {@code .git} 后缀）</li>
 *   <li>{@code https://gitlab.com/group/sub/proj} → owner=group / repo=proj /
 *       fullPath="group/sub/proj"（GitLab 需要完整 namespace 路径）</li>
 *   <li>{@code https://gitee.com/owner/repo} → owner=owner / repo=repo</li>
 * </ul>
 *
 * <p>仅做"格式裁剪"，不做平台白名单匹配；调用方按各自需求使用 {@link Parsed#owner()}
 * / {@link Parsed#repo()} / {@link Parsed#fullPath()}。
 *
 * <p>Covers: R5.1（解析 repoUrl 用于平台 API 拼接）。
 */
final class RepoUrlParser {

    private RepoUrlParser() {
    }

    /** 解析结果。{@code fullPath} 已去掉前后斜杠与 {@code .git} 后缀。 */
    record Parsed(String host, String owner, String repo, String fullPath) {

        Parsed {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(repo, "repo");
            Objects.requireNonNull(fullPath, "fullPath");
        }
    }

    /**
     * 解析仓库 URL。
     *
     * @param repoUrl 形如 {@code https://github.com/owner/repo[.git]}
     * @return {@link Parsed}
     * @throws IllegalArgumentException 当 URL 缺少 host 或路径不足两段时
     */
    static Parsed parse(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("repoUrl must not be blank");
        }
        URI uri;
        try {
            uri = new URI(repoUrl.trim());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("invalid repoUrl: " + repoUrl, ex);
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("repoUrl missing host: " + repoUrl);
        }
        String path = uri.getPath();
        if (path == null) {
            throw new IllegalArgumentException("repoUrl missing path: " + repoUrl);
        }
        // 去掉前后斜杠
        String cleaned = path;
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        // 去掉 .git 后缀
        if (cleaned.endsWith(".git")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4);
        }
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("repoUrl missing path segments: " + repoUrl);
        }
        String[] segs = cleaned.split("/");
        if (segs.length < 2) {
            throw new IllegalArgumentException("repoUrl path must have at least owner/repo: " + repoUrl);
        }
        String owner = segs[0];
        String repo = segs[segs.length - 1];
        return new Parsed(host, owner, repo, cleaned);
    }
}
