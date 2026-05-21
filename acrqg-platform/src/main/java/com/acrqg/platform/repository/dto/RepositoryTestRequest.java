package com.acrqg.platform.repository.dto;

import com.acrqg.platform.repository.domain.Provider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * 仓库连通性测试请求体（design.md §8.4 / R5.1）。
 *
 * <p>对应接口：{@code POST /api/v1/projects/{id}/repository/test}。仅做一次
 * 同步 ProviderClient.ping 调用，<b>不会持久化</b>。返回 {@link ConnectivityResultDTO}。
 *
 * <p>字段约束：
 * <ul>
 *   <li>{@code provider}：必填，{@link Provider} 枚举之一；</li>
 *   <li>{@code repoUrl}：必填，长度 ≤ 512，且符合 URL 形式（hibernate-validator
 *       {@link URL @URL}）；</li>
 *   <li>{@code accessToken}：必填，长度 8..512。下限 8 字节用于过滤明显的占位输入，
 *       上限 512 字节覆盖主流平台的 PAT 长度。</li>
 * </ul>
 *
 * <p>Covers: R5.1, R5.2。
 *
 * @param provider    代码平台
 * @param repoUrl     仓库 URL
 * @param accessToken 平台 PAT / 访问令牌
 */
public record RepositoryTestRequest(
        @NotNull Provider provider,
        @NotBlank @Size(max = 512) @URL String repoUrl,
        @NotBlank @Size(min = 8, max = 512) String accessToken
) {
}
