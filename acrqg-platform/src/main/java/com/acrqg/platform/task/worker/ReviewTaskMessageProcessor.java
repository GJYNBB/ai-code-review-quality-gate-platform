package com.acrqg.platform.task.worker;

import com.acrqg.platform.task.service.impl.ReviewTaskServiceImpl;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Shared Redis Stream record processor used by normal consumption and PEL recovery. */
@Component
@Profile("worker")
public class ReviewTaskMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReviewTaskMessageProcessor.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final TaskOrchestrator taskOrchestrator;

    public ReviewTaskMessageProcessor(StringRedisTemplate stringRedisTemplate,
                                      TaskOrchestrator taskOrchestrator) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.taskOrchestrator = taskOrchestrator;
    }

    public void process(MapRecord<String, Object, Object> record) {
        String recordId = record.getId() == null ? null : record.getId().getValue();
        process(recordId, record.getValue());
    }

    public void process(String recordId, Map<Object, Object> fields) {
        Long taskId = parseTaskId(fields);
        boolean attemptPresent = fields != null && fields.containsKey("attempt");
        Integer attempt = parseAttempt(fields);
        if (taskId == null) {
            log.warn("processRecord: invalid taskId in record, skipping. recordId={} fields={}",
                    recordId, fields);
            ackQuietly(recordId);
            return;
        }
        if (attemptPresent && attempt == null) {
            log.warn("processRecord: invalid attempt in record, skipping. recordId={} taskId={} fields={}",
                    recordId, taskId, fields);
            ackQuietly(recordId);
            return;
        }
        try {
            if (attempt == null) {
                taskOrchestrator.run(taskId);
            } else {
                taskOrchestrator.run(taskId, attempt);
            }
            ackQuietly(recordId);
        } catch (RuntimeException ex) {
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
                    ReviewTaskServiceImpl.STREAM_KEY, ReviewTaskConsumer.CONSUMER_GROUP, recordId);
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

    private static Integer parseAttempt(Map<Object, Object> fields) {
        if (fields == null) {
            return null;
        }
        Object v = fields.get("attempt");
        if (v == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(v).trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
