package com.acrqg.platform.repository.client;

import com.acrqg.platform.repository.domain.Provider;
import com.acrqg.platform.repository.dto.ConnectivityResultDTO;
import com.acrqg.platform.repository.dto.RepositoryTestRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Gitee REST v5 实现（design.md §6.2 / §11）。
 *
 * <p>{@link #ping(RepositoryTestRequest)} 命中
 * {@code GET https://gitee.com/api/v5/repos/{owner}/{repo}}，
 * 携带 {@code Authorization: token {accessToken}}。Gitee 的私有仓库要求
 * 把 token 放在 Authorization 头中（也支持 access_token query 参数；
 * 这里统一选 header 方式，避免 token 出现在 URL 与日志）。
 *
 * <p>HTTP 状态映射由父类 {@link AbstractProviderClient#runPing} 统一处理。
 *
 * <p>Covers: R5.1, R5.2。
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
}
