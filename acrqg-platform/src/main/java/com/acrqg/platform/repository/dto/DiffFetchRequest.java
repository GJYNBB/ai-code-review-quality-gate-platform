package com.acrqg.platform.repository.dto;

import com.acrqg.platform.repository.domain.Provider;

/**
 * Diff 拉取请求载荷（design.md §6.4 / R10.1）。
 *
 * <p>由 {@link com.acrqg.platform.diff.service.impl.DiffParserImpl} 在执行
 * {@code FETCHING_DIFF} 阶段时构造，传入
 * {@link com.acrqg.platform.repository.client.ProviderClient#fetchDiff
 * ProviderClient.fetchDiff}。
 *
 * <p>{@code accessToken} 字段由 DiffParser 通过
 * {@link com.acrqg.platform.repository.service.RepositoryService#decryptAccessToken
 * RepositoryService.decryptAccessToken} 实时解密获取；ProviderClient 实现仅
 * 在 HTTP 头中使用，<b>不得</b> 把 token 拼接到日志、URL 或返回 message 中（R23.3）。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code provider}：调用方需要的目标平台，与
 *       {@link com.acrqg.platform.repository.client.ProviderClientFactory#byProvider
 *       ProviderClientFactory.byProvider} 的入参一致；</li>
 *   <li>{@code repoUrl}：仓库 URL，{@code ProviderClient} 实现需自行从中解析
 *       owner/repo 或 host/projectPath；</li>
 *   <li>{@code prId}：Pull / Merge Request 编号；为空表示按 commit 拉取；</li>
 *   <li>{@code baseSha} / {@code headSha}：commit 拉取所需的两侧 SHA；
 *       按 PR 拉取时这两个字段可由 ProviderClient 内部回填；</li>
 *   <li>{@code accessToken}：明文 access token，仅在 HTTP Authorization 头中使用。</li>
 * </ul>
 *
 * <p>Covers: R10.1, R23.3。
 *
 * @param provider    目标平台
 * @param repoUrl     仓库 URL
 * @param prId        PR / MR 编号；可为 {@code null}
 * @param baseSha     基线 commit SHA；可为 {@code null}
 * @param headSha     当前 commit SHA；可为 {@code null}
 * @param accessToken 明文 access token
 */
public record DiffFetchRequest(
        Provider provider,
        String repoUrl,
        String prId,
        String baseSha,
        String headSha,
        String accessToken
) {
}
