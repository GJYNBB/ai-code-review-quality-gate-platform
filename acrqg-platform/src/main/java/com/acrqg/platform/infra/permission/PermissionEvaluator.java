package com.acrqg.platform.infra.permission;

import com.acrqg.platform.infra.security.AuthenticatedUser;
import org.springframework.stereotype.Component;

/**
 * 权限校验执行器：把 {@link RequirePermission} 注解的语义翻译为具体判定。
 *
 * <p>本类是 {@link PermissionAspect} 的协作者，目的是将"AOP 拦截 + 参数解析"与
 * "具体的角色 / 成员关系判定"分离，便于后续替换实现（例如把 {@link #isProjectMember}
 * 与 {@link #hasProjectRole} 改成基于 ProjectMemberMapper + Caffeine 的真实查询）
 * 而不影响切面代码。
 *
 * <h3>当前实现状态（B0-A.7 阶段）</h3>
 * <ul>
 *   <li>{@link #hasAnyRole(AuthenticatedUser, Role[])} —— <b>已完整实现</b>，
 *       与 JWT 中的 {@code roles} claim 字符串严格匹配。</li>
 *   <li>{@link #isProjectMember(long, long)} —— <b>占位实现</b>，恒返回 {@code true}
 *       以避免在 {@code project_member} 表 / Service 落地之前阻塞已加注解的接口；
 *       将由 <b>B1-C.6</b> 替换为基于 {@code ProjectMemberMapper} 的真实查询，
 *       并叠加 60s Caffeine 缓存。</li>
 *   <li>{@link #hasProjectRole(long, long, ProjectRole[])} —— <b>占位实现</b>，
 *       恒返回 {@code true}；同样由 <b>B1-C.6</b> 实现。</li>
 * </ul>
 *
 * <p>占位返回 {@code true}（而非抛异常或返回 {@code false}）的设计原因：B0-A 阶段
 * 没有任何 Controller 真正使用 {@code projectMember=true}，将占位返回 {@code true}
 * 不会放过任何实际的越权请求；而一旦 B1-C 把真实判定接入，所有项目级注解会自动收紧。
 * 反过来若占位为 {@code false}，B1-A / B1-D 中可能合法的全局角色接口若误标
 * {@code projectMember=true}，将立即被拦死，反而难以发现。
 *
 * <p>Covers: R2.1（{@link #hasAnyRole} 已交付）；
 * R2.2 / R2.3 / R6.4（{@link #isProjectMember} / {@link #hasProjectRole}
 * 占位，B1-C.6 完整交付）。
 */
@Component
public class PermissionEvaluator {

    /**
     * 判断当前用户是否拥有 {@code required} 中任一全局角色。
     *
     * <p>判定规则：
     * <ul>
     *   <li>{@code user == null} → 返回 {@code false}（未登录）。</li>
     *   <li>{@code required == null} 或长度为 0 → 返回 {@code true}（不限制全局角色）。</li>
     *   <li>否则取 {@link AuthenticatedUser#roles()} 与 {@code required} 的交集；
     *       不为空即放行。比对使用 {@link Role#name()} 字符串严格相等。</li>
     * </ul>
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

    /**
     * 判断指定用户是否为指定项目的成员。
     *
     * <p><b>占位实现</b>：B0-A.7 阶段恒返回 {@code true}。
     *
     * <p>TODO B1-C.6: 替换为基于 {@code ProjectMemberMapper.isMember(projectId, userId)}
     * 的真实查询，并叠加 Caffeine 缓存（key={@code "{userId}:{projectId}"}, TTL=60s）。
     * 项目成员被移除时由 {@code ProjectService.removeMember} 主动清理对应缓存项。
     *
     * <p>Covers: R2.2, R6.4（占位 → B1-C.6 完整交付）。
     */
    public boolean isProjectMember(long userId, long projectId) {
        // TODO B1-C.6: implement via ProjectMemberMapper + Caffeine cache (TTL=60s)
        return true;
    }

    /**
     * 判断指定用户在指定项目内是否拥有 {@code required} 中任一项目角色。
     *
     * <p><b>占位实现</b>：B0-A.7 阶段恒返回 {@code true}。
     *
     * <p>TODO B1-C.6: 通过 {@code ProjectMemberMapper.roleOf(projectId, userId)}
     * 查询当前项目角色，与 {@code required} 求交集；同样叠加 Caffeine 缓存。
     *
     * <p>Covers: R2.3（占位 → B1-C.6 完整交付）。
     */
    public boolean hasProjectRole(long userId, long projectId, ProjectRole[] required) {
        // TODO B1-C.6: implement via ProjectMemberMapper + Caffeine cache (TTL=60s)
        return true;
    }
}
