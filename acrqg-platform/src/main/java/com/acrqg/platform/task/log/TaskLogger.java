package com.acrqg.platform.task.log;

import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.domain.TaskLog;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.acrqg.platform.task.repository.TaskLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 任务流水写入 + SLF4J 日志的统一入口。
 *
 * <p>每次调用都会做两件事：
 * <ol>
 *   <li>把 {@code taskNo}（从缓存或数据库查到）注入 MDC，调用 SLF4J 输出
 *       INFO/WARN/ERROR 级别日志（满足 R24.5 链路追踪与
 *       {@code logback-spring.xml} 的 {@code taskNo} 字段要求）；</li>
 *   <li>把同一条信息持久化到 {@code task_log} 表，便于报告页与运维查询（R9.7）。</li>
 * </ol>
 *
 * <h3>缓存策略</h3>
 * <p>{@code taskNo} 在任务生命周期内不变，使用 Caffeine 缓存
 * （{@code TTL=10min, maxSize=10_000}），把"taskId → taskNo"的查询从数据库迁出。
 * 缓存是 best-effort 的：未命中时回库，仍命中失败则使用 {@code "RT?"} 兜底
 * （写库不会失败，仅日志中的 taskNo 字段缺失精确值）。
 *
 * <h3>失败处理</h3>
 * <p>写 task_log 失败时仅记 SLF4J ERROR 日志，不再重抛——这避免业务流程因
 * 流水写入失败而中断，符合 design.md §16.3 "task_log 是辅助记录，不是事务关键路径"。
 *
 * <p>Covers: R9.7, R24.5。
 */
@Component
public class TaskLogger {

    private static final Logger log = LoggerFactory.getLogger(TaskLogger.class);

    /** task_log.level 取值。 */
    public static final String LEVEL_INFO = "INFO";
    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_ERROR = "ERROR";

    /** 异常堆栈在 detail 中保存的最长字符数。 */
    private static final int STACK_TRACE_MAX_LEN = 4_000;

    /** task_log.message 最长字符数（VARCHAR 但实际为 TEXT，仍主动截断防爆）。 */
    private static final int MESSAGE_MAX_LEN = 4_000;

    private final TaskLogMapper taskLogMapper;
    private final ReviewTaskMapper reviewTaskMapper;
    private final ObjectMapper objectMapper;

    /** taskId → taskNo 缓存，TTL 10min。 */
    private final Cache<Long, String> taskNoCache;

    public TaskLogger(TaskLogMapper taskLogMapper,
                      ReviewTaskMapper reviewTaskMapper,
                      ObjectMapper objectMapper) {
        this.taskLogMapper = taskLogMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.objectMapper = objectMapper;
        this.taskNoCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(10_000L)
                .build();
    }

    // =====================================================================
    // public API（消息 + 可选 detail / throwable）
    // =====================================================================

    public void info(long taskId, String stage, String message) {
        write(LEVEL_INFO, taskId, stage, message, null, null);
    }

    public void info(long taskId, String stage, String message, Map<String, Object> detail) {
        write(LEVEL_INFO, taskId, stage, message, detail, null);
    }

    public void warn(long taskId, String stage, String message) {
        write(LEVEL_WARN, taskId, stage, message, null, null);
    }

    public void warn(long taskId, String stage, String message, Map<String, Object> detail) {
        write(LEVEL_WARN, taskId, stage, message, detail, null);
    }

    public void warn(long taskId, String stage, String message, Throwable throwable) {
        write(LEVEL_WARN, taskId, stage, message, null, throwable);
    }

    public void error(long taskId, String stage, String message) {
        write(LEVEL_ERROR, taskId, stage, message, null, null);
    }

    public void error(long taskId, String stage, String message, Map<String, Object> detail) {
        write(LEVEL_ERROR, taskId, stage, message, detail, null);
    }

    public void error(long taskId, String stage, String message, Throwable throwable) {
        write(LEVEL_ERROR, taskId, stage, message, null, throwable);
    }

    public void error(long taskId, String stage, String message,
                      Map<String, Object> detail, Throwable throwable) {
        write(LEVEL_ERROR, taskId, stage, message, detail, throwable);
    }

    // =====================================================================
    // internal
    // =====================================================================

    private void write(String level, long taskId, String stage, String message,
                       Map<String, Object> detail, Throwable throwable) {
        String safeStage = stage == null || stage.isBlank() ? "SYSTEM" : stage;
        String safeMessage = truncate(message == null ? "" : message, MESSAGE_MAX_LEN);

        // 注入 MDC 让 SLF4J 输出包含 taskNo 字段
        String taskNo = lookupTaskNo(taskId);
        String previousTaskNo = MDC.get(LoggingMDCKey.TASK_NO);
        if (taskNo != null) {
            MDC.put(LoggingMDCKey.TASK_NO, taskNo);
        }
        try {
            // 1) SLF4J 输出
            switch (level) {
                case LEVEL_ERROR -> {
                    if (throwable != null) {
                        log.error("[{}][{}] {}", taskId, safeStage, safeMessage, throwable);
                    } else {
                        log.error("[{}][{}] {}", taskId, safeStage, safeMessage);
                    }
                }
                case LEVEL_WARN -> {
                    if (throwable != null) {
                        log.warn("[{}][{}] {}", taskId, safeStage, safeMessage, throwable);
                    } else {
                        log.warn("[{}][{}] {}", taskId, safeStage, safeMessage);
                    }
                }
                default -> log.info("[{}][{}] {}", taskId, safeStage, safeMessage);
            }

            // 2) 持久化到 task_log
            try {
                TaskLog row = new TaskLog();
                row.setTaskId(taskId);
                row.setStage(safeStage);
                row.setLevel(level);
                row.setMessage(safeMessage);
                row.setDetailJson(buildDetailJson(detail, throwable));
                taskLogMapper.insertLog(row);
            } catch (RuntimeException ex) {
                // 流水写入失败不影响业务流程；仅记 ERROR
                log.error("TaskLogger.persist failed: taskId={} stage={} err={}",
                        taskId, safeStage, ex.toString(), ex);
            }
        } finally {
            // 还原 MDC（如果原本就有 taskNo，保持原值；否则移除）
            if (previousTaskNo == null) {
                MDC.remove(LoggingMDCKey.TASK_NO);
            } else {
                MDC.put(LoggingMDCKey.TASK_NO, previousTaskNo);
            }
        }
    }

    private String lookupTaskNo(long taskId) {
        return taskNoCache.get(taskId, key -> {
            try {
                ReviewTask t = reviewTaskMapper.selectById(key);
                return t != null ? t.getTaskNo() : null;
            } catch (RuntimeException ex) {
                log.warn("lookupTaskNo failed: taskId={} err={}", key, ex.toString());
                return null;
            }
        });
    }

    /** 把 detail map + 可选 throwable 序列化为 JSON 字符串；空时返回 null（不入 detail 列）。 */
    private String buildDetailJson(Map<String, Object> detail, Throwable throwable) {
        if ((detail == null || detail.isEmpty()) && throwable == null) {
            return null;
        }
        // 合并：detail + exception 字段（不修改入参 map）
        java.util.Map<String, Object> merged = new java.util.LinkedHashMap<>();
        if (detail != null) {
            merged.putAll(detail);
        }
        if (throwable != null) {
            merged.put("exceptionClass", throwable.getClass().getName());
            merged.put("exceptionMessage", truncate(
                    throwable.getMessage() == null ? "" : throwable.getMessage(), MESSAGE_MAX_LEN));
            merged.put("stackTrace", captureStackTrace(throwable));
        }
        try {
            return objectMapper.writeValueAsString(merged);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("TaskLogger.serialize detail failed: err={}", ex.toString());
            return "{}";
        }
    }

    private static String captureStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            t.printStackTrace(pw);
        }
        return truncate(sw.toString(), STACK_TRACE_MAX_LEN);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
