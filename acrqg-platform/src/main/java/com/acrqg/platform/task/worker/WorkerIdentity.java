package com.acrqg.platform.task.worker;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * 当前 Worker 进程身份。
 *
 * <p>格式：{@code worker-{HOSTNAME}-{PID}}；当无法获取 hostname / PID 时
 * 退化为 {@code worker-{8 位 UUID}} 兜底。
 *
 * <p>用于：
 * <ul>
 *   <li>{@link com.acrqg.platform.task.worker.ReviewTaskConsumer} 注册到
 *       Redis Stream 消费组的 consumer name；</li>
 *   <li>{@link TaskOrchestrator} 写 {@code task_log.detail} 时携带 workerId，
 *       便于多副本下故障定位（R24.5）。</li>
 * </ul>
 *
 * <p>静态缓存：进程内首次解析后即不再变化，避免反复 DNS 查询。
 *
 * <p>Covers: R24.4, R24.5。
 */
public final class WorkerIdentity {

    private static final String CURRENT = computeIdentity();

    private WorkerIdentity() {
        // utility class
    }

    /** 返回当前 Worker 身份字符串。 */
    public static String current() {
        return CURRENT;
    }

    private static String computeIdentity() {
        String host = resolveHostname();
        long pid = resolvePid();
        if (host != null && !host.isBlank()) {
            return "worker-" + host + "-" + pid;
        }
        return "worker-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String resolveHostname() {
        try {
            String h = InetAddress.getLocalHost().getHostName();
            if (h != null && !h.isBlank()) {
                return h;
            }
        } catch (UnknownHostException ex) {
            // fall through
        }
        // 兜底：环境变量
        String env = System.getenv("HOSTNAME");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return null;
    }

    private static long resolvePid() {
        try {
            return ProcessHandle.current().pid();
        } catch (RuntimeException ex) {
            return 0L;
        }
    }
}
