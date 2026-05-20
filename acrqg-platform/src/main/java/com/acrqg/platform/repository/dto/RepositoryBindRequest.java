package com.acrqg.platform.repository.dto;

import com.acrqg.platform.repository.domain.Provider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * 仓库绑定请求体（design.md §8.4 / R5.3 / R5.4 / R5.5 / R5.6）。
 *
 * <p>对应接口：{@code POST /api/v1/projects/{id}/repository}。Service 层会：
 * <ol>
 *   <li>先调用 {@link com.acrqg.platform.repository.client.ProviderClient#ping
 *       ProviderClient.ping} 校验连通性，失败抛 {@code REPOSITORY_UNREACHABLE}；</li>
 *   <li>使用 {@link com.acrqg.platform.infra.crypto.TokenEncryptor TokenEncryptor}
 *       将 {@code accessToken} / {@code webhookSecret} 加密后落库（R5.3 / R23.2）；</li>
 *   <li>计算并保存 {@code webhookUrl = {app.platform.base-url}/api/v1/webhooks/git}
 *       （R5.5）；</li>
 *   <li>{@code project_id} 唯一约束 {@code uk_repository_binding_project} 保证一项目一绑定
 *       （R5.6）；同 projectId 重复绑定走 UPDATE 路径。</li>
 * </ol>
 *
 * <p>字段约束：
 * <ul>
 *   <li>{@code accessToken}：8..512 字节（与 GitHub PAT / GitLab PAT / Gitee Token 兼容）；</li>
 *   <li>{@code webhookSecret}：8..256 字节，用于 HMAC-SHA256 签名校验（R7.1）；</li>
 *   <li>所有字段 {@link NotBlank @NotBlank}，不允许空白字符串。</li>
 * </ul>
 *
 * <p>Covers: R5.3, R5.4, R5.5, R5.6。
 *
 * @param provider      代码平台
 * @param repoUrl       仓库 URL
 * @param accessToken   平台 PAT / 访问令牌（明文；落库前加密）
 * @param webhookSecret Webhook 签名密钥（明文；落库前加密）
 */
public record RepositoryBindRequest(
        @NotNull Provider provider,
        @NotBlank @Size(max = 512) @URL String repoUrl,
        @NotBlank @Size(min = 8, max = 512) String accessToken,
        @NotBlank @Size(min = 8, max = 256) String webhookSecret
) {
}
