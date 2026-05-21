package com.acrqg.platform.task.worker;

import com.acrqg.platform.admin.dto.SystemParamDTO;
import com.acrqg.platform.admin.service.AdminService;
import com.acrqg.platform.task.service.impl.ReviewTaskServiceImpl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Redis Stream 消费者（worker profile 专用）。
 *
 * <p>设计要点（design.md §6.3.2）：
 * <ul>
 *   <li>Stream key：{@link ReviewTaskServiceImpl#STREAM_KEY}（{@code review-task-stream}）；</li>
 *   <li>Consumer Group：{@code review-worker-group}，启动期幂等创建
 *       （{@code BUSYGROUP} 错误被忽略）；</li>
 *   <li>Consumer ID：{@link WorkerIdentity#current()}；</li>
 *   <li>拉取：单个 dispatcher 线程使用阻塞 {@code XREADGROUP COUNT=N BLOCK=5000ms}
 *       拉取消息，提交到 {@link ThreadPoolExecutor} 并发执行；</li>
 *   <li>并发：从 {@link AdminService} 读 {@code review.worker.concurrency}（R21.4）
 *       初始化线程池；订阅 Redis pub/sub 通道
 *       {@code param-changed:review.worker.concurrency} 在收到通知时调用
 *       {@link ThreadPoolExecutor#setCorePoolSize} 与
 *       {@link ThreadPoolExecutor#setMaximumPoolSize} 热更新（R24.3）；</li>
 *   <li>ACK：业务流程结束后 {@code XACK}；异常不 ACK，由 PEL 中等待 XCLAIM 转移
 *       （超出 B3-A 范围；本阶段仅记 ERROR）。</li>
 * </ul>
 *
 * <p><b>仅在 worker profile 下注册</b>：与 4 个 Stage bean 一致，避免 api 进程
 * 被错误地用作消费者。
 *
 * <p>Covers: R7.6, R9, R24.3, R24.4。
 */
@Component
@Profile("worker")
public class ReviewTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReviewTaskConsumer.class);

    /** 与 design.md §6.3.2 一致的 consumer group 名。 */
    public static final String CONSUMER_GROUP = "review-worker-group";

    /** {@code review.worker.concurrency} 的合法范围（与 R21.4 / AdminService 一致）。 */
    private static final int CONCURRENCY_MIN = 1;
    private static final int CONCURRENCY_MAX = 32;

    /** 默认并发数（也是 application.yml 的默认值）。 */
    private static final int DEFAULT_CONCURRENCY = 4;

    /** {@code system_param} 的并发参数 key。 */
    private static final String PARAM_KEY_CONCURRENCY = "review.worker.concurrency";

    /** Redis pub/sub 通道前缀（与 AdminServiceImpl 中 CHANNEL_PARAM_CHANGED_PREFIX 对齐）。 */
    private static final String CHANNEL_PARAM_CHANGED_PREFIX = "param-changed:";

    /** XREADGROUP 单次拉取的最大消息数。 */
    private static final int XREAD_COUNT = 16;

    /** XREADGROUP BLOCK 时长。 */
    private static final Duration XREAD_BLOCK = Duration.ofSeconds(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectProvider<RedisMessageListenerContainer> messageListenerContainerProvider;
    private final TaskOrchestrator taskOrchestrator;
    private final ObjectProvider<AdminService> adminServiceProvider;

    /** 工作线程池；并发热更新时调整 core/max。 */
    private ThreadPoolExecutor workerExecutor;

    /** 单 dispatcher 线程，循环阻塞拉取并提交到工作线程池。 */
    private Thread dispatcher;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final String consumerName = WorkerIdentity.current();

    public ReviewTaskConsumer(StringRedisTemplate stringRedisTemplate,
                              ObjectProvider<RedisMessageListenerContainer> messageListenerContainerProvider,
                              TaskOrchestrator taskOrchestrator,
                              ObjectProvider<AdminService> adminServiceProvider) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.messageListenerContainerProvider = messageListenerContainerProvider;
        this.taskOrchestrator = taskOrchestrator;
        this.adminServiceProvider = adminServiceProvider;
    }

    // =====================================================================
    // 生命周期
    // =====================================================================

    @PostConstruct
    void start() {
        // 1) 创建 consumer group（幂等）
        ensureConsumerGroup();

        // 2) 初始化工作线程池
        int concurrency = clampConcurrency(loadConcurrencyFromAdmin());
        this.workerExecutor = new ThreadPoolExecutor(
                concurrency, concurrency,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "review-task-worker");
                    t.setDaemon(false);
                    return t;
                });

        // 3) 订阅 param-changed 通道
        registerParamChangedSubscription(concurrency);

        // 4) 启动 dispatcher 线程
        running.set(true);
        this.dispatcher = new Thread(this::dispatchLoop, "review-task-dispatcher");
        this.dispatcher.setDaemon(false);
        this.dispatcher.start();

        log.info("ReviewTaskConsumer started: consumer={} concurrency={}", consumerName, concurrency);
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (dispatcher != null) {
            dispatcher.interrupt();
            try {
                dispatcher.join(Duration.ofSeconds(5).toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        if (workerExecutor != null) {
            workerExecutor.shutdown();
            try {
                if (!workerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    workerExecutor.shutdownNow();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                workerExecutor.shutdownNow();
            }
        }
        log.info("ReviewTaskConsumer stopped: consumer={}", consumerName);
    }

    // =====================================================================
    // dispatcher loop
    // =====================================================================

    private void dispatchLoop() {
        StreamOperations<String, Object, Object> ops = stringRedisTemplate.opsForStream();
        StreamReadOptions readOptions = StreamReadOptions.empty()
                .count(XREAD_COUNT)
                .block(XREAD_BLOCK);
        Consumer consumer = Consumer.from(CONSUMER_GROUP, consumerName);
        StreamOffset<String> offset = StreamOffset.create(
                ReviewTaskServiceImpl.STREAM_KEY, ReadOffset.lastConsumed());

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                List<MapRecord<String, Object, Object>> records =
                        (List) ops.read(consumer, readOptions, offset);

                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (MapRecord<String, Object, Object> rec : records) {
                    submitToWorker(rec);
                }
            } catch (RuntimeException ex) {
                if (!running.get()) {
                    break;
                }
                // 临时连接异常等：sleep 1s 后继续，避免空转
                log.warn("dispatchLoop XREADGROUP failed: err={}", ex.toString());
                sleepQuietly(1000);
            }
        }
        log.info("dispatchLoop exit: consumer={}", consumerName);
    }

    private void submitToWorker(MapRecord<String, Object, Object> record) {
        try {
            workerExecutor.submit(() -> processRecord(record));
        } catch (RuntimeException ex) {
            // 提交失败（极少见，工作池已关闭）：不 ACK，等待 PEL 转移
            log.warn("submitToWorker rejected: recordId={} err={}",
                    record.getId() == null ? null : record.getId().getValue(), ex.toString());
        }
    }

    private void processRecord(MapRecord<String, Object, Object> record) {
        String recordId = record.getId() == null ? null : record.getId().getValue();
        Long taskId = parseTaskId(record.getValue());
        if (taskId == null) {
            log.warn("processRecord: invalid taskId in record, skipping. recordId={} fields={}",
                    recordId, record.getValue());
            // 仍 ACK 避免无效消息阻塞 PEL
            ackQuietly(recordId);
            return;
        }
        try {
            taskOrchestrator.run(taskId);
            ackQuietly(recordId);
        } catch (RuntimeException ex) {
            // 不 ACK：留在 PEL，等待 XCLAIM 或人工干预
            log.error("processRecord failed: taskId={} recordId={} err={}",
                    taskId, recordId, ex.toString(), ex);
        }
    }

    private void ackQuietly(String recordId) {
        if (recordId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForStream().acknowledge(
                    ReviewTaskServiceImpl.STREAM_KEY, CONSUMER_GROUP, recordId);
        } catch (RuntimeException ex) {
            log.warn("XACK failed: recordId={} err={}", recordId, ex.toString());
        }
    }

    private static Long parseTaskId(Map<Object, Object> fields) {
        if (fields == null) {
            return null;
        }
        Object v = fields.get("taskId");
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // =====================================================================
    // consumer group 初始化
    // =====================================================================

    private void ensureConsumerGroup() {
        try {
            stringRedisTemplate.opsForStream().createGroup(
                    ReviewTaskServiceImpl.STREAM_KEY,
                    ReadOffset.from("0"),
                    CONSUMER_GROUP);
            log.info("created consumer group {} on stream {}",
                    CONSUMER_GROUP, ReviewTaskServiceImpl.STREAM_KEY);
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            if (msg.contains("BUSYGROUP")) {
                // group 已存在：忽略
                log.debug("consumer group {} already exists", CONSUMER_GROUP);
            } else {
                log.warn("createGroup failed (may retry on first read): err={}", msg);
            }
        }
    }

    // =====================================================================
    // 并发热更新
    // =====================================================================

    private int loadConcurrencyFromAdmin() {
        AdminService admin = adminServiceProvider.getIfAvailable();
        if (admin == null) {
            return DEFAULT_CONCURRENCY;
        }
        try {
            SystemParamDTO p = admin.getParam(PARAM_KEY_CONCURRENCY);
            if (p == null || p.paramValue() == null) {
                return DEFAULT_CONCURRENCY;
            }
            return Integer.parseInt(p.paramValue().trim());
        } catch (RuntimeException ex) {
            log.warn("loadConcurrencyFromAdmin failed, using default: err={}", ex.toString());
            return DEFAULT_CONCURRENCY;
        }
    }

    private void registerParamChangedSubscription(int initialConcurrency) {
        RedisMessageListenerContainer container = messageListenerContainerProvider.getIfAvailable();
        if (container == null) {
            log.info("no RedisMessageListenerContainer bean; concurrency hot-update disabled");
            return;
        }
        MessageListener listener = (message, pattern) -> {
            String body = message.getBody() == null ? "" : new String(message.getBody());
            onConcurrencyChanged(body, initialConcurrency);
        };
        container.addMessageListener(listener,
                new ChannelTopic(CHANNEL_PARAM_CHANGED_PREFIX + PARAM_KEY_CONCURRENCY));
        log.info("subscribed channel {}{} for concurrency hot-update",
                CHANNEL_PARAM_CHANGED_PREFIX, PARAM_KEY_CONCURRENCY);
    }

    private void onConcurrencyChanged(String body, int fallback) {
        int target;
        try {
            target = clampConcurrency(Integer.parseInt(body.trim()));
        } catch (NumberFormatException ex) {
            log.warn("onConcurrencyChanged: invalid value '{}', keep current", body);
            return;
        }
        if (workerExecutor == null) {
            return;
        }
        // ThreadPoolExecutor 要求 max >= core；先扩容上限再调 core 才安全
        synchronized (workerExecutor) {
            if (target > workerExecutor.getMaximumPoolSize()) {
                workerExecutor.setMaximumPoolSize(target);
                workerExecutor.setCorePoolSize(target);
            } else {
                workerExecutor.setCorePoolSize(target);
                workerExecutor.setMaximumPoolSize(target);
            }
        }
        log.info("concurrency hot-updated: {} (fallback was {})", target, fallback);
    }

    private static int clampConcurrency(int v) {
        if (v < CONCURRENCY_MIN) {
            return CONCURRENCY_MIN;
        }
        if (v > CONCURRENCY_MAX) {
            return CONCURRENCY_MAX;
        }
        return v;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
