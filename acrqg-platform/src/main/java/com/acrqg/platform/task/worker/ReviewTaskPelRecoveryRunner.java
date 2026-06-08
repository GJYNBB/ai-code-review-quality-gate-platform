package com.acrqg.platform.task.worker;

import com.acrqg.platform.task.service.impl.ReviewTaskServiceImpl;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reclaims idle Redis Stream PEL entries with XAUTOCLAIM and processes them like normal deliveries. */
@Component
@Profile("worker")
public class ReviewTaskPelRecoveryRunner {

    private static final Logger log = LoggerFactory.getLogger(ReviewTaskPelRecoveryRunner.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ReviewTaskMessageProcessor messageProcessor;
    private final boolean enabled;
    private final Duration minIdle;
    private final int batchSize;
    private final int maxClaimCount;
    private final String consumerName = WorkerIdentity.current();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ReviewTaskPelRecoveryRunner(
            StringRedisTemplate stringRedisTemplate,
            ReviewTaskMessageProcessor messageProcessor,
            @Value("${app.worker.pel-recovery.enabled:true}") boolean enabled,
            @Value("${app.worker.pel-recovery.min-idle-ms:300000}") long minIdleMs,
            @Value("${app.worker.pel-recovery.batch-size:50}") int batchSize,
            @Value("${app.worker.pel-recovery.max-claim-count:200}") int maxClaimCount) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.messageProcessor = messageProcessor;
        this.enabled = enabled;
        this.minIdle = Duration.ofMillis(Math.max(minIdleMs, 1));
        this.batchSize = Math.max(batchSize, 1);
        this.maxClaimCount = Math.max(maxClaimCount, 1);
    }

    @Scheduled(initialDelayString = "${app.worker.pel-recovery.initial-delay-ms:45000}",
            fixedDelayString = "${app.worker.pel-recovery.fixed-delay-ms:60000}")
    void scheduledPelRecovery() {
        if (!enabled) {
            return;
        }
        recoverPendingEntries();
    }

    int recoverPendingEntries() {
        if (!running.compareAndSet(false, true)) {
            log.debug("PEL recovery skipped because previous run is still active");
            return 0;
        }
        try {
            int total = 0;
            String startId = "0-0";
            while (total < maxClaimCount) {
                AutoClaimResult result = autoClaim(startId, Math.min(batchSize, maxClaimCount - total));
                if (result.entries().isEmpty()) {
                    break;
                }
                for (ClaimedEntry entry : result.entries()) {
                    messageProcessor.process(entry.recordId(), entry.fields());
                    total++;
                }
                if ("0-0".equals(result.nextStartId())) {
                    break;
                }
                startId = result.nextStartId();
            }
            if (total > 0) {
                log.info("PEL recovery claimed and processed {} stream record(s)", total);
            }
            return total;
        } catch (RuntimeException ex) {
            log.warn("PEL recovery failed: err={}", ex.toString(), ex);
            return 0;
        } finally {
            running.set(false);
        }
    }

    private AutoClaimResult autoClaim(String startId, int count) {
        Object raw = stringRedisTemplate.execute((RedisCallback<Object>) connection -> connection.execute(
                "XAUTOCLAIM",
                bytes(ReviewTaskServiceImpl.STREAM_KEY),
                bytes(ReviewTaskConsumer.CONSUMER_GROUP),
                bytes(consumerName),
                bytes(String.valueOf(minIdle.toMillis())),
                bytes(startId),
                bytes("COUNT"),
                bytes(String.valueOf(count))));
        return parseAutoClaimResult(raw);
    }

    private static AutoClaimResult parseAutoClaimResult(Object raw) {
        if (!(raw instanceof List<?> root) || root.size() < 2) {
            return new AutoClaimResult("0-0", List.of());
        }
        String next = stringValue(root.get(0));
        Object entriesRaw = root.get(1);
        if (!(entriesRaw instanceof List<?> entriesList) || entriesList.isEmpty()) {
            return new AutoClaimResult(next == null ? "0-0" : next, List.of());
        }
        List<ClaimedEntry> entries = entriesList.stream()
                .map(ReviewTaskPelRecoveryRunner::parseEntry)
                .filter(e -> e != null)
                .toList();
        return new AutoClaimResult(next == null ? "0-0" : next, entries);
    }

    private static ClaimedEntry parseEntry(Object rawEntry) {
        if (!(rawEntry instanceof List<?> entry) || entry.size() < 2) {
            return null;
        }
        String id = stringValue(entry.get(0));
        if (id == null || id.isBlank()) {
            return null;
        }
        Map<Object, Object> fields = new LinkedHashMap<>();
        if (entry.get(1) instanceof List<?> fieldList) {
            for (int i = 0; i + 1 < fieldList.size(); i += 2) {
                fields.put(stringValue(fieldList.get(i)), stringValue(fieldList.get(i + 1)));
            }
        }
        return new ClaimedEntry(id, fields);
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record AutoClaimResult(String nextStartId, List<ClaimedEntry> entries) {
    }

    private record ClaimedEntry(String recordId, Map<Object, Object> fields) {
    }
}
