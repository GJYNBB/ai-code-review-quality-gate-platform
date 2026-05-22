package com.acrqg.platform.infra.security;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import java.util.Optional;

/**
 * 当前请求线程内的 {@link AuthenticatedUser} 持有者。
 *
 * <p>由 {@code JwtAuthFilter} 在 token 校验通过之后调用 {@link #set(AuthenticatedUser)}
 * 写入；在 {@code finally} 块中调用 {@link #clear()} 清理，避免线程池复用导致的串扰。
 * 切面 / Service 层通过 {@link #optional()} 或 {@link #requireCurrent()} 取用，
 * 避免每次去解析 {@link org.springframework.security.core.context.SecurityContextHolder}
 * 的 {@code Authentication} 对象。
 *
 * <p>注意：本类故意不依赖 Spring 容器（无 {@code @Component}），是一个纯静态工具，
 * 同时便于在单元测试中直接 set / clear。
 *
 * <p>异步 / Worker 场景下不保证传播：B1-B 审计模块在异步落库时，应通过 MDC +
 * 自定义 {@code AuditContext} 在事件载荷中显式携带 operatorId / username，
 * 而不是从 {@code CurrentUserHolder} 跨线程读取。
 *
 * <p>Covers: R1.4, R22.1, R23.1。
 */
public final class CurrentUserHolder {

    private static final ThreadLocal<AuthenticatedUser> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
        // utility class - no instantiation
    }

    /** 设置当前线程的已认证用户。{@code null} 等同于 {@link #clear()}。 */
    public static void set(AuthenticatedUser user) {
        if (user == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(user);
        }
    }

    /** 取当前线程的已认证用户；未设置时返回 {@link Optional#empty()}。 */
    public static Optional<AuthenticatedUser> optional() {
        return Optional.ofNullable(HOLDER.get());
    }

    /**
     * 取当前线程的已认证用户；未设置时抛出 {@link BusinessException}。
     *
     * <p>用于必须在已认证上下文中执行的代码路径（如审计切面、权限切面）。
     */
    public static AuthenticatedUser requireCurrent() {
        AuthenticatedUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "no current user");
        }
        return user;
    }

    /** 清理当前线程的持有对象（必须在请求结束时调用）。 */
    public static void clear() {
        HOLDER.remove();
    }
}
