package com.acrqg.platform.infra.health;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * JWT 黑名单大小指标。
 *
 * <p>在 Micrometer {@link MeterRegistry} 上注册一个名为
 * <b>{@code acrqg.jwt.blacklist.size}</b>（Prometheus 暴露为
 * {@code acrqg_jwt_blacklist_size}）的 {@link Gauge}，反映 Redis 中
 * 当前命中 {@code jwt:bl:jti:*} 模式的 key 数量——即"近似的被拉黑 jti 总数"。
 * 该指标用于运维侧观察登出 / 用户禁用事件的潜在风暴
 * （R3.2 / R23.1 / R24.6）。
 *
 * <p><b>关键安全约束：禁止 KEYS 命令。</b>
 * Redis 的 {@code KEYS pattern} 是 O(N) 阻塞命令，会在生产 keyspace 较大时
 * 阻塞主线程数百毫秒甚至秒级，违反 R24.6 对低延迟可观测的要求。本实现严格使用
 * {@code SCAN cursor MATCH pattern COUNT n} 的迭代器（{@link Cursor}），
 * 每批次抓 {@value #SCAN_BATCH} 条，避免长尾延迟；
 * 同时仅返回近似计数（SCAN 期间允许并发增删），这与 Prometheus 抓取的"采样"
 * 语义天然吻合。
 *
 * <p><b>缓存策略</b>：Prometheus 抓取通常每 15 秒一次，但 Spring Boot Actuator
 * 在单次 scrape 内可能多次回调 Gauge supplier；此外业务侧若把同名指标暴露在
 * 多个面板亦可能更高频地读取。本实现内部用 {@code cachedValue} +
 * {@code lastSampleEpochMillis} 缓存 {@value #CACHE_TTL_SECONDS} 秒，避免
 * 短时间内重复 SCAN。
 *
 * <p><b>失败处理</b>：Redis 故障时 {@link #size()} 返回 {@code -1}（Prometheus
 * 习惯把 -1 视为"unknown / 不可用"），并以最长每分钟一次的频率打 WARN，避免
 * 抓取风暴下日志爆炸。WARN 日志与 actuator HTTP 响应解耦，因此即便 Redis 持续
 * 不可用，{@code /actuator/prometheus} 仍能在毫秒级响应。
 *
 * <p>Covers: R24.6（Micrometer 指标占位 acrqg_jwt_blacklist_size）。
 *
 * @see com.acrqg.platform.infra.redis.JwtBlacklist
 */
@Component
public class JwtBlacklistMetrics {

    private static final Logger log = LoggerFactory.getLogger(JwtBlacklistMetrics.class);

    /** Gauge 名称（Micrometer 风格）。Prometheus 自动转换为 {@code acrqg_jwt_blacklist_size}。 */
    static final String METRIC_NAME = "acrqg.jwt.blacklist.size";

    /** SCAN 匹配模式：与 {@link com.acrqg.platform.infra.redis.JwtBlacklist#JTI_KEY_PREFIX} 对齐。 */
    static final String SCAN_PATTERN = "jwt:bl:jti:*";

    /** SCAN 单批返回的 hint（实际数量由 Redis 决定，通常接近这个值）。 */
    static final long SCAN_BATCH = 100L;

    /** 采样缓存窗口；Prometheus 抓取间隔典型 15s，5s 既能滤掉单次 scrape 内的多次回调，
     *  又能保证最差 5s 延迟能反映黑名单变化。 */
    static final long CACHE_TTL_SECONDS = 5L;

    /** WARN 日志最小间隔：每分钟一次。 */
    static final long WARN_INTERVAL_SECONDS = 60L;

    /** 失败时返回值（Prometheus 习惯：-1 表示 unknown）。 */
    static final long ERROR_VALUE = -1L;

    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate stringRedisTemplate;

    /** 缓存最近一次成功 SCAN 的结果；初始 -1 表示尚未采样。 */
    private final AtomicLong cachedValue = new AtomicLong(ERROR_VALUE);

    /** 缓存写入时间戳（epoch millis）；volatile 保证 sample() 与 register() 间的可见性。 */
    private volatile long lastSampleEpochMillis = 0L;

    /** 上次 WARN 日志时间戳（epoch seconds）；用于限频。 */
    private volatile long lastWarnEpochSeconds = 0L;

    public JwtBlacklistMetrics(MeterRegistry meterRegistry, StringRedisTemplate stringRedisTemplate) {
        this.meterRegistry = meterRegistry;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 在 bean 初始化阶段把自身注册为 Gauge 来源。
     *
     * <p>Micrometer 的 {@link Gauge#builder} 推荐"长寿对象 + 抽取函数"模式：
     * 这里把 {@code this} 作为 stateObject，{@link #size()} 作为 measurement
     * function；Micrometer 内部弱引用 stateObject，避免本组件被 GC 后仍保留无效
     * gauge。
     */
    @PostConstruct
    void register() {
        Gauge.builder(METRIC_NAME, this, JwtBlacklistMetrics::sample)
                .description("Approximate count of jti entries currently blacklisted in Redis "
                        + "(SCAN-based sampling, cached " + CACHE_TTL_SECONDS + "s; -1 indicates Redis unavailable)")
                .baseUnit("entries")
                .register(meterRegistry);
        log.info("Registered Micrometer gauge '{}' with SCAN pattern '{}' and {}s cache",
                METRIC_NAME, SCAN_PATTERN, CACHE_TTL_SECONDS);
    }

    /**
     * Gauge 抽取函数（Micrometer 接受 double 返回值）。
     *
     * <p>使用 {@link #size()} 计算，再做最低限度的类型转换。
     */
    double sample() {
        return (double) size();
    }

    /**
     * 计算当前黑名单大小。
     *
     * <p>命中 {@value #CACHE_TTL_SECONDS} 秒缓存窗口时直接返回 {@link #cachedValue}；
     * 否则发起一次 {@code SCAN}：使用 {@link RedisCallback} 拿到原生 connection
     * 以便 try-with-resources 关闭 cursor，避免 cursor 泄漏。
     *
     * <p>Redis 异常一律捕获并返回 {@link #ERROR_VALUE}；不向 Micrometer / actuator
     * 抛出异常，从而保证 {@code /actuator/prometheus} 端点的稳定性。
     *
     * @return 近似计数；Redis 不可用时返回 {@code -1}
     */
    long size() {
        long nowMillis = Instant.now().toEpochMilli();
        long cacheTtlMillis = Duration.ofSeconds(CACHE_TTL_SECONDS).toMillis();
        if (nowMillis - lastSampleEpochMillis < cacheTtlMillis && lastSampleEpochMillis != 0L) {
            return cachedValue.get();
        }

        try {
            Long count = stringRedisTemplate.execute((RedisCallback<Long>) connection -> {
                ScanOptions options = ScanOptions.scanOptions()
                        .match(SCAN_PATTERN)
                        .count(SCAN_BATCH)
                        .build();
                long n = 0L;
                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    while (cursor.hasNext()) {
                        cursor.next();
                        n++;
                    }
                }
                return n;
            });
            long resolved = count == null ? 0L : count;
            cachedValue.set(resolved);
            lastSampleEpochMillis = nowMillis;
            return resolved;
        } catch (Exception ex) {
            warnRateLimited(ex);
            // 不更新 lastSampleEpochMillis，下次抓取仍会尝试重新采样；
            // 但更新 cachedValue 为 ERROR_VALUE，让 Prometheus 立刻看到 unknown。
            cachedValue.set(ERROR_VALUE);
            return ERROR_VALUE;
        }
    }

    /** 限频 WARN：避免 actuator 高频抓取下日志风暴。 */
    private void warnRateLimited(Exception ex) {
        long nowSec = Instant.now().getEpochSecond();
        if (nowSec - lastWarnEpochSeconds >= WARN_INTERVAL_SECONDS) {
            log.warn("JWT blacklist gauge SCAN failed (returning -1): {}", ex.toString());
            lastWarnEpochSeconds = nowSec;
        }
    }
}
