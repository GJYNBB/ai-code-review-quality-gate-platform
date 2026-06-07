package com.acrqg.platform.auth.controller;

import com.acrqg.platform.auth.dto.LoginRequest;
import com.acrqg.platform.auth.dto.LoginResultDTO;
import com.acrqg.platform.auth.dto.RefreshRequest;
import com.acrqg.platform.auth.dto.RefreshResultDTO;
import com.acrqg.platform.auth.dto.UserDTO;
import com.acrqg.platform.auth.service.AuthService;
import com.acrqg.platform.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器（design.md §8.7）。
 *
 * <p>路由清单：
 * <pre>
 * POST /api/v1/auth/login    - 公开（白名单），用户名+密码登录
 * POST /api/v1/auth/logout   - 已登录，注销当前 access token
 * POST /api/v1/auth/refresh  - 公开（白名单），用 refreshToken 换新 accessToken
 * GET  /api/v1/auth/me       - 已登录，返回当前用户视图
 * </pre>
 *
 * <p>{@code /login} / {@code /refresh} 在 {@link com.acrqg.platform.infra.security.JwtAuthFilter}
 * 与 {@link com.acrqg.platform.infra.security.SecurityConfig} 中已声明为 permitAll；
 * 无需在控制器层重复声明。失败响应（401 / 400）由
 * {@link com.acrqg.platform.common.exception.GlobalExceptionHandler} 统一映射。
 *
 * <p>Covers: R1.1, R1.2, R1.3, R1.4, R1.5, R1.6, R23.1。
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "认证（M01 / R1）")
public class AuthController {

    /** Authorization 头的 Bearer 前缀。 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "用户登录",
            description = "用户名 + 密码登录，签发 access + refresh token（R1.1 / R1.4）。"
                    + "失败映射：账号禁用 -> AUTH_ACCOUNT_DISABLED；用户名/密码错 -> AUTH_INVALID_CREDENTIALS。")
    @PostMapping("/login")
    public ApiResponse<LoginResultDTO> login(@Valid @RequestBody LoginRequest request) {
        LoginResultDTO result = authService.login(request);
        return ApiResponse.success(result);
    }

    @Operation(summary = "登出当前会话",
            description = "把当前 access token 的 jti 加入 Redis 黑名单，并在请求体提供 refreshToken 时撤销 refresh。"
                    + "需携带有效 access token；refreshToken 缺失时按旧客户端兼容路径仅撤销 access。")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request,
                                    @RequestBody(required = false) RefreshRequest logoutRequest) {
        String refreshToken = logoutRequest == null ? null : logoutRequest.refreshToken();
        authService.logout(extractAccessToken(request), refreshToken);
        return ApiResponse.success(null);
    }

    @Operation(summary = "刷新访问令牌",
            description = "用 refreshToken 换取新 accessToken，并旋转 refreshToken（R1.5）。"
                    + "refreshToken 无效 / 已撤销 -> AUTH_INVALID_TOKEN。")
    @PostMapping("/refresh")
    public ApiResponse<RefreshResultDTO> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshResultDTO result = authService.refresh(request.refreshToken());
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取当前用户",
            description = "返回当前 token 对应的用户视图（含 roles）。")
    @GetMapping("/me")
    public ApiResponse<UserDTO> me() {
        return ApiResponse.success(authService.me());
    }

    /**
     * 从 {@code Authorization: Bearer <token>} 头提取 token；缺失或格式不对返回 {@code null}。
     */
    private static String extractAccessToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
