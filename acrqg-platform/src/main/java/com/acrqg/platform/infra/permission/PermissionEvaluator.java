package com.acrqg.platform.infra.permission;

import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.project.repository.ProjectMemberMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 权限校验执行器：把 {@link RequirePermission} 注解的语义翻译为具体判定。
 *
 * <p>本类是 {@link PermissionAspect} 的协作者，负责：
 * <ul>
 *   <li>{@link #hasAnyRole(AuthenticatedUser, Role[])} —— 与 JWT {@code roles} claim 严格匹配；</li>
 *   <li>{@link #isProjectMember(long, long)} —— 通过 {@link ProjectMemberMapper#roleOf}
 *       实时查询项目成员关系；</li>
 *   <li>{@link #hasProjectRole(long, long, ProjectRole[])} —— 校验项目内角色；</li>
 *   <li>{@link #evictMember(long, long)} —— 由 {@code ProjectService.removeMember} 调用，
 *       移除成员时立即清理缓存项，确保被移除的用户最迟在下一次请求被拒绝（R6.4）。</li>
 * </ul>
 *
 * <h3>缓存设计</h3>
 *
 * <p>使用 Caffeine 的内存缓存：
 * <ul>
 *   <li>key：{@code "{userId}:{projectId}"}（字符串拼接，避免引入额外的复合 key 类型）；</li>
 *   <li>value：{@link Optional}&lt;{@link ProjectRole}&gt;，{@code Optional.empty()} 表示"非成员"，
 *       这样 {@link #isProjectMember} 与 {@link #hasProjectRole} 共享同一缓存条目，
 *       避免对同一查询发起两次 SQL；</li>
 *   <li>TTL：60 秒 expireAfterWrite，配合 {@code maximumSize=10_000}
 *       兜底防止缓存无限增长；</li>
 *   <li>失效策略：被动 TTL + 主动 {@link #evictMember} —— 后者在
 *       {@code ProjectService.removeMember} 删除关联表之后立即调用，用户在该项目内的
 *       所有缓存条目被清理。</li>
 * </ul>
 *
 * <p><b>关于 ObjectProvider 注入</b>：{@link ProjectMemberMapper} 通过
 * {@link ObjectProvider} 间接注入而非直接构造器依赖，目的是允许本 bean 在
 * 单元测试或精简启动场景（无 MyBatis Mapper 扫描）下仍可被构造。
 * 当 mapper bean 不可用时，{@link #isProjectMember} 与 {@link #hasProjectRole}
 * 退化为 {@code false}（拒绝所有项目级操作），而非占位 {@code true}：
 * 这与"安全失败优先于可用失败"的原则一致。
 *
 * <p>Covers: R2.1, R2.2, R2.3, R6.4。
 */
@Component
public class PermissionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PermissionEvaluator.class);

    /** 60s TTL —— 与 design / tasks 中描述一致，避免频繁查 DB。 */
    static final Duration CACHE_TTL = Duration.ofSeconds(60);

    /** 缓存上限：10000 个 {@code (user,project)} 组合，按 LRU 驱逐。 */
    static final long CACHE_MAX_SIZE = 10_000L;

    private final ObjectProvider<ProjectMemberMapper> mapperProvider;

    /**
     * key = "userId:projectId" → Optional&lt;ProjectRole&gt;。
     * {@code Optional.empty()} 表示"非成员"。
     */
    private final Cache<String, Optional<ProjectRole>> roleCache;

    public PermissionEvaluator(ObjectProvider<ProjectMemberMapper> mapperProvider) {
        this.mapperProvider = mapperProvider;
        this.roleCache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL)
                .maximumSize(CACHE_MAX_SIZE)
                .build();
    }

    // ---------------------------------------------------------------------
    // 全局角色
    // ---------------------------------------------------------------------

    /**
     * 判断当前用户是否拥有 {@code required} 中任一全局角色。
     *
     * <p>Covers: R2.1。
     */
    public boolean hasAnyRole(AuthenticatedUser user, Role[] required) {
        if (user == null) {
            return false;
        }
        if (required == null || required.length == 0) {
            return true;
        }
        if (user.roles() == null || user.roles().isEmpty()) {
            return false;
        }
        for (Role r : required) {
            if (user.roles().contains(r.name())) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // 项目成员关系
    // ---------------------------------------------------------------------

    /**
     * 判断指定用户是否为指定项目的成员。
     *
     * <p>命中缓存直接返回；未命中则调用 {@link ProjectMemberMapper#roleOf} 一次查询，
     * 把结果（包括"非成员"作为 {@link Optional#empty()}）写入缓存。
     *
     * <p>Covers: R2.2, R6.4。
     */
    public boolean isProjectMember(long userId, long projectId) {
        return resolveRole(userId, projectId).isPresent();
    }

    /**
     * 判断指定用户在指定项目内是否拥有 {@code required} 中任一项目角色。
     *
     * <p>{@code required} 为空 / null 时退化为"项目成员即可"。
     *
     * <p>Covers: R2.3, R6.4。
     */
    public boolean hasProjectRole(long userId, long projectId, ProjectRole[] required) {
        Optional<ProjectRole> opt = resolveRole(userId, projectId);
        if (opt.isEmpty()) {
            return false;
        }
        if (required == null || required.length == 0) {
            return true;
        }
        ProjectRole actual = opt.get();
        for (ProjectRole r : required) {
            if (r == actual) {
                return true;
            }
        }
        return false;
    }

    /**
     * 主动清理缓存项。
     *
     * <p>由 {@code ProjectService.removeMember} 调用，确保移除成员后立即生效（R6.4）。
     */
    public void evictMember(long projectId, long userId) {
        roleCache.invalidate(cacheKey(userId, projectId));
    }

    // ---------------------------------------------------------------------
    // internal
    // ---------------------------------------------------------------------

    /**
     * 共享的"取角色"实现：缓存命中直接返回；未命中查 DB 并回填。
     *
     * <p>{@link Cache#get(Object, java.util.function.Function)} 已经做了
     * "并发同 key 仅查一次"的去重，无需额外锁。
     */
    private Optional<ProjectRole> resolveRole(long userId, long projectId) {
        return roleCache.get(cacheKey(userId, projectId), key -> loadFromDb(userId, projectId));
    }

    private Optional<ProjectRole> loadFromDb(long userId, long projectId) {
        ProjectMemberMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            // 无 mapper（极少见，仅在精简启动 / 单元测试场景）：安全失败
            log.debug("PermissionEvaluator: ProjectMemberMapper bean unavailable; deny userId={} projectId={}",
                    userId, projectId);
            return Optional.empty();
        }
        String code = mapper.roleOf(projectId, userId);
        if (code == null || code.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ProjectRole.valueOf(code));
        } catch (IllegalArgumentException ex) {
            // DB CHECK 约束保证不会到达这里；防御性日志一行
            log.warn("PermissionEvaluator: unexpected project_role code in DB: projectId={} userId={} code={}",
                    projectId, userId, code);
            return Optional.empty();
        }
    }

    private static String cacheKey(long userId, long projectId) {
        return userId + ":" + projectId;
    }
}
