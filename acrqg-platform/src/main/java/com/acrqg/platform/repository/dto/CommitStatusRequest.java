package com.acrqg.platform.repository.dto;

import com.acrqg.platform.repository.domain.Provider;

/**
 * Commit Status 回写请求载荷（design.md §6.9 / R20.1 / R20.2）。
 *
 * <p>由 B4-E 的 WritebackService 在门禁判定结束后构造，传入
 * {@link com.acrqg.platform.repository.client.ProviderClient#postCommitStatus
 * ProviderClient.postCommitStatus}；本任务（B2-A）仅定义结构占位，
 * {@code postCommitStatus} 由 B4-E 完整实现。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code provider} / {@code repoUrl}：目标平台与仓库；</li>
 *   <li>{@code commitSha}：要回写状态的 commit SHA；</li>
 *   <li>{@code state}：通用状态字面量（{@code success} / {@code failure} /
 *       {@code pending} / {@code error}），各 ProviderClient 实现负责映射到平台
 *       原生取值（GitHub: state；GitLab: state；Gitee: state）；</li>
 *   <li>{@code description}：UI 中可见的简短描述（如"AI 评审通过"或"门禁未通过"）；</li>
 *   <li>{@code context}：状态来源上下文标识（如 {@code "acrqg/quality-gate"}）；</li>
 *   <li>{@code targetUrl}：点击跳转目标，通常指向平台报告页。</li>
 * </ul>
 *
 * <p>Covers: R20.1, R20.2（结构）；语义由 B4-E 完整实现。
 *
 * @param provider    目标平台
 * @param repoUrl     仓库 URL
 * @param commitSha   commit SHA
 * @param state       通用状态字面量
 * @param description 描述
 * @param context     上下文标识
 * @param targetUrl   跳转目标 URL
 */
public record CommitStatusRequest(
        Provider provider,
        String repoUrl,
        String commitSha,
        String state,
        String description,
        String context,
        String targetUrl
) {
}
