package com.acrqg.platform.auth.service;

import com.acrqg.platform.infra.security.JwtTokenProvider;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 中跟踪用户 access / refresh token 的辅助组件。
 *
 * <p>设计目的：与 {@link com.acrqg.platform.infra.redis.JwtBlacklist} 解耦，
 * 后者只负责"是否拉黑"判定；本组件负责"知道某用户当前签发了哪些 token"，
 * 二者协作完成 R3.2 的 5 分钟内必失效语义：
 * <ul>
 *   <li>登录成功后：本组件 SADD 把 access jti 与 refresh jti 分别记入两个 Set，
 *       同时为 refresh jti 单独设一个 String key 用于双向校验；</li>
 *   <li>用户禁用 / 删除 / 全局退出时：先调用本组件 {@link #revokeAllForUser(long)}
 *       把所有已知 access jti 入 {@link com.acrqg.platform.infra.redis.JwtBlacklist}，
 *       再删除 refresh jti 的 String key 与 Set；</li>
 *   <li>refresh 旋转时：DEL 旧 refresh String key、SREM 旧 jti、SADD + SET 新 jti。</li>
 * </ul>
 *
 * <h3>Redis Key 设计</h3>
 *
 * <table>
 *   <tr><th>Key</th><th>类型</th><th>含义</th></tr>
 *   <tr><td>{@code auth:user:jtis:{userId}}</td><td>Set</td>
 *       <td>该用户已签发但尚未失效的 access jti 集合</td></tr>
 *   <tr><td>{@code auth:user:rts:{userId}}</td><td>Set</td>
 *       <td>该用户已签发但尚未失效的 refresh jti 集合</td></tr>
 *   <tr><td>{@code auth:rt:{refreshJti}}</td><td>String</td>
 *       <td>refresh jti → userId 反向索引；存在即视为有效</td></tr>
 *   <tr><td>{@code auth:session:at:{accessJti}}</td><td>String</td>
 *       <td>access jti → 当前会话 refresh jti，用于无请求体 logout 撤销 refresh</td></tr>
 * </table>
 *
 * <p>Set 的 TTL 始终取 {@code max(currentTtl, refreshTtl + 60s)}，保证最长存活的
 * refresh token 失效前 Set 不会被 Redis 提前回收（与 JwtBlacklist 的策略一致）。
 *
 * <p>Covers: R1.5, R3.2。
 */
@Component
public class AuthTokenTracker {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenTracker.class);

    /** Set TTL 比 refresh ttl 多预留的缓冲，防止边界回收。 */
    static final Duration GRACE = Duration.ofSeconds(60);

    /** key 前缀。 */
    static final String JTIS_KEY_PREFIX = "auth:user:jtis:";
    static final String RTS_KEY_PREFIX  = "auth:user:rts:";
    static final String RT_KEY_PREFIX   = "auth:rt:";
    static final String ACCESS_REFRESH_KEY_PREFIX = "auth:session:at:";

    private final StringRedisTemplate redis;
    private final JwtTokenProvider tokenProvider;

    public AuthTokenTracker(StringRedisTemplate redis, JwtTokenProvider tokenProvider) {
        this.redis = redis;
        this.tokenProvider = tokenProvider;
    }

    // ---------------------------------------------------------------------
    // public API
    // ---------------------------------------------------------------------

    /**
     * 登录成功后跟踪 access / refresh jti。
     *
     * <p>同时写三处：jtis Set / rts Set / rt String。三处都按 refresh ttl + grace
     * 续 TTL；access 单独签发的 jti 也跟随 refresh 的窗口（虽然 access 自身 ttl
     * 较短，但 Set 内即便保留过期 jti 也无副作用，contains 仍由 JwtBlacklist 决定）。
     *
     * @param userId      用户 id
     * @param accessJti   access token jti
     * @param refreshJti  refresh token jti
     */
    public void trackLogin(long userId, String accessJti, String refreshJti) {
        if (accessJti == null || accessJti.isBlank() || refreshJti == null || refreshJti.isBlank()) {
            log.warn("AuthTokenTracker.trackLogin called with blank jti, userId={}", userId);
            return;
        }
        long refreshTtl = tokenProvider.getRefreshTtlSeconds();
        long setTtlSec = refreshTtl + GRACE.toSeconds();

        // SADD jtis
        redis.opsForSet().add(jtisKey(userId), accessJti);
        refreshExpire(jtisKey(userId), setTtlSec);

        // SADD rts + SET rt:{jti}
        redis.opsForSet().add(rtsKey(userId), refreshJti);
        refreshExpire(rtsKey(userId), setTtlSec);
        redis.opsForValue().set(rtKey(refreshJti), String.valueOf(userId), Duration.ofSeconds(refreshTtl));
        bindAccessToRefresh(accessJti, refreshJti);
    }

    /**
     * 单独跟踪 access jti（用于 refresh 旋转时签发新 access 的场景）。
     *
     * @param userId      用户 id
     * @param accessJti   新 access token jti
     */
    public void trackAccess(long userId, String accessJti) {
        trackAccess(userId, accessJti, null);
    }

    /**
     * 跟踪 access jti，并可选绑定当前会话 refresh jti，供 logout 服务端撤销 refresh。
     */
    public void trackAccess(long userId, String accessJti, String refreshJti) {
        if (accessJti == null || accessJti.isBlank()) {
            return;
        }
        long ttlSec = tokenProvider.getRefreshTtlSeconds() + GRACE.toSeconds();
        redis.opsForSet().add(jtisKey(userId), accessJti);
        refreshExpire(jtisKey(userId), ttlSec);
        bindAccessToRefresh(accessJti, refreshJti);
    }

    /**
     * refresh 旋转：删除旧 refresh，写入新 refresh。
     *
     * <p>调用方必须已经验证过旧 refresh 的合法性（包括存在性）。
     */
    public void rotateRefresh(long userId, String oldRefreshJti, String newRefreshJti) {
        if (oldRefreshJti != null && !oldRefreshJti.isBlank()) {
            redis.delete(rtKey(oldRefreshJti));
            redis.opsForSet().remove(rtsKey(userId), oldRefreshJti);
        }
        if (newRefreshJti == null || newRefreshJti.isBlank()) {
            rebindAccessRefresh(userId, oldRefreshJti, null);
            return;
        }
        long refreshTtl = tokenProvider.getRefreshTtlSeconds();
        long setTtlSec = refreshTtl + GRACE.toSeconds();
        redis.opsForSet().add(rtsKey(userId), newRefreshJti);
        refreshExpire(rtsKey(userId), setTtlSec);
        redis.opsForValue().set(rtKey(newRefreshJti), String.valueOf(userId), Duration.ofSeconds(refreshTtl));
        rebindAccessRefresh(userId, oldRefreshJti, newRefreshJti);
    }

    /**
     * 校验 refresh jti 是否仍有效，且确实属于 {@code userId}。
     *
     * @return true 当且仅当 {@code auth:rt:{jti}} 存在且 value = userId
     */
    public boolean isRefreshValid(long userId, String refreshJti) {
        if (refreshJti == null || refreshJti.isBlank()) {
            return false;
        }
        String storedUserId = redis.opsForValue().get(rtKey(refreshJti));
        if (storedUserId == null) {
            return false;
        }
        return storedUserId.equals(String.valueOf(userId));
    }

    /**
     * 单条登出：把指定 access jti 从 jtis Set 中移除。
     *
     * <p>{@link com.acrqg.platform.infra.redis.JwtBlacklist#add} 由调用方完成；
     * 本方法只负责清理跟踪 Set，避免后续 {@link #revokeAllForUser(long)} 再次操作。
     */
    public void untrackAccess(long userId, String accessJti) {
        if (accessJti == null || accessJti.isBlank()) {
            return;
        }
        redis.opsForSet().remove(jtisKey(userId), accessJti);
        redis.delete(accessRefreshKey(accessJti));
    }

    /**
     * 根据 access jti 查询同一会话当前 refresh jti。用于 logout 无请求体时服务端撤销 refresh。
     */
    public String refreshJtiForAccess(String accessJti) {
        if (accessJti == null || accessJti.isBlank()) {
            return null;
        }
        return redis.opsForValue().get(accessRefreshKey(accessJti));
    }

    /**
     * 用户被禁用 / 全局踢出：
     * <ol>
     *   <li>取出 rts Set 中的全部 refresh jti，逐个 DEL 对应的 rt String key；</li>
     *   <li>DEL rts Set 与 jtis Set。</li>
     * </ol>
     *
     * <p><b>不</b>负责把 access jti 加入 {@link com.acrqg.platform.infra.redis.JwtBlacklist}；
     * 该步骤由 {@code UserService.changeStatus} 在调用 {@link #revokeAllForUser(long)}
     * 之前完成（先拉黑后清跟踪，避免清完就找不到 jti 了）。
     *
     * @return 删除的 refresh jti 数量
     */
    public int revokeAllForUser(long userId) {
        Set<String> refreshJtis = redis.opsForSet().members(rtsKey(userId));
        int rtRemoved = 0;
        if (refreshJtis != null) {
            for (String jti : refreshJtis) {
                if (jti != null && !jti.isBlank()) {
                    Boolean ok = redis.delete(rtKey(jti));
                    if (Boolean.TRUE.equals(ok)) {
                        rtRemoved++;
                    }
                }
            }
        }
        Set<String> accessJtis = redis.opsForSet().members(jtisKey(userId));
        if (accessJtis != null) {
            for (String accessJti : accessJtis) {
                if (accessJti != null && !accessJti.isBlank()) {
                    redis.delete(accessRefreshKey(accessJti));
                }
            }
        }
        redis.delete(rtsKey(userId));
        redis.delete(jtisKey(userId));
        if (log.isDebugEnabled()) {
            log.debug("AuthTokenTracker revokeAll userId={} refreshJtisRemoved={}", userId, rtRemoved);
        }
        return rtRemoved;
    }

    /** 仅供测试 / 监控读取已记录的 access jti 集合。 */
    public Set<String> accessJtisOf(long userId) {
        Set<String> s = redis.opsForSet().members(jtisKey(userId));
        return s == null ? Collections.emptySet() : Set.copyOf(s);
    }

    /** 仅供测试 / 监控读取已记录的 refresh jti 集合。 */
    public Set<String> refreshJtisOf(long userId) {
        Set<String> s = redis.opsForSet().members(rtsKey(userId));
        return s == null ? Collections.emptySet() : Set.copyOf(s);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /** Set 的 TTL 取 max(当前 TTL, desired)，避免每次 add 都"刷短"已有 TTL。 */
    private void refreshExpire(String key, long desiredSeconds) {
        if (desiredSeconds <= 0) {
            return;
        }
        Long currentTtlSec = redis.getExpire(key);
        long base = currentTtlSec == null || currentTtlSec < 0 ? 0L : currentTtlSec;
        long target = Math.max(base, desiredSeconds);
        if (target > 0) {
            redis.expire(key, Duration.ofSeconds(target));
        }
    }

    private void rebindAccessRefresh(long userId, String oldRefreshJti, String newRefreshJti) {
        if (oldRefreshJti == null || oldRefreshJti.isBlank()) {
            return;
        }
        Set<String> accessJtis = redis.opsForSet().members(jtisKey(userId));
        if (accessJtis == null || accessJtis.isEmpty()) {
            return;
        }
        for (String accessJti : accessJtis) {
            if (accessJti == null || accessJti.isBlank()) {
                continue;
            }
            String key = accessRefreshKey(accessJti);
            String bound = redis.opsForValue().get(key);
            if (!oldRefreshJti.equals(bound)) {
                continue;
            }
            if (newRefreshJti == null || newRefreshJti.isBlank()) {
                redis.delete(key);
                continue;
            }
            Long ttlSeconds = redis.getExpire(key);
            long effectiveTtl = ttlSeconds == null || ttlSeconds <= 0
                    ? tokenProvider.getAccessTtlSeconds() + GRACE.toSeconds()
                    : ttlSeconds;
            redis.opsForValue().set(key, newRefreshJti, Duration.ofSeconds(effectiveTtl));
        }
    }

    static String jtisKey(long userId) {
        return JTIS_KEY_PREFIX + userId;
    }

    static String rtsKey(long userId) {
        return RTS_KEY_PREFIX + userId;
    }

    static String rtKey(String refreshJti) {
        return RT_KEY_PREFIX + refreshJti;
    }

    static String accessRefreshKey(String accessJti) {
        return ACCESS_REFRESH_KEY_PREFIX + accessJti;
    }

    private void bindAccessToRefresh(String accessJti, String refreshJti) {
        if (accessJti == null || accessJti.isBlank()
                || refreshJti == null || refreshJti.isBlank()) {
            return;
        }
        long accessTtl = tokenProvider.getAccessTtlSeconds();
        redis.opsForValue().set(accessRefreshKey(accessJti), refreshJti,
                Duration.ofSeconds(accessTtl + GRACE.toSeconds()));
    }
}
