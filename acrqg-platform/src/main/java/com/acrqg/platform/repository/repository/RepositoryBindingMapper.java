package com.acrqg.platform.repository.repository;

import com.acrqg.platform.repository.domain.RepositoryBinding;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 仓库绑定 Mapper（B2-A.2）。
 *
 * <p>继承 {@link BaseMapper} 享受标准 CRUD（{@link BaseMapper#insert insert} /
 * {@link BaseMapper#updateById updateById} / {@link BaseMapper#selectById selectById}）；
 * 额外提供按业务键查询：
 * <ul>
 *   <li>{@link #selectByProjectId} —— 按 {@code project_id} 唯一查询，对应
 *       {@code uk_repository_binding_project} 唯一约束；</li>
 *   <li>{@link #selectByRepoUrl} —— 按 {@code repo_url} 查询，主要用于运维排查
 *       "同一仓库被多个项目绑定"等异常场景。</li>
 *   <li>{@link #touchLastChecked} —— 仅刷新 {@code last_checked_at}，不引发
 *       {@code updated_at} 触发器之外的字段写入。</li>
 * </ul>
 *
 * <p>Covers: R5.4, R5.6。
 */
public interface RepositoryBindingMapper extends BaseMapper<RepositoryBinding> {

    /**
     * 按 {@code project_id} 唯一查询。
     *
     * @param projectId 项目主键
     * @return 命中的 {@link RepositoryBinding}；未命中返回 {@code null}
     */
    @Select("SELECT id, project_id, provider, repo_url, "
            + "       access_token_encrypted, webhook_secret_encrypted, "
            + "       webhook_url, status, last_checked_at, created_at, updated_at "
            + "  FROM repository_binding "
            + " WHERE project_id = #{projectId}")
    RepositoryBinding selectByProjectId(@Param("projectId") Long projectId);

    /**
     * 按 {@code repo_url} 查询（首条命中即返回；不同项目可能绑定不同 URL）。
     *
     * @param repoUrl 仓库 URL
     * @return 命中的 {@link RepositoryBinding}；未命中返回 {@code null}
     */
    @Select("SELECT id, project_id, provider, repo_url, "
            + "       access_token_encrypted, webhook_secret_encrypted, "
            + "       webhook_url, status, last_checked_at, created_at, updated_at "
            + "  FROM repository_binding "
            + " WHERE repo_url = #{repoUrl} "
            + " ORDER BY id ASC LIMIT 1")
    RepositoryBinding selectByRepoUrl(@Param("repoUrl") String repoUrl);

    /** 按 provider + repo_url 查询启用中的绑定，供公开 webhook 入口使用。 */
    @Select("SELECT id, project_id, provider, repo_url, "
            + "       access_token_encrypted, webhook_secret_encrypted, "
            + "       webhook_url, status, last_checked_at, created_at, updated_at "
            + "  FROM repository_binding "
            + " WHERE provider = #{provider} "
            + "   AND repo_url = #{repoUrl} "
            + "   AND status = 'ACTIVE' "
            + " ORDER BY id ASC LIMIT 1")
    RepositoryBinding selectActiveByProviderAndRepoUrl(@Param("provider") String provider,
                                                       @Param("repoUrl") String repoUrl);

    /**
     * 仅刷新 {@code last_checked_at}（不修改其他字段）。
     *
     * <p>{@code updated_at} 由 {@code trg_repository_binding_updated} 触发器自动维护；
     * 本方法用于 {@link com.acrqg.platform.repository.client.ProviderClient#ping
     * ProviderClient.ping} 成功后的轻量回写。
     *
     * @param id      主键
     * @param checkedAt 期望的 {@code last_checked_at} 时间戳，使用
     *                {@code java.time.OffsetDateTime}
     * @return 受影响行数
     */
    @Update("UPDATE repository_binding "
            + "   SET last_checked_at = #{checkedAt} "
            + " WHERE id = #{id}")
    int touchLastChecked(@Param("id") Long id,
                         @Param("checkedAt") java.time.OffsetDateTime checkedAt);
}
