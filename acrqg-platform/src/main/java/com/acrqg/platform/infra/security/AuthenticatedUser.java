package com.acrqg.platform.infra.security;

import java.util.List;

/**
 * 当前请求的已认证用户视图（principal）。
 *
 * <p>由 {@code JwtAuthFilter} 在解析 access token 之后构造并设置为
 * {@link org.springframework.security.core.Authentication#getPrincipal()}，
 * 同时通过 {@link CurrentUserHolder} 暴露给切面 / Service 层。
 *
 * <p>该对象仅承载 token 中已经过签名校验的字段，不包含 password / token 原文，
 * 可以安全地放入日志或审计 detail（{@code MaskUtils} 不需要再处理）。
 *
 * <p>Covers: R1.4, R23.1。
 *
 * @param id       用户主键（来自 JWT 的 {@code sub} 字段）
 * @param username 用户名（来自自定义 claim {@code username}）
 * @param roles    全局角色编码列表（来自自定义 claim {@code roles}），
 *                 与 {@code Role.code} 对齐：
 *                 {@code DEVELOPER} / {@code REVIEWER} / {@code PROJECT_ADMIN}
 *                 / {@code SYSTEM_ADMIN} / {@code CI_CD}
 * @param jti      JWT ID，用于黑名单查询与登出
 */
public record AuthenticatedUser(long id, String username, List<String> roles, String jti) {

    /**
     * 是否拥有指定全局角色。
     *
     * <p>仅做严格字符串匹配，不解析 {@code ROLE_} 前缀；调用方应传入
     * 与 token 中保持一致的角色编码（如 {@code "SYSTEM_ADMIN"}）。
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
