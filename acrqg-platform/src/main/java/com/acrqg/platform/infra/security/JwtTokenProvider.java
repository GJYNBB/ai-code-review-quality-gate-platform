package com.acrqg.platform.infra.security;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * JWT 签发 / 解析组件（HS256，jjwt 0.12.x）。
 *
 * <p>关键点：
 * <ul>
 *   <li>密钥由 {@code app.security.jwt.secret} 注入，长度必须 ≥ 32 字节（HS256
 *       要求）；启动期 {@link #init()} 强校验，否则抛 {@link IllegalStateException}
 *       中止上下文，便于尽早发现配置问题。</li>
 *   <li>access token / refresh token 严格区分：通过自定义 claim {@code tokenType}
 *       标识，{@link JwtAuthFilter} 会拒绝把 refresh token 当 access 用。</li>
 *   <li>每个 token 都生成 UUID 作为 {@code jti}，配合 B0-A.6 实现的 JWT 黑名单
 *       支持禁用用户即时失效（R3.2 / R23.1）。</li>
 *   <li>{@link #parse(String)} 在所有失败路径（签名错、过期、不支持、空串、
 *       格式错）统一映射为 {@link ErrorCode#AUTH_INVALID_TOKEN}，避免向调用方
 *       泄漏内部异常细节。</li>
 *   <li>{@link #toAuthentication(Claims)} 将 roles 映射为 {@code ROLE_<role>}
 *       前缀的 {@link SimpleGrantedAuthority}，与 Spring Security 的
 *       {@code hasRole(...)} / {@code @PreAuthorize} 语义保持兼容。</li>
 * </ul>
 *
 * <p>Covers: R1.1, R1.4, R23.1。
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** access token 自定义 claim 标识。 */
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    /** refresh token 自定义 claim 标识。 */
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    /** 源码中保留的本地开发默认密钥；启动期必须显式拒绝。 */
    private static final String DEV_DEFAULT_SECRET =
            "please-change-me-32-bytes-minimum-secret-key-for-jwt-hmac-signing";

    /** 自定义 claim 键名。 */
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";

    private final String secret;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    private SecretKey signingKey;

    public JwtTokenProvider(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.access-ttl-seconds:7200}") long accessTtlSeconds,
            @Value("${app.security.jwt.refresh-ttl-seconds:604800}") long refreshTtlSeconds) {
        this.secret = secret;
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    /**
     * 启动期校验密钥并构造 {@link SecretKey}。
     *
     * <p>HS256 要求密钥长度 ≥ 256 bit (32 字节)；不足时直接抛
     * {@link IllegalStateException}，避免运行期才暴露问题。
     */
    @PostConstruct
    void init() {
        if (secret == null) {
            throw new IllegalStateException("app.security.jwt.secret must not be null");
        }
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "app.security.jwt.secret uses the known development default; set JWT_SECRET explicitly");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least 32 bytes for HS256, current=" + bytes.length);
        }
        if (accessTtlSeconds <= 0) {
            throw new IllegalStateException(
                    "app.security.jwt.access-ttl-seconds must be positive, current=" + accessTtlSeconds);
        }
        if (refreshTtlSeconds <= 0) {
            throw new IllegalStateException(
                    "app.security.jwt.refresh-ttl-seconds must be positive, current=" + refreshTtlSeconds);
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
        log.info("JwtTokenProvider initialized: accessTtl={}s, refreshTtl={}s", accessTtlSeconds, refreshTtlSeconds);
    }

    /**
     * 签发 access token。
     *
     * @param userId   用户 id（写入 {@code sub}）
     * @param username 用户名（自定义 claim）
     * @param roles    全局角色列表（自定义 claim {@code roles}），可空但不允许 null
     * @return JWS Compact 序列化串
     */
    public String issueAccessToken(long userId, String username, List<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(accessTtlSeconds);
        List<String> safeRoles = roles == null ? List.of() : List.copyOf(roles);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, safeRoles)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 签发 refresh token。仅包含 {@code sub} / {@code jti} / {@code tokenType=REFRESH}。
     */
    public String issueRefreshToken(long userId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(refreshTtlSeconds);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验签名 / 过期；任何失败抛 {@link BusinessException}（{@link ErrorCode#AUTH_INVALID_TOKEN}）。
     */
    public Claims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // 不向调用方透出底层原因，统一为无效 token
            log.debug("JWT parse failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    /**
     * 安静解析：失败时返回 {@link Optional#empty()}，不抛异常。
     *
     * <p>主要供 {@link JwtAuthFilter} 在请求入口处区分"无效 token"与"非鉴权失败"
     * 时使用。
     */
    public Optional<Claims> tryParse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT tryParse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 从 {@link Claims} 构造 Spring Security 的 {@link Authentication}。
     *
     * <p>principal 为 {@link AuthenticatedUser}（不放原始 token）；
     * credentials 置空；authorities 由 roles 加 {@code ROLE_} 前缀映射。
     */
    public Authentication toAuthentication(Claims claims) {
        long userId = extractUserId(claims);
        String username = claims.get(CLAIM_USERNAME, String.class);
        List<String> roles = extractRoles(claims);
        String jti = extractJti(claims);

        List<GrantedAuthority> authorities = new ArrayList<>(roles.size());
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        AuthenticatedUser principal = new AuthenticatedUser(userId, username, roles, jti);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    // -------------------- helpers --------------------

    /** 从 {@code sub} 提取 userId，非数字时抛 {@link BusinessException}。 */
    public long extractUserId(Claims claims) {
        String sub = claims.getSubject();
        if (sub == null || sub.isEmpty()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        try {
            return Long.parseLong(sub);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    public String extractJti(Claims claims) {
        return claims.getId();
    }

    public Instant extractExp(Claims claims) {
        Date exp = claims.getExpiration();
        return exp == null ? null : exp.toInstant();
    }

    public String extractTokenType(Claims claims) {
        Object v = claims.get(CLAIM_TOKEN_TYPE);
        return v == null ? null : v.toString();
    }

    /**
     * 安全提取 roles 列表。
     *
     * <p>Jackson 反序列化后通常是 {@code List<String>}，但兼容老版本可能传入
     * 单字符串或 null；此处统一返回不可变列表。
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object raw = claims.get(CLAIM_ROLES);
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
            return List.copyOf(out);
        }
        if (raw instanceof String s && !s.isEmpty()) {
            return List.of(s);
        }
        return List.of();
    }

    /** access token 的有效期（秒），用于 logout 时计算黑名单 TTL。 */
    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    /** refresh token 的有效期（秒）。 */
    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }
}
