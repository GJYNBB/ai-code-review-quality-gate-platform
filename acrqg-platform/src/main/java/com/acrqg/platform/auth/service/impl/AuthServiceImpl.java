package com.acrqg.platform.auth.service.impl;

import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.auth.domain.User;
import com.acrqg.platform.auth.domain.UserStatus;
import com.acrqg.platform.auth.dto.LoginRequest;
import com.acrqg.platform.auth.dto.LoginResultDTO;
import com.acrqg.platform.auth.dto.RefreshResultDTO;
import com.acrqg.platform.auth.dto.UserDTO;
import com.acrqg.platform.auth.repository.UserMapper;
import com.acrqg.platform.auth.service.AuthService;
import com.acrqg.platform.auth.service.AuthTokenTracker;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.infra.redis.JwtBlacklist;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.acrqg.platform.infra.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * {@link AuthService} 默认实现。
 *
 * <h3>关键约束</h3>
 *
 * <ol>
 *   <li><b>错误响应不区分用户名 / 密码错</b>：登录失败时统一抛
 *       {@link ErrorCode#AUTH_INVALID_CREDENTIALS}，无论真实原因是"用户不存在"
 *       还是"密码不匹配"——避免账号枚举攻击（design.md §13 / 安全约束）。</li>
 *   <li><b>账号禁用单独区分</b>：当用户存在但 status=DISABLED 时，抛
 *       {@link ErrorCode#AUTH_ACCOUNT_DISABLED}（401）。这是 R1.3 明确要求的
 *       区分语义，便于前端展示"账号已被禁用"提示。</li>
 *   <li><b>token 跟踪</b>：登录、刷新都通过 {@link AuthTokenTracker} 在 Redis 中
 *       记录已签发的 access jti / refresh jti，配合 {@link JwtBlacklist} 让
 *       SYSTEM_ADMIN 在禁用用户时能在 5 分钟内强制下线（R3.2）。</li>
 *   <li><b>refresh 旋转</b>：每次刷新都签发新 access + 新 refresh，并 DEL 旧 refresh
 *       的 String key + SREM 旧 refresh 从 rts Set 中移除，确保旧 refresh 一次性使用。</li>
 *   <li><b>审计</b>：登录成功 / 失败、登出、刷新分别发布 LOGIN_SUCCESS / LOGIN_FAILED /
 *       AUTH_LOGOUT / AUTH_TOKEN_REFRESHED 事件，由 {@code AuditEventListener} 异步落库。</li>
 * </ol>
 *
 * <p>Covers: R1.1, R1.2, R1.3, R1.4, R1.5, R1.6, R3.2。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** 审计 action 字面量。 */
    private static final String ACTION_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
    private static final String ACTION_LOGIN_FAILED  = "AUTH_LOGIN_FAILED";
    private static final String ACTION_LOGOUT        = "AUTH_LOGOUT";
    private static final String ACTION_REFRESHED     = "AUTH_TOKEN_REFRESHED";

    private static final String RESOURCE_USER = "USER";

    private final UserMapper userMapper;
    private final JwtTokenProvider tokenProvider;
    private final JwtBlacklist blacklist;
    private final AuthTokenTracker tokenTracker;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public AuthServiceImpl(UserMapper userMapper,
                           JwtTokenProvider tokenProvider,
                           JwtBlacklist blacklist,
                           AuthTokenTracker tokenTracker,
                           PasswordEncoder passwordEncoder,
                           ApplicationEventPublisher eventPublisher) {
        this.userMapper = userMapper;
        this.tokenProvider = tokenProvider;
        this.blacklist = blacklist;
        this.tokenTracker = tokenTracker;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    // ---------------------------------------------------------------------
    // login
    // ---------------------------------------------------------------------

    @Override
    public LoginResultDTO login(LoginRequest request) {
        String username = request.username();
        User user = userMapper.selectWithRolesByUsername(username);

        if (user == null) {
            // 不区分"用户不存在"与"密码错误"
            publishLoginFailedAudit(null, username, "INVALID_CREDENTIALS");
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        // 账号禁用：单独区分（R1.3）
        if (UserStatus.DISABLED.name().equals(user.getStatus())) {
            publishLoginFailedAudit(user.getId(), username, "ACCOUNT_DISABLED");
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            publishLoginFailedAudit(user.getId(), username, "INVALID_CREDENTIALS");
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        long userId = user.getId();
        List<String> roles = user.getRoles() == null ? List.of() : List.copyOf(user.getRoles());

        // 签发 access + refresh
        String accessToken = tokenProvider.issueAccessToken(userId, user.getUsername(), roles);
        String refreshToken = tokenProvider.issueRefreshToken(userId);

        // 解析回 jti（避免重复 UUID 生成 / 解耦实现）
        Claims accessClaims = tokenProvider.parse(accessToken);
        Claims refreshClaims = tokenProvider.parse(refreshToken);
        String accessJti = tokenProvider.extractJti(accessClaims);
        String refreshJti = tokenProvider.extractJti(refreshClaims);

        // Redis 跟踪
        tokenTracker.trackLogin(userId, accessJti, refreshJti);

        // 审计
        publishAudit(userId, user.getUsername(), ACTION_LOGIN_SUCCESS, RESOURCE_USER,
                String.valueOf(userId),
                detailOf("username", user.getUsername(), "roles", roles));

        return new LoginResultDTO(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTtlSeconds(),
                toUserDTO(user));
    }

    // ---------------------------------------------------------------------
    // logout
    // ---------------------------------------------------------------------

    @Override
    public void logout(String accessToken) {
        // 优先从当前线程取 jti / userId（已经过 JwtAuthFilter 校验）
        Optional<AuthenticatedUser> opt = CurrentUserHolder.optional();

        long userId;
        String jti;
        Instant exp;
        String username = null;

        if (opt.isPresent()) {
            AuthenticatedUser current = opt.get();
            userId = current.id();
            jti = current.jti();
            username = current.username();
            // exp 不在 AuthenticatedUser 中保存；从 token 重新解析
            if (accessToken != null && !accessToken.isBlank()) {
                Claims claims = tokenProvider.tryParse(accessToken).orElse(null);
                exp = claims == null ? null : tokenProvider.extractExp(claims);
            } else {
                exp = null;
            }
        } else {
            // 兜底：通过传入的 token 解析（典型场景：测试或 filter 未介入）
            if (accessToken == null || accessToken.isBlank()) {
                log.debug("logout noop: no current user and no accessToken");
                return;
            }
            Claims claims = tokenProvider.tryParse(accessToken).orElse(null);
            if (claims == null) {
                log.debug("logout noop: invalid token");
                return;
            }
            userId = tokenProvider.extractUserId(claims);
            jti = tokenProvider.extractJti(claims);
            exp = tokenProvider.extractExp(claims);
        }

        if (jti == null || jti.isBlank()) {
            log.debug("logout noop: no jti");
            return;
        }

        // 计算剩余 TTL；非正值由 JwtBlacklist.add 自动抬升为 5 分钟下限
        Duration remaining = computeRemaining(exp);
        blacklist.add(userId, jti, remaining);
        tokenTracker.untrackAccess(userId, jti);

        publishAudit(userId, username, ACTION_LOGOUT, RESOURCE_USER,
                String.valueOf(userId),
                detailOf("jti", jti));
    }

    // ---------------------------------------------------------------------
    // refresh
    // ---------------------------------------------------------------------

    @Override
    public RefreshResultDTO refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        Claims claims = tokenProvider.parse(refreshToken);

        // 必须是 refresh token 类型
        String tokenType = tokenProvider.extractTokenType(claims);
        if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        long userId = tokenProvider.extractUserId(claims);
        String oldRefreshJti = tokenProvider.extractJti(claims);

        // 校验 Redis 中仍存在且 user 一致
        if (!tokenTracker.isRefreshValid(userId, oldRefreshJti)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // 取最新的用户与角色（密码哈希不需要）
        User user = userMapper.selectWithRolesById(userId);
        if (user == null || UserStatus.DISABLED.name().equals(user.getStatus())) {
            // 账号已不可用：拉黑旧 jti（防止万一 access 还在），并删除 refresh
            tokenTracker.rotateRefresh(userId, oldRefreshJti, null);
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        List<String> roles = user.getRoles() == null ? List.of() : List.copyOf(user.getRoles());

        // 签发新 access + 旋转 refresh
        String newAccess = tokenProvider.issueAccessToken(userId, user.getUsername(), roles);
        String newRefresh = tokenProvider.issueRefreshToken(userId);

        Claims newAccessClaims = tokenProvider.parse(newAccess);
        Claims newRefreshClaims = tokenProvider.parse(newRefresh);
        String newAccessJti = tokenProvider.extractJti(newAccessClaims);
        String newRefreshJti = tokenProvider.extractJti(newRefreshClaims);

        tokenTracker.rotateRefresh(userId, oldRefreshJti, newRefreshJti);
        tokenTracker.trackAccess(userId, newAccessJti);

        publishAudit(userId, user.getUsername(), ACTION_REFRESHED, RESOURCE_USER,
                String.valueOf(userId),
                detailOf("oldRefreshJti", oldRefreshJti, "newAccessJti", newAccessJti));

        return new RefreshResultDTO(newAccess, newRefresh, tokenProvider.getAccessTtlSeconds());
    }

    // ---------------------------------------------------------------------
    // me
    // ---------------------------------------------------------------------

    @Override
    public UserDTO me() {
        AuthenticatedUser current = CurrentUserHolder.requireCurrent();
        User user = userMapper.selectWithRolesById(current.id());
        if (user == null) {
            // 用户已被删除（极少见）：当成 token 失效处理
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        return toUserDTO(user);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /** 计算 token 剩余有效期；exp 为空或已过期则返回 5 分钟下限（与黑名单语义一致）。 */
    private static Duration computeRemaining(Instant exp) {
        if (exp == null) {
            return JwtBlacklist.MIN_TTL;
        }
        long secs = exp.getEpochSecond() - Instant.now().getEpochSecond();
        if (secs <= 0) {
            return JwtBlacklist.MIN_TTL;
        }
        return Duration.ofSeconds(secs);
    }

    private void publishLoginFailedAudit(Long userId, String username, String reason) {
        publishAudit(userId, username, ACTION_LOGIN_FAILED, RESOURCE_USER,
                userId == null ? null : String.valueOf(userId),
                detailOf("username", username, "reason", reason));
    }

    private void publishAudit(Long operatorId, String operatorUsername, String action,
                              String resourceType, String resourceId,
                              Map<String, Object> detail) {
        AuditEvent event = AuditEvent.of(
                operatorId, operatorUsername,
                action, resourceType, resourceId,
                null, detail);
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception ex) {
            // 审计写入失败不应影响主流程
            log.warn("publish audit event failed action={} resource={}/{}", action, resourceType, resourceId, ex);
        }
    }

    /** 构造保留插入顺序的 detail Map。可变长键值对参数。 */
    private static Map<String, Object> detailOf(Object... kv) {
        if (kv == null || kv.length == 0) {
            return Collections.emptyMap();
        }
        if ((kv.length & 1) != 0) {
            throw new IllegalArgumentException("detailOf requires even number of args");
        }
        Map<String, Object> map = new LinkedHashMap<>(kv.length / 2);
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    /** {@link User} → {@link UserDTO}：仅包含对外字段，不暴露 passwordHash。 */
    static UserDTO toUserDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus(),
                user.getRoles() == null ? List.of() : List.copyOf(user.getRoles()),
                user.getCreatedAt());
    }
}
