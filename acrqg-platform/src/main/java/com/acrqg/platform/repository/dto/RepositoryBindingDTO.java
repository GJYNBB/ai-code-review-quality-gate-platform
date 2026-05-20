package com.acrqg.platform.repository.dto;

import java.time.OffsetDateTime;

/**
 * 仓库绑定对外视图（design.md §8.4）。
 *
 * <p>由 {@code RepositoryService.bind / get} 接口返回。<b>永远不会包含</b>
 * {@code accessToken} / {@code webhookSecret} / 任何加密密文字段（R5.4 / R23.3）；
 * 即便上游持有加密列，也由 Service 层在 DTO 转换时主动剥除。
 *
 * <p>Covers: R5.4, R5.5, R23.3。
 *
 * @param id             主键
 * @param projectId      项目主键
 * @param provider       代码平台代码（GITHUB / GITLAB / GITEE）
 * @param repoUrl        仓库 URL（原文）
 * @param webhookUrl     平台计算出的 Webhook 接收 URL
 * @param status         ACTIVE / INACTIVE
 * @param lastCheckedAt  最近 ping 成功时间，可为 {@code null}
 */
public record RepositoryBindingDTO(
        Long id,
        Long projectId,
        String provider,
        String repoUrl,
        String webhookUrl,
        String status,
        OffsetDateTime lastCheckedAt
) {
}
