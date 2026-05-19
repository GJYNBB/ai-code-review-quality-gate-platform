package com.acrqg.platform.infra.permission;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import java.lang.reflect.Method;
import java.util.Optional;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 基于 {@link RequirePermission} 注解的 AOP 权限切面。
 *
 * <p>在请求方法执行前完成三层校验：
 * <ol>
 *   <li>当前是否已认证（{@link CurrentUserHolder#optional()} 是否非空）；</li>
 *   <li>全局角色是否满足 {@link RequirePermission#role()}；</li>
 *   <li>当 {@link RequirePermission#projectMember()} 为 {@code true} 时，
 *       从 {@code @PathVariable} / {@code @RequestParam} 解析 {@code projectId}，
 *       校验项目成员关系与项目角色。</li>
 * </ol>
 *
 * <p>失败时抛出 {@link AccessDeniedException}，由 {@code GlobalExceptionHandler}
 * 统一映射为 HTTP 403 / {@code PERMISSION_DENIED}。当 {@code projectMember=true} 但
 * 请求中无法解析出 {@code projectId} 时，抛 {@link BusinessException} 并返回
 * {@link ErrorCode#VALIDATION_ERROR}（语义"接口设计错误"，便于在开发期立刻发现注解
 * 与路由不匹配）。
 *
 * <h3>切点设计</h3>
 *
 * <p>注解既可写在方法上也可写在类上，因此本切面使用单一拦截范围 + 反射查找的策略：
 * 拦截 {@code com.acrqg.platform..controller..*(..)} 下的所有公有方法，先取方法上的
 * {@link RequirePermission}，没有则回退到声明类（包括类继承层次）。这样避免了
 * AspectJ 同时使用 {@code @annotation} 与 {@code @within} 时绑定参数的复杂性。
 *
 * <h3>切面顺序</h3>
 *
 * <p>使用 {@code @Order(HIGHEST_PRECEDENCE + 100)}：在 {@code TraceIdFilter}、
 * {@code JwtAuthFilter} 之后（这两个由 Servlet Filter 链先执行），但比业务事务切面
 * 早，确保越权请求不会进入数据库事务即被拒绝。
 *
 * <p>Covers: R2.1, R2.2, R2.3, R2.4, R2.5。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class PermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

    private final PermissionEvaluator evaluator;

    public PermissionAspect(PermissionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * 拦截所有 controller 包下的方法，统一进行权限校验。
     *
     * <p>方法上有 {@link RequirePermission} 时优先使用方法注解；否则回退到声明类
     * （含父类）上的注解；二者都不存在则视为公开接口，直接放行。
     */
    @Before("execution(public * com.acrqg.platform..controller..*(..))")
    public void check(JoinPoint jp) {
        RequirePermission rp = findAnnotation(jp);
        if (rp == null) {
            return;
        }

        // 1) 当前用户
        Optional<AuthenticatedUser> opt = CurrentUserHolder.optional();
        boolean needLogin = rp.role().length > 0 || rp.projectMember();
        if (opt.isEmpty()) {
            if (needLogin) {
                if (log.isDebugEnabled()) {
                    log.debug("PermissionAspect deny: no current user, requireRole={}, projectMember={}",
                            rp.role().length, rp.projectMember());
                }
                throw new AccessDeniedException("PERMISSION_DENIED: no current user");
            }
            // 注解存在但既不限制角色也不限制项目成员（极少见）；放行
            return;
        }
        AuthenticatedUser user = opt.get();

        // 2) 全局角色
        if (rp.role().length > 0 && !evaluator.hasAnyRole(user, rp.role())) {
            if (log.isDebugEnabled()) {
                log.debug("PermissionAspect deny: userId={} roles={} requireRole={} -> role mismatch",
                        user.id(), user.roles(), rolesAsString(rp.role()));
            }
            throw new AccessDeniedException("PERMISSION_DENIED: role");
        }

        // 3) 项目成员 / 项目角色
        if (rp.projectMember()) {
            Long projectId = ParamResolver.resolveLong(jp, rp.projectIdParam());
            if (projectId == null) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "missing project id parameter '" + rp.projectIdParam() + "'");
            }
            if (!evaluator.isProjectMember(user.id(), projectId)) {
                if (log.isDebugEnabled()) {
                    log.debug("PermissionAspect deny: userId={} projectId={} -> not a project member",
                            user.id(), projectId);
                }
                throw new AccessDeniedException("PERMISSION_DENIED: not project member");
            }
            if (rp.projectRole().length > 0
                    && !evaluator.hasProjectRole(user.id(), projectId, rp.projectRole())) {
                if (log.isDebugEnabled()) {
                    log.debug("PermissionAspect deny: userId={} projectId={} requireProjectRole={} -> mismatch",
                            user.id(), projectId, projectRolesAsString(rp.projectRole()));
                }
                throw new AccessDeniedException("PERMISSION_DENIED: project role");
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("PermissionAspect allow: userId={} requireRole={} projectMember={}",
                    user.id(), rolesAsString(rp.role()), rp.projectMember());
        }
    }

    // ---------------------------------------------------------------------
    // 内部工具
    // ---------------------------------------------------------------------

    /**
     * 优先取方法上的注解，没有再取声明类的注解（含继承层次）。
     *
     * <p>使用 Spring 的 {@link AnnotationUtils#findAnnotation} 处理元注解 / 继承场景。
     */
    private RequirePermission findAnnotation(JoinPoint jp) {
        if (!(jp.getSignature() instanceof MethodSignature ms)) {
            return null;
        }
        Method method = ms.getMethod();
        RequirePermission rp = AnnotationUtils.findAnnotation(method, RequirePermission.class);
        if (rp != null) {
            return rp;
        }
        Class<?> declaringClass = method.getDeclaringClass();
        return AnnotationUtils.findAnnotation(declaringClass, RequirePermission.class);
    }

    private static String rolesAsString(Role[] roles) {
        if (roles == null || roles.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < roles.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(roles[i].name());
        }
        return sb.append(']').toString();
    }

    private static String projectRolesAsString(ProjectRole[] roles) {
        if (roles == null || roles.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < roles.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(roles[i].name());
        }
        return sb.append(']').toString();
    }
}
