package com.acrqg.platform.task.log;

/**
 * MDC 键名常量，集中管理便于跨模块对齐
 * （{@code logback-spring.xml} JSON encoder + {@code TraceIdFilter} +
 * {@code TaskLogger} 三处需要保持一致）。
 *
 * <p>Covers: R24.5。
 */
public final class LoggingMDCKey {

    /** 链路追踪 ID，由 {@code TraceIdFilter} 写入。 */
    public static final String TRACE_ID = "traceId";

    /**
     * 评审任务编号 RT{yyyyMMdd}{seq}，由 {@link TaskLogger} 在写入 task_log
     * 前临时设置，方法退出时清理。
     */
    public static final String TASK_NO = "taskNo";

    /** 当前 worker 进程身份。 */
    public static final String WORKER_ID = "workerId";

    private LoggingMDCKey() {
        // utility class
    }
}
