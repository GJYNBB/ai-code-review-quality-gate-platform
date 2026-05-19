package com.acrqg.platform.infra.redis;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Stream 发布器。
 *
 * <p>封装 {@code XADD} 与 {@code XLEN} 两个操作，统一上游模块（B3-B Webhook 接收、
 * B3-A 评审任务编排）向 {@code review-task-stream} 等队列入队的语义：
 * <ul>
 *   <li>fields 一律使用 {@link Map}{@code <String,String>}：Redis Stream 的 entry
 *       本质上就是 field/value 字符串对。非字符串值由
 *       {@link #enqueue(String, String, Object, Map)} 重载在入队前统一字符串化，
 *       {@code null} 值替换为空串以避免 NPE 出现在底层 Lettuce 层。</li>
 *   <li>主入口 {@link #enqueue(String, Map)} 在调试日志里输出 streamKey + recordId
 *       便于按 traceId 串联任务执行链路（R24.5）。</li>
 *   <li>{@link #pendingCount(String)} 返回当前 stream 长度，供
 *       {@code RedisStreamHealthIndicator}（B0-A.13）暴露为
 *       {@code acrqg_review_task_stream_size} 指标。</li>
 * </ul>
 *
 * <p>Covers: R7.6（Webhook 异步入队，3 秒响应保障），R24.6（Stream 长度可观测）。
 */
@Component
public class RedisStreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamPublisher.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisStreamPublisher(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 入队（{@code XADD streamKey * field1 v1 field2 v2 ...}）。
     *
     * <p>该方法对入参做最小校验：streamKey 不可为空、fields 不可为空。
     * 任何字段值会原样作为字符串发送到 Redis；调用方负责保证已经字符串化。
     *
     * @param streamKey Redis Stream key，例如 {@code review-task-stream}
     * @param fields    field/value 字符串对；保留 {@link Map} 的迭代顺序
     * @return Redis 分配的 {@link RecordId#toString()}（形如 {@code 1718777777777-0}）
     */
    public String enqueue(String streamKey, Map<String, String> fields) {
        if (streamKey == null || streamKey.isBlank()) {
            throw new IllegalArgumentException("streamKey must not be null or blank");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be null or empty");
        }
        RecordId recordId = stringRedisTemplate.opsForStream()
                .add(StreamRecords.string(fields).withStreamKey(streamKey));
        String idStr = recordId == null ? null : recordId.getValue();
        if (log.isDebugEnabled()) {
            log.debug("XADD streamKey={} recordId={} fieldCount={}", streamKey, idStr, fields.size());
        }
        return idStr;
    }

    /**
     * 入队便捷重载：业务层只关心 taskId 等强类型主字段时使用。
     *
     * <p>所有值经 {@link String#valueOf(Object)} 字符串化，{@code null} 替换为空串；
     * 主字段优先于 {@code extra} 写入（{@link LinkedHashMap} 保留顺序，便于 Redis
     * MONITOR 时人类阅读）。{@code extra} 中若包含与主字段同名的键，将被静默覆盖。
     *
     * @param streamKey    Redis Stream key
     * @param taskIdField  主字段名（例如 {@code "taskId"}）
     * @param taskIdValue  主字段值；{@code null} 视为空串
     * @param extra        附加字段；{@code null} 等价空 map
     * @return {@link #enqueue(String, Map)} 的返回值
     */
    public String enqueue(String streamKey, String taskIdField, Object taskIdValue, Map<String, Object> extra) {
        if (taskIdField == null || taskIdField.isBlank()) {
            throw new IllegalArgumentException("taskIdField must not be null or blank");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(taskIdField, stringify(taskIdValue));
        if (extra != null) {
            for (Map.Entry<String, Object> e : extra.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank() || taskIdField.equals(e.getKey())) {
                    continue;
                }
                fields.put(e.getKey(), stringify(e.getValue()));
            }
        }
        return enqueue(streamKey, fields);
    }

    /**
     * 返回当前 stream 长度（{@code XLEN streamKey}）。
     *
     * <p>当 stream 不存在时 Spring Data Redis 返回 0 而非抛异常；本方法做空值兜底。
     *
     * @param streamKey Redis Stream key
     * @return 队列长度（0 表示空 / 不存在）
     */
    public long pendingCount(String streamKey) {
        if (streamKey == null || streamKey.isBlank()) {
            throw new IllegalArgumentException("streamKey must not be null or blank");
        }
        Long size = stringRedisTemplate.opsForStream().size(streamKey);
        long n = size == null ? 0L : size;
        if (log.isDebugEnabled()) {
            log.debug("XLEN streamKey={} size={}", streamKey, n);
        }
        return n;
    }

    private static String stringify(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
