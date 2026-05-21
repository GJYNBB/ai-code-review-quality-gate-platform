package com.acrqg.platform.repository.client;

import com.acrqg.platform.repository.domain.Provider;
import com.acrqg.platform.repository.dto.ConnectivityResultDTO;
import com.acrqg.platform.repository.dto.RepositoryTestRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * GitLab REST v4 实现（design.md §6.2 / §11）。
 *
 * <p>{@link #ping(RepositoryTestRequest)} 命中
 * {@code GET https://{host}/api/v4/projects/{url-encoded full path}}，
 * 携带 {@code PRIVATE-TOKEN: {token}}。GitLab 的项目可能包含子组（group/sub/proj），
 * 因此使用 URL 编码后的 fullPath 作为唯一定位。
 *
 * <p>使用 {@code repoUrl} 中的 host 而非硬编码 gitlab.com，以兼容自建 GitLab。
 *
 * <p>HTTP 状态映射由父类 {@link AbstractProviderClient#runPing} 统一处理。
 *
 * <p>Covers: R5.1, R5.2。
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
}
