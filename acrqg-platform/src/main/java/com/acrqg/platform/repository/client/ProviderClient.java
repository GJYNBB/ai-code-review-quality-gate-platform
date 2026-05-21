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
 * <p>调用方应通过 {@link ProviderClientFactory#byProvider} 取得实例，避免
 * 在业务模块内 hardcode 任一具体实现。
 *
 * <p><b>实现状态</b>：
 * <ul>
 *   <li>{@link #ping}      —— B2-A.3 完整实现；</li>
 *   <li>{@link #fetchDiff} —— B3-C.3 完整实现；网络 / 4xx / 5xx 异常抛
 *       {@link DiffFetchException}；</li>
 *   <li>{@link #postCommitStatus} —— B4-E 实现；当前抛
 *       {@link UnsupportedOperationException}。</li>
 * </ul>
 *
 * <p>另外注意：design.md 把 ping 写成 {@code boolean}，但 {@code RepositoryService}
 * 需要在不可达时把 message 透传给 {@link com.acrqg.platform.common.api.ErrorCode#REPOSITORY_UNREACHABLE}
 * 的响应 details，因此本接口在保留 {@link #ping(RepositoryTestRequest)} 签名的
 * 同时返回 {@link ConnectivityResultDTO}。这是 design 与 requirements R5.2
 * 在落地层面的最小补全。
 *
 * <p>Covers: R5.1, R5.2, R10.1, R10.4, R20.1, R20.2, R23.3。
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
     *   <li>网络 / DNS / 解析异常 → {@code reachable=false, message=...}。</li>
     * </ul>
     *
     * <p>实现 <b>不得</b> 把 access token 拼接到日志或返回 message 中（R23.3）。
     *
     * @param req 包含 provider / repoUrl / accessToken 的请求体
     * @return {@link ConnectivityResultDTO}，永不为 {@code null}
     */
    ConnectivityResultDTO ping(RepositoryTestRequest req);

    /**
     * 拉取 PR / MR 的变更文件 diff（B3-C.3）。
     *
     * <p>语义：
     * <ul>
     *   <li>访问平台 REST API 列出 PR/MR 的变更文件，构造
     *       {@link DiffPayload}；</li>
     *   <li>支持分页（GitHub / Gitee 的 {@code page=&per_page=}；GitLab 一次性返回
     *       changes 数组），分页迭代上限由实现按 {@code MAX_PAGES} 兜底；</li>
     *   <li>4xx / 5xx 与网络异常一律封装为 {@link DiffFetchException}（R10.4）；</li>
     *   <li>返回的 {@link DiffPayload#files()} 列表保持平台返回顺序，重复文件名由
     *       上游 DiffParser 通过 {@code uk_diff_file_task_path} 兜底；</li>
     *   <li>实现 <b>不得</b> 把 access token 写入日志 / message（R23.3）。</li>
     * </ul>
     *
     * @param req 拉取参数（含 accessToken）
     * @return diff 载荷，永不为 {@code null}
     * @throws DiffFetchException 网络 / HTTP 错误
     */
    DiffPayload fetchDiff(DiffFetchRequest req);

    /**
     * 回写 commit status（B4-E 实现）。
     *
     * <p>实现要求：
     * <ul>
     *   <li>构造平台原生 status 请求，依各 provider 的 API 规范映射 state 字面量；</li>
     *   <li>HTTP 请求超时 ≤ 10s（与 ping / fetchDiff 一致）；</li>
     *   <li>4xx / 5xx / 网络异常一律抛
     *       {@link com.acrqg.platform.writeback.exception.WritebackException
     *       WritebackException}，由 WritebackService 决定是否重试（4xx 不重试，5xx 重试）；</li>
     *   <li>实现 <b>不得</b> 把 access token 写入日志或异常 message。</li>
     * </ul>
     *
     * @param req           回写请求
     * @param decryptedToken 已解密的 access token，仅在 HTTP Authorization 头中使用
     * @throws com.acrqg.platform.writeback.exception.WritebackException 网络 / HTTP 错误
     */
    void postCommitStatus(CommitStatusRequest req, String decryptedToken);
}
