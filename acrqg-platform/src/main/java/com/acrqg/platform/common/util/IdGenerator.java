package com.acrqg.platform.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 平台统一标识符生成工具。
 *
 * <p>提供三类标识：
 * <ul>
 *   <li>{@link #taskNo()}：评审任务编号 {@code RT{yyyyMMdd}{6 位序列}}，例如
 *       {@code RT20260519000001}。日切（按本机时区，默认 {@code Asia/Shanghai}，
 *       与 {@code application.yml} 对齐）后序列重置为 1。</li>
 *   <li>{@link #requestId()}：请求级标识 {@code req_{yyyyMMddHHmmss}{4 位序列}}，
 *       供 {@code TraceIdFilter} 在 {@code X-Request-Id} 头缺失时兜底生成。</li>
 *   <li>{@link #uuid()} / {@link #uuidNoDash()}：标准 UUID（带或不带连字符），
 *       用于 JWT {@code jti}、幂等键等。</li>
 * </ul>
 *
 * <p>实现说明：
 * <ul>
 *   <li>使用 {@link AtomicLong} 保证序列在多线程下递增的原子性；</li>
 *   <li>每次生成 {@link #taskNo()} 时检查与上次记录的"日期 key"是否相同，不同则
 *       通过 CAS 将序列重置为 0；该过程不持久化，重启进程后从 0 起步——任务编号在
 *       数据库层另有 {@code uk_review_task_task_no} 约束兜底，重启撞号几乎不会发生
 *       但即使发生也会被插入失败感知到。</li>
 *   <li>序列对 10^6（taskNo）/ 10^4（requestId）取模，避免异常激增时溢出。</li>
 * </ul>
 *
 * <p>Covers: R7.4, R8.4 (幂等键), R9.7, R23.1 (requestId), R24.5 (链路追踪)。
 */
public final class IdGenerator {

    /** 业务时区。与 {@code application.yml} 中 {@code spring.jackson.time-zone} 对齐。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT);

    /** 评审任务序列：当日累计计数。 */
    private static final AtomicLong TASK_NO_SEQ = new AtomicLong(0L);
    /** 评审任务上一次生成时的日期 key（{@code yyyyMMdd} 转 long）。 */
    private static final AtomicLong TASK_NO_DATE_KEY = new AtomicLong(0L);

    /** 请求 id 序列：每秒级累计，模 10^4。 */
    private static final AtomicLong REQUEST_ID_SEQ = new AtomicLong(0L);

    private IdGenerator() {
        // utility class
    }

    /**
     * 生成评审任务编号 {@code RT{yyyyMMdd}{6 位序列}}。
     *
     * @return 形如 {@code "RT20260519000001"} 的字符串
     */
    public static String taskNo() {
        LocalDate now = LocalDate.now(ZONE);
        long todayKey = Long.parseLong(now.format(DATE_FMT));
        // 跨天则把序列与日期 key 一起 CAS 重置；多线程下"先到的线程"完成重置
        long currentDateKey = TASK_NO_DATE_KEY.get();
        if (currentDateKey != todayKey
                && TASK_NO_DATE_KEY.compareAndSet(currentDateKey, todayKey)) {
            TASK_NO_SEQ.set(0L);
        }
        long seq = TASK_NO_SEQ.incrementAndGet() % 1_000_000L;
        return String.format(Locale.ROOT, "RT%08d%06d", todayKey, seq);
    }

    /**
     * 生成请求级标识 {@code req_{yyyyMMddHHmmss}{4 位序列}}。
     *
     * @return 形如 {@code "req_202605191230450001"} 的字符串
     */
    public static String requestId() {
        String ts = LocalDateTime.now(ZONE).format(DATETIME_FMT);
        long seq = REQUEST_ID_SEQ.incrementAndGet() % 10_000L;
        return String.format(Locale.ROOT, "req_%s%04d", ts, seq);
    }

    /** 标准 UUID（带连字符，36 字符）。 */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /** UUID 去除连字符（32 字符）。 */
    public static String uuidNoDash() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
