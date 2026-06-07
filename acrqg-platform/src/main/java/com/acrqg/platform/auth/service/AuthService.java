package com.acrqg.platform.auth.service;

import com.acrqg.platform.auth.dto.LoginRequest;
import com.acrqg.platform.auth.dto.LoginResultDTO;
import com.acrqg.platform.auth.dto.RefreshResultDTO;
import com.acrqg.platform.auth.dto.UserDTO;

/**
 * 认证服务接口（design.md §6.1）。
 *
 * <p>暴露 4 个接口：登录、登出、刷新令牌、获取当前用户视图。
 * 实现见 {@code AuthServiceImpl}。
 *
 * <p>异常约定：
 * <ul>
 *   <li>{@link #login}：账号禁用 → {@code AUTH_ACCOUNT_DISABLED}；用户名/密码错 →
 *       {@code AUTH_INVALID_CREDENTIALS}（响应文案<b>不</b>区分二者，避免账号枚举）。</li>
 *   <li>{@link #logout}：token 无效或已注销时静默处理；不抛出错误（幂等）。</li>
 *   <li>{@link #refresh}：refresh token 无效 / 已撤销 / 类型错 → {@code AUTH_INVALID_TOKEN}。</li>
 *   <li>{@link #me}：当前线程未携带 {@code AuthenticatedUser} 时，由 {@code CurrentUserHolder.requireCurrent}
 *       抛 {@code PERMISSION_DENIED}（理论上 JwtAuthFilter 已经在更早处拒绝）。</li>
 * </ul>
 *
 * <p>Covers: R1.1, R1.2, R1.3, R1.4, R1.5, R1.6, R3.2。
 */
public interface AuthService {

    /** 用户名密码登录，签发 access + refresh token；写审计 LOGIN_SUCCESS / LOGIN_FAILED。 */
    LoginResultDTO login(LoginRequest request);

    /**
     * 注销当前会话：把 access 的 jti 加入 Redis 黑名单（TTL=token 剩余有效期），
     * 并在调用方提供 refresh token 时撤销同一会话的 refresh jti。
     *
     * @param accessToken 来自请求头的原始 token；可为 {@code null}（此时使用
     *                    CurrentUserHolder 中已解析的 jti）
     * @param refreshToken 当前会话的 refresh token；可为 {@code null}，用于兼容旧客户端
     */
    void logout(String accessToken, String refreshToken);

    /** 用 refresh token 换取新 access token；同时旋转 refresh。 */
    RefreshResultDTO refresh(String refreshToken);

    /** 返回当前已认证用户的视图（含 roles）。 */
    UserDTO me();
}
