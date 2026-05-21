package com.acrqg.platform.repository.client;

import com.acrqg.platform.repository.domain.Provider;
import com.acrqg.platform.repository.dto.CommitStatusRequest;
import com.acrqg.platform.repository.dto.ConnectivityResultDTO;
import com.acrqg.platform.repository.dto.DiffFetchRequest;
import com.acrqg.platform.repository.dto.DiffPayload;
import com.acrqg.platform.repository.dto.RepositoryTestRequest;

/**
 * 代码托管平台抽象客户端（design.md §6.2 / §11 / R5.1 / R10.1 / R20.1）。
 *
 * <p>三种实现按 {@link Provider} 一一对应：
 * <ul>
 *   <li>{@link GithubClient} —— github.com REST v3；</li>
 *   <li>{@link GitlabClient} —— gitlab.com REST v4；</li>
 *   <li>{@link GiteeClient}  —— gitee.com REST v5。</li>
 * </ul>
 *
 * <p>调用方应通过 {@link ProviderClientFactory#getByProvider} 取得实例，避免
 * 在业务模块内 hardcode 任一具体实现。本接口的方法签名同样镜像 design.md §6.2
 * {@code public interface ProviderClient}：
 * <pre>
 * String name();
 * void postCommitStatus(CommitStatusRequest req);     // R20.1 / R20.2
 * DiffPayload fetchDiff(DiffFetchRequest req);        // R10.1
 * boolean ping(RepositoryTestRequest req);            // R5.1
 * </pre>
 *
 * <p><b>B2-A 阶段范围</b>：
 * <ul>
 *   <li>{@link #ping} 由本任务（B2-A.3）完整实现，{@link #name()} 返回平台代码；</li>
 *   <li>{@link #fetchDiff} / {@link #postCommitStatus} 在本任务中由各实现抛出
 *       {@link UnsupportedOperationException}（"TODO: B3-C / B4-E"），
 *       后续在对应任务中替换为真实实现；调用方在 B2-A 阶段不会触发它们。</li>
 * </ul>
 *
 * <p>另外注意：design.md 把 ping 写成 {@code boolean}，但 {@code RepositoryService}
 * 需要在不可达时把 message 透传给 {@link com.acrqg.platform.common.api.ErrorCode#REPOSITORY_UNREACHABLE}
 * 的响应 details，因此本接口在保留 {@link #ping(RepositoryTestRequest)} 签名的
 * 同时返回 {@link ConnectivityResultDTO}（{@code reachable + message}）。这是
 * design 与 requirements R5.2 在落地层面的最小补全（R5.2 要求"不可访问时返回原因"）。
 *
 * <p>Covers: R5.1, R5.2, R10.1, R20.1, R20.2。
 */
public interface ProviderClient {

    /** 客户端对应的平台代码（GITHUB / GITLAB / GITEE）。 */
    String name();

    /**
     * 平台连通性测试。
     *
     * <p>实现要求：
     * <ul>
     *   <li>HTTP 请求超时 ≤ 10s；</li>
     *   <li>HTTP 200 → {@code reachable=true, message="OK"}；</li>
     *   <li>HTTP 401 / 403 → {@code reachable=false, message="invalid token"}；</li>
     *   <li>HTTP 404 → {@code reachable=false, message="repo not found"}；</li>
     *   <li>其他 4xx / 5xx → {@code reachable=false, message="unreachable: HTTP {status}"}；</li>
     *   <li>网络 / DNS / 解析异常 → {@code reachable=false, message=Throwable.getMessage()}。</li>
     * </ul>
     *
     * <p>实现 <b>不得</b> 把 access token 拼接到日志或返回 message 中（R23.3）。
     *
     * @param req 包含 provider / repoUrl / accessToken 的请求体
     * @return {@link ConnectivityResultDTO}，永不为 {@code null}
     */
    ConnectivityResultDTO ping(RepositoryTestRequest req);

    /**
     * 拉取 diff（B3-C 实现）。
     *
     * <p>本任务（B2-A）下的实现一律抛 {@link UnsupportedOperationException}
     * 提示 {@code "TODO: B3-C will implement"}，避免被误调用。
     *
     * @param req           diff 拉取参数
     * @param decryptedToken {@code RepositoryService.decryptAccessToken} 返回的明文 token
     * @return diff 载荷
     */
    DiffPayload fetchDiff(DiffFetchRequest req, String decryptedToken);

    /**
     * 回写 commit status（B4-E 实现）。
     *
     * <p>本任务（B2-A）下的实现一律抛 {@link UnsupportedOperationException}
     * 提示 {@code "TODO: B4-E will implement"}，避免被误调用。
     *
     * @param req           回写请求
     * @param decryptedToken 已解密的 access token，仅在 HTTP Authorization 头中使用
     */
    void postCommitStatus(CommitStatusRequest req, String decryptedToken);
}
