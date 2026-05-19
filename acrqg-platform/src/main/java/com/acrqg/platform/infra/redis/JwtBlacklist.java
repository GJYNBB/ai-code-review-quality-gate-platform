package com.acrqg.platform.infra.redis;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * JWT 黑名单（基于 Redis）。
 *
 * <p>用于实现 R3.2 / R23.1 描述的"禁用用户 / 主动登出后 5 分钟内必失效"语义。
 * Key 设计：
 * <ul>
 *   <li>{@code jwt:bl:jti:{jti}}：单值字符串，存在即视为该 jti 已被拉黑；
 *       value 固定为 {@code "1"}；TTL 取该 token 的剩余有效期。</li>
 *   <li>{@code jwt:bl:user:{userId}}：Redis Set，记录某用户曾被拉黑的所有 jti。
 *       禁用用户时通过 {@link #removeUser(long)} 一次性"加固"该用户的全部 jti
 *       为已拉黑状态。set 的 TTL 在每次 add 时刷新为 {@code max(currentTtl,
 *       jtiTtl + 60s 缓冲)}，保证最长 token 失效前 set 不会被 Redis 提前回收。</li>
 * </ul>
 *
 * <p>{@link com.acrqg.platform.infra.security.JwtAuthFilter} 通过名为
 * {@code jwtJtiBlacklist} 的 {@code Predicate<String>} bean 在请求入口处查询本组件
 * （由 {@link RedisBeansConfig} 统一暴露），从而把"是否拉黑"作为
 * 鉴权链路的最后一道闸门。
 *
 * <p><b>幂等 / 容错</b>：
 * <ul>
 *   <li>{@link #add} 多次写入同一 jti 是允许的，最后一次的 TTL 覆盖之前；
 *       Set 成员 add 自然幂等。</li>
 *   <li>当 ttl 非正时按"5 分钟"作为下限补齐——这是 design §3.2 的窗口下限，
 *       避免出现 {@code SET ... EX 0} 导致 key 立即过期、起不到拉黑效果。</li>
 *   <li>Redis 不可达时由 Spring Data Redis 抛运行时异常上抛，调用方
 *       （登出 / 用户禁用流程）应自行决定是否回滚事务；本组件不静默吞错。</li>
 * </ul>
 *
 * <p>Covers: R3.2 (禁用用户 5 分钟内失效)。
 */
@Component
public class JwtBlacklist {

    private static final Logger log = LoggerFactory.getLogger(JwtBlacklist.class);

    /** 每个被拉黑 jti 的最小 TTL（设计 §3.2：5 分钟窗口）。 */
    static final Duration MIN_TTL = Duration.ofMinutes(5);

    /** 用户 set 在 add 时的额外缓冲（避免边界回收）。 */
    private static final Duration USER_SET_GRACE = Duration.ofSeconds(60);

    /** key 前缀。 */
    static final String JTI_KEY_PREFIX = "jwt:bl:jti:";
    static final String USER_KEY_PREFIX = "jwt:bl:user:";

    private final StringRedisTemplate stringRedisTemplate;

    public JwtBlacklist(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将单个 jti 加入黑名单。
     *
     * @param userId 用户 id（用于反向索引到 {@link #removeUser(long)} 的批量入口）
     * @param jti    JWT 的 jti claim；空串将被忽略并打 WARN
     * @param ttl    黑名单保留时长；非正值会被抬升为 {@link #MIN_TTL}
     */
    public void add(long userId, String jti, Duration ttl) {
        if (jti == null || jti.isBlank()) {
            log.warn("JwtBlacklist.add called with blank jti, userId={}", userId);
            return;
        }
        Duration effective = (ttl == null || !ttl.isPositive()) ? MIN_TTL : ttl;

        String jtiKey = jtiKey(jti);
        String userKey = userKey(userId);

        // 1) jti -> "1"，TTL=token 剩余有效期
        stringRedisTemplate.opsForValue().set(jtiKey, "1", effective);

        // 2) user set 添加成员
        stringRedisTemplate.opsForSet().add(userKey, jti);

        // 3) 刷新 user set 的 TTL：取 max(当前 TTL, effective + grace)
        long desiredSeconds = effective.toSeconds() + USER_SET_GRACE.toSeconds();
        Long currentTtlSec = stringRedisTemplate.getExpire(userKey);
        long newTtlSeconds = Math.max(
                currentTtlSec == null || currentTtlSec < 0 ? 0L : currentTtlSec,
                desiredSeconds);
        if (newTtlSeconds > 0) {
            stringRedisTemplate.expire(userKey, Duration.ofSeconds(newTtlSeconds));
        }

        if (log.isDebugEnabled()) {
            log.debug("JwtBlacklist add userId={} jti={} ttl={}s userSetTtl={}s",
                    userId, jti, effective.toSeconds(), newTtlSeconds);
        }
    }

    /**
     * 判定某 jti 是否已被拉黑。
     *
     * @param jti JWT 的 jti claim
     * @return 当且仅当 {@code jwt:bl:jti:{jti}} 存在时返回 {@code true}
     */
    public boolean contains(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Boolean exists = stringRedisTemplate.hasKey(jtiKey(jti));
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 一次性"加固"某用户已知的全部 jti 为已拉黑状态。
     *
     * <p>典型场景：SYSTEM_ADMIN 将用户切换为 DISABLED；调用本方法可保证该用户
     * 历史登录时签发的 access token 在 5 分钟内全部失效（R3.2）。
     *
     * <p>实现策略：遍历 {@code jwt:bl:user:{userId}} 集合中的每个 jti，对其执行
     * {@code SET jwt:bl:jti:{jti} 1 EX 300 NX}（即"已存在则保留原 TTL，不存在则
     * 以 5 分钟为下限拉黑"）。最终返回本次实际新拉黑的数量。
     *
     * <p><b>注意</b>：本方法只能加固"已经知道的 jti"——也就是历史调用过
     * {@link #add} 的那些。对于尚未登出且 add 过的 token，配合 B1-A.4 在
     * {@code changeStatus} 时调用 {@link #add} 将每个仍活跃的 jti 入库即可。
     *
     * @param userId 目标用户 id
     * @return 本次新拉黑的 jti 数量（已存在的不计入）
     */
    public int removeUser(long userId) {
        String userKey = userKey(userId);
        Set<String> jtis = stringRedisTemplate.opsForSet().members(userKey);
        if (jtis == null || jtis.isEmpty()) {
            return 0;
        }
        int newlyAdded = 0;
        for (String jti : jtis) {
            if (jti == null || jti.isBlank()) {
                continue;
            }
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent(jtiKey(jti), "1", MIN_TTL);
            if (Boolean.TRUE.equals(ok)) {
                newlyAdded++;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("JwtBlacklist removeUser userId={} known={} newlyAdded={}",
                    userId, jtis.size(), newlyAdded);
        }
        return newlyAdded;
    }

    /**
     * 返回某用户已记录的全部 jti（不可变拷贝）。主要供测试验证使用。
     */
    public Set<String> userJtis(long userId) {
        Set<String> jtis = stringRedisTemplate.opsForSet().members(userKey(userId));
        return jtis == null ? Collections.emptySet() : Set.copyOf(jtis);
    }

    static String jtiKey(String jti) {
        return JTI_KEY_PREFIX + jti;
    }

    static String userKey(long userId) {
        return USER_KEY_PREFIX + userId;
    }
}
