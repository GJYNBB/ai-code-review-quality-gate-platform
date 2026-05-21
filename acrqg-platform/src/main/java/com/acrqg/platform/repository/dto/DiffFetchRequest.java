package com.acrqg.platform.repository.dto;

import com.acrqg.platform.repository.domain.Provider;

/**
 * Diff 拉取请求载荷（design.md §6.4 / R10.1）。
 *
 * <p>由 B3-C 阶段的 DiffOrchestrator 在调度时构造，传入
 * {@link com.acrqg.platform.repository.client.ProviderClient#fetchDiff
 * ProviderClient.fetchDiff}；本任务（B2-A）仅定义结构，{@code fetchDiff}
 * 留给 B3-C 实现。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code provider}：调用方需要的目标平台；与
 *       {@link com.acrqg.platform.repository.client.ProviderClientFactory#getByProvider
 *       ProviderClientFactory.getByProvider} 的入参一致；</li>
 *   <li>{@code repoUrl}：仓库 URL，{@code ProviderClient} 实现需自行从中解析
 *       owner/repo 或 host/projectPath；</li>
 *   <li>{@code prId}：Pull / Merge Request 编号；为空表示按 commit 拉取；</li>
 *   <li>{@code baseSha} / {@code headSha}：commit 拉取所需的两侧 SHA；按 PR 拉取时
 *       这两个字段可由 ProviderClient 内部回填。</li>
 * </ul>
 *
 * <p>Covers: R10.1（字段结构）；具体调用语义由 B3-C 完整实现。
 *
 * @param provider 目标平台
 * @param repoUrl  仓库 URL
 * @param prId     PR / MR 编号；可为 {@code null}
 * @param baseSha  基线 commit SHA；可为 {@code null}
 * @param headSha  当前 commit SHA；可为 {@code null}
 */
public record DiffFetchRequest(
        Provider provider,
        String repoUrl,
        String prId,
        String baseSha,
        String headSha
) {
}
