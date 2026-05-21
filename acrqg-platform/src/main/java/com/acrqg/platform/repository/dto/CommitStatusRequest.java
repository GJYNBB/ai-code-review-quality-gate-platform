package com.acrqg.platform.repository.dto;

import com.acrqg.platform.repository.domain.Provider;

/**
 * Commit Status 回写请求载荷（design.md §6.9 / R20.1 / R20.2）。
 *
 * <p>由 B4-E 的 WritebackService 在门禁判定结束后构造，传入
 * {@link com.acrqg.platform.repository.client.ProviderClient#postCommitStatus
 * ProviderClient.postCommitStatus}。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code provider} / {@code repoUrl}：目标平台与仓库；</li>
 *   <li>{@code commitSha}：要回写状态的 commit SHA；</li>
 *   <li>{@code state}：通用状态枚举（{@link CommitStatusState}），各
 *       ProviderClient 实现负责映射到平台原生取值；</li>
 *   <li>{@code description}：UI 中可见的简短描述（如"score=80, critical=0"
 *       或"已豁免：原因..."）；GitHub/Gitee 限制 ≤ 140 字，本字段在使用方
 *       自行截断；</li>
 *   <li>{@code context}：状态来源上下文标识（如 {@code "acrqg/quality-gate"}），
 *       GitHub/Gitee 用作 status context；GitLab 用作 status name；</li>
 *   <li>{@code targetUrl}：点击跳转目标，通常指向平台报告页。</li>
 * </ul>
 *
 * <p>access token <b>不</b>包含在本请求中：调用方应通过
 * {@link com.acrqg.platform.repository.client.ProviderClient#postCommitStatus(CommitStatusRequest, String)
 * postCommitStatus} 的第二个参数 {@code decryptedToken} 单独传入，避免 token 与
 * 业务字段混在一个 record 中导致序列化日志泄漏。
 *
 * <p>Covers: R20.1, R20.2, R23.3。
 *
 * @param provider    目标平台
 * @param repoUrl     仓库 URL
 * @param commitSha   commit SHA
 * @param state       通用状态枚举
 * @param description 描述（建议 ≤ 140 字）
 * @param context     上下文标识（如 {@code "acrqg/quality-gate"}）
 * @param targetUrl   跳转目标 URL
 */
public record CommitStatusRequest(
        Provider provider,
        String repoUrl,
        String commitSha,
        CommitStatusState state,
        String description,
        String context,
        String targetUrl
) {
}
