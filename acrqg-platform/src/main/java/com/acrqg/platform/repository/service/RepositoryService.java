package com.acrqg.platform.repository.service;

import com.acrqg.platform.repository.dto.ConnectivityResultDTO;
import com.acrqg.platform.repository.dto.RepositoryBindRequest;
import com.acrqg.platform.repository.dto.RepositoryBindingDTO;
import com.acrqg.platform.repository.dto.RepositoryTestRequest;

/**
 * 仓库绑定（M02）服务接口。
 *
 * <p>对齐 design.md §6.2：
 * <pre>
 * public interface RepositoryService {
 *     ConnectivityResultDTO test(Long projectId, RepositoryTestRequest req);
 *     RepositoryBindingDTO bind(Long projectId, RepositoryBindRequest req);
 *     RepositoryBindingDTO get(Long projectId);
 *     String decryptAccessToken(Long projectId);
 *     String decryptWebhookSecret(Long projectId);
 * }
 * </pre>
 *
 * <p>异常约定：
 * <ul>
 *   <li>{@code test}：项目必须存在，否则抛 {@code BusinessException(VALIDATION_ERROR, "项目不存在")}；
 *       连通性失败<b>不</b>抛异常，结果由 {@link ConnectivityResultDTO#reachable()} 表达。</li>
 *   <li>{@code bind}：先 {@link #test} 通；不可达 → {@code REPOSITORY_UNREACHABLE}（R5.2）。
 *       同 {@code projectId} 重复绑定时走 UPDATE 路径，依赖
 *       {@code uk_repository_binding_project} 唯一约束 +
 *       {@link org.springframework.dao.DuplicateKeyException} 兜底（R5.6）。</li>
 *   <li>{@code get}：未绑定 → {@code BusinessException(VALIDATION_ERROR, "仓库未绑定")}。</li>
 *   <li>{@code decryptAccessToken / decryptWebhookSecret}：未绑定 → 同上；密文损坏抛
 *       {@code IllegalArgumentException}（由 {@link com.acrqg.platform.infra.crypto.TokenEncryptor}
 *       透传），由调用方决定是否做容错处理。</li>
 * </ul>
 *
 * <p>所有变更操作发布 {@code AuditEvent}：
 * <ul>
 *   <li>新建 → {@code REPOSITORY_BOUND}；</li>
 *   <li>更新 → {@code REPOSITORY_UPDATED}；</li>
 *   <li>{@code accessToken / webhookSecret} 字段在 detail 中以 {@code MaskUtils.FULL_MASK} 替换（R23.3）。</li>
 * </ul>
 *
 * <p>Covers: R5.1, R5.2, R5.3, R5.4, R5.5, R5.6, R23.2, R23.3。
 */
public interface RepositoryService {

    /**
     * 仅测试连通性，不持久化（R5.1）。
     *
     * @param projectId 项目主键，仅用于权限切面校验，与底层 ping 调用无关
     * @param request   测试请求
     * @return 连通性结果，永不为 {@code null}
     */
    ConnectivityResultDTO test(Long projectId, RepositoryTestRequest request);

    /**
     * 绑定（或更新）项目仓库（R5.3 / R5.4 / R5.5 / R5.6）。
     *
     * <p>顺序：
     * <ol>
     *   <li>先调用 {@link #test} 确认可达；不可达抛 {@code REPOSITORY_UNREACHABLE}；</li>
     *   <li>使用 {@link com.acrqg.platform.infra.crypto.TokenEncryptor} 加密
     *       {@code accessToken / webhookSecret}；</li>
     *   <li>计算 {@code webhookUrl}（{@code app.webhook.base-url} + {@code /api/v1/webhooks/git}）；</li>
     *   <li>INSERT 或按 projectId UPSERT。</li>
     * </ol>
     *
     * @param projectId 项目主键
     * @param request   绑定请求
     * @return 绑定后的视图（不含明文 token / secret）
     */
    RepositoryBindingDTO bind(Long projectId, RepositoryBindRequest request);

    /**
     * 查询项目当前绑定。返回的 DTO 保证不含密文字段（R5.4 / R23.3）。
     *
     * @param projectId 项目主键
     * @return DTO；未绑定时抛 {@code BusinessException(VALIDATION_ERROR)}
     */
    RepositoryBindingDTO get(Long projectId);

    /**
     * 内部 API：返回明文 access token，供 B3-B / B3-C / B4-E 调用平台 REST API。
     *
     * <p>本方法不做权限校验；调用方必须保证已通过 Service 层或 Worker 内部上下文授权。
     */
    String decryptAccessToken(Long projectId);

    /**
     * 内部 API：返回明文 webhook secret，供 B3-B 验证 HMAC-SHA256 签名。
     */
    String decryptWebhookSecret(Long projectId);
}
