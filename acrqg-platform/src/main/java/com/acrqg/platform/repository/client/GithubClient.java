package com.acrqg.platform.repository.client;

import com.acrqg.platform.repository.domain.Provider;
import com.acrqg.platform.repository.dto.ConnectivityResultDTO;
import com.acrqg.platform.repository.dto.RepositoryTestRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * GitHub REST v3 实现（design.md §6.2 / §11）。
 *
 * <p>{@link #ping(RepositoryTestRequest)} 命中
 * {@code GET https://api.github.com/repos/{owner}/{repo}}，
 * 携带 {@code Authorization: Bearer {token}} 与
 * {@code Accept: application/vnd.github+json}。
 *
 * <p>HTTP 状态映射在父类 {@link AbstractProviderClient#runPing} 中统一处理：
 * <ul>
 *   <li>200 → {@code reachable=true / "OK"}；</li>
 *   <li>401 / 403 → {@code reachable=false / "invalid token"}；</li>
 *   <li>404 → {@code reachable=false / "repo not found"}；</li>
 *   <li>其他 4xx / 5xx → {@code reachable=false / "unreachable: HTTP {code}"}；</li>
 *   <li>网络异常 → {@code reachable=false / "unreachable: ..."}。</li>
 * </ul>
 *
 * <p>Covers: R5.1, R5.2。
 */
@Component
public class GithubClient extends AbstractProviderClient {

    /** GitHub.com REST API 根。GitHub Enterprise 自建实例的 host 由 repoUrl 推导，此处不做支持。 */
    private static final String API_ROOT = "https://api.github.com";

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
}
