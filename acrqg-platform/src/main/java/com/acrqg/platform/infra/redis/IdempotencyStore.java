package com.acrqg.platform.infra.redis;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 幂等键存储（Redis 实现）。
 *
 * <p>通过 {@code SET key value NX EX ttl} 原子写入幂等键，配合应用层的
 * "首次成功后回填业务结果"逻辑，可同时满足：
 * <ul>
 *   <li>R7.4 Webhook 幂等：{@code (provider, repositoryId, eventId)} 三元组在 24 小时内
 *       重复到达时只创建一次任务，参见 {@link #webhookKey(String, String, String)}。</li>
 *   <li>R8.4 手动接口幂等：请求头 {@code Idempotency-Key} 在 24 小时内重复使用时
 *       直接返回上一次结果，参见 {@link #taskKey(String)}。</li>
 * </ul>
 *
 * <p>语义说明：
 * <ul>
 *   <li>{@link #putIfAbsent} 仅"占位"——返回 {@code true} 表示当前调用是首次，
 *       调用方应继续执行业务逻辑并在成功后将结果（如 taskId）写回到同一 key
 *       的 value 中（通过覆盖性的 {@code SET key value KEEPTTL} 由调用方实现）。
 *       返回 {@code false} 表示已有占位，调用方应当读取 {@link #get(String)}
 *       并返回上一次的结果。</li>
 *   <li>TTL 默认 24 小时，与 design.md §7.4 描述一致；调用方亦可显式传入
 *       自定义 TTL（例如对长事务延长保留期）。</li>
 *   <li>本组件不保证强一致性意义上的"恰好一次"——Redis 故障期间退化为
 *       "至多一次创建"由上层业务唯一约束（例如 review_task 表的
 *       {@code (project_id, pr_id, commit_sha)} 唯一索引）兜底。</li>
 * </ul>
 *
 * <p>Covers: R7.4, R8.4。
 */
@Component
public class IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);

    /** 默认 TTL：24 小时。与 design.md §7.4 一致。 */
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    /** 默认占位值（当调用方暂无业务 id 可写入时）。 */
    private static final String DEFAULT_PLACEHOLDER = "1";

    private final StringRedisTemplate stringRedisTemplate;

    public IdempotencyStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 占位写入。
     *
     * <p>原子语义：{@code SET key value NX EX ttl}；当且仅当 key 不存在时写入并返回
     * {@code true}。
     *
     * @param key   幂等键全名（建议通过 {@link #webhookKey} / {@link #taskKey} 构造）
     * @param value 占位值；推荐传入业务 id（例如 taskId 字符串），便于命中时直接 get
     * @param ttl   过期时长；不可为空且必须为正
     * @return {@code true} 表示首次占位成功；{@code false} 表示已有占位
     */
    public boolean putIfAbsent(String key, String value, Duration ttl) {
        validateKey(key);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be a positive Duration");
        }
        String safeValue = value == null ? "" : value;
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, safeValue, ttl);
        boolean inserted = Boolean.TRUE.equals(ok);
        if (log.isDebugEnabled()) {
            log.debug("idempotency putIfAbsent key={} ttl={}s inserted={}", key, ttl.toSeconds(), inserted);
        }
        return inserted;
    }

    /** 默认 TTL（24 小时）的便捷重载。 */
    public boolean putIfAbsent(String key, String value) {
        return putIfAbsent(key, value, DEFAULT_TTL);
    }

    /** 默认 TTL + 默认占位值的便捷重载（仅关心是否首次到达时使用）。 */
    public boolean putIfAbsent(String key) {
        return putIfAbsent(key, DEFAULT_PLACEHOLDER, DEFAULT_TTL);
    }

    /**
     * 读取已有占位值。
     *
     * @param key 幂等键全名
     * @return 当 key 存在时返回 {@link Optional#of} 包装的 value；不存在或已过期时
     *         返回 {@link Optional#empty()}
     */
    public Optional<String> get(String key) {
        validateKey(key);
        String v = stringRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(v);
    }

    /** 删除占位（调试 / 回滚场景）。 */
    public void delete(String key) {
        validateKey(key);
        stringRedisTemplate.delete(key);
    }

    /**
     * 返回 key 的剩余 TTL（秒）。
     *
     * <p>语义与 Redis {@code TTL} 相同：
     * {@code -2} 表示 key 不存在；{@code -1} 表示 key 永不过期；其他正整数为剩余秒数。
     */
    public Long ttlSeconds(String key) {
        validateKey(key);
        return stringRedisTemplate.getExpire(key);
    }

    // -------------------- canonical key builders --------------------

    /**
     * Webhook 幂等键：{@code idem:webhook:{provider}:{repositoryId}:{eventId}}。
     *
     * <p>来源：design.md §7.4。三元组任一为空时抛
     * {@link IllegalArgumentException}，避免出现 {@code idem:webhook:::} 这种坍缩
     * 到全局单例的危险 key。
     */
    public static String webhookKey(String provider, String repositoryId, String eventId) {
        if (provider == null || provider.isBlank()
                || repositoryId == null || repositoryId.isBlank()
                || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException(
                    "webhook idempotency key requires non-blank provider/repositoryId/eventId");
        }
        return "idem:webhook:" + provider + ":" + repositoryId + ":" + eventId;
    }

    /**
     * 手动任务幂等键：{@code idem:task:{idempotencyKey}}。
     *
     * <p>{@code idempotencyKey} 来自请求头 {@code Idempotency-Key}（R8.4）。
     */
    public static String taskKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be null or blank");
        }
        return "idem:task:" + idempotencyKey;
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be null or blank");
        }
    }
}
