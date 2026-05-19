package com.acrqg.platform.infra.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Stream 健康指示器。
 *
 * <p>由 Spring Boot Actuator 自动发现并注册到 {@code /actuator/health}：
 * 由于 bean 名称去掉 {@code HealthIndicator} 后缀作为标识，本类暴露的指示器名称为
 * <b>{@code redisStream}</b>。
 *
 * <p>判定逻辑：
 * <ol>
 *   <li>对底层 Redis 连接执行 {@code PING}：
 *       {@code stringRedisTemplate.execute((RedisCallback) c -> c.ping())}。
 *       回显非空即视为连通；任何 {@link DataAccessException} 或运行时异常视为
 *       {@link Health#down(Throwable) DOWN}。</li>
 *   <li>对评审任务 Stream 执行 {@code XLEN review-task-stream}，作为待消费消息
 *       数量的可观测指标。Stream 不存在时 Spring Data Redis 返回 {@code null}，
 *       此处折算为 0；该值仅作为细节附加，<b>不</b>参与 UP/DOWN 判定（避免长队列
 *       误报为不健康；告警由 Prometheus 规则在 metrics 维度上独立配置）。</li>
 * </ol>
 *
 * <p>详情字段：
 * <ul>
 *   <li>{@code ping}：成功时为 {@code "PONG"}（Redis 协议规定）。</li>
 *   <li>{@code review_task_stream_pending}：当前队列长度。</li>
 *   <li>{@code error}（仅 DOWN）：异常类型简短描述，<b>不</b>暴露完整 stack trace；
 *       完整堆栈通过 Logback 输出到日志。</li>
 * </ul>
 *
 * <p>R24.6：本指示器是 worker 实例 readinessProbe 的关键依赖——Worker 不能消费
 * Redis Stream 时不应被视为 ready。
 */
@Component
public class RedisStreamHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamHealthIndicator.class);

    /** 评审任务 Stream key（与 design §3.1 / §6.3.2 一致）。 */
    static final String REVIEW_TASK_STREAM = "review-task-stream";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisStreamHealthIndicator(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Health health() {
        try {
            String pong = ping();
            long pending = streamPendingCount();
            return Health.up()
                    .withDetail("ping", pong)
                    .withDetail("review_task_stream", REVIEW_TASK_STREAM)
                    .withDetail("review_task_stream_pending", pending)
                    .build();
        } catch (Exception ex) {
            // 仅记录 WARN：actuator 抓取频率较高，避免 ERROR 噪音放大；
            // 完整异常通过 Health.down(ex) 由调用方 (actuator) 决定如何展示。
            log.warn("Redis stream health check failed: {}", ex.toString());
            return Health.down(ex)
                    .withDetail("error", ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .build();
        }
    }

    /**
     * 通过底层连接执行 {@code PING}，返回 {@code "PONG"}。
     *
     * <p>使用 {@link RedisCallback} 而非 {@link StringRedisTemplate#hasKey(Object)}
     * 等高层 API，原因有二：
     * <ul>
     *   <li>{@code PING} 是 Redis 唯一不依赖 keyspace 状态的连通性探针，比
     *       {@code EXISTS / GET} 更准确反映"连接 + 协议层"健康；</li>
     *   <li>{@code RedisConnection.ping()} 在 Lettuce 实现下直接走单连接，避免
     *       触发 connection pool 的额外语义。</li>
     * </ul>
     */
    private String ping() {
        // RedisCallback#doInRedis 的连接由 Spring 管理生命周期，无需 try-with-resources：
        // 模板会在回调返回后自动归还连接到底层 ConnectionFactory。
        String pong = stringRedisTemplate.execute((RedisCallback<String>) RedisConnection::ping);
        if (pong == null || pong.isBlank()) {
            // 极端情况下 Lettuce 返回 null（例如 connection 未就绪）：上抛供 health() 转 DOWN。
            throw new IllegalStateException("redis ping returned empty response");
        }
        return pong;
    }

    /**
     * 返回 {@code XLEN review-task-stream}；stream 不存在时折算为 0。
     */
    private long streamPendingCount() {
        Long size = stringRedisTemplate.opsForStream().size(REVIEW_TASK_STREAM);
        return size == null ? 0L : size;
    }

    /**
     * 暴露给 {@link #ping()} 的连接工厂访问点（保留以便后续单元测试用 mock 连接工厂注入）。
     */
    @SuppressWarnings("unused")
    private RedisConnectionFactory connectionFactory() {
        return stringRedisTemplate.getConnectionFactory();
    }
}
