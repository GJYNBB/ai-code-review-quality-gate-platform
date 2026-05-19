package com.acrqg.platform.audit.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 审计模块的异步配置。
 *
 * <p>本类完成两件事：
 * <ol>
 *   <li>在 audit 模块本地启用 {@link EnableAsync}，使 {@code @Async("auditTaskExecutor")}
 *       注解生效。整个应用启动类（{@code AcrqgApplication}）<b>不</b>开启全局
 *       {@code @EnableAsync}，避免误把无关 service 的方法变为异步。</li>
 *   <li>注册名为 {@code auditTaskExecutor} 的 {@link ThreadPoolTaskExecutor} bean，
 *       专用于审计落库：
 *       <ul>
 *         <li>core = 2 —— 常驻 2 个线程，足以应对常态登录 / 状态变更类审计；</li>
 *         <li>max  = 4 —— 突发流量时最多 4 个并发写库线程，避免过多 JDBC 连接；</li>
 *         <li>queue = 1024 —— 缓冲短时尖峰；满后采用 {@link ThreadPoolExecutor.CallerRunsPolicy}
 *             退化到调用线程同步执行，<b>保证审计不丢失</b>（与 R22.1 一致）；</li>
 *         <li>thread-name-prefix = {@code audit-} —— 与 design.md §6.10 / 任务文档对齐；</li>
 *         <li>{@code waitForTasksToCompleteOnShutdown=true} + {@code awaitTerminationSeconds=10}
 *             —— 优雅关闭时尽量把待写日志冲刷掉。</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>实现 {@link AsyncConfigurer} 是为了：
 * <ul>
 *   <li>{@link #getAsyncExecutor()} 提供"未指定 executor 名称时的默认执行器"，
 *       但本模块统一显式使用 {@code @Async("auditTaskExecutor")}，因此该默认值
 *       仅作兜底；</li>
 *   <li>{@link #getAsyncUncaughtExceptionHandler()} 把异步执行抛出的未捕获异常
 *       转为 WARN 级日志，避免审计失败影响业务。</li>
 * </ul>
 *
 * <p>Covers: R22.1（关键操作必须记录审计；异步落库不影响业务主路径）。
 */
@Configuration
@EnableAsync
public class AuditAsyncConfig implements AsyncConfigurer {

    /** {@code @Async("auditTaskExecutor")} 中使用的 bean 名称。 */
    public static final String EXECUTOR_BEAN_NAME = "auditTaskExecutor";

    private static final Logger log = LoggerFactory.getLogger(AuditAsyncConfig.class);

    /**
     * 审计专用线程池。core=2 / max=4 / queue=1024 / 名称前缀 "audit-"。
     *
     * <p>对外以 bean 名称 {@value EXECUTOR_BEAN_NAME} 暴露，{@code @Async} 通过
     * 注解 {@code value} 显式选用：{@code @Async("auditTaskExecutor")}。
     */
    @Bean(name = EXECUTOR_BEAN_NAME)
    public ThreadPoolTaskExecutor auditTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(1024);
        exec.setThreadNamePrefix("audit-");
        exec.setKeepAliveSeconds(60);
        // 队列满时退化为调用线程执行，确保审计不丢失
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等待至多 10s 让队列中的审计任务完成
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(10);
        exec.initialize();
        return exec;
    }

    /**
     * 默认异步执行器（@Async 不带 value 时使用）。审计模块统一使用命名版本，
     * 这里仍返回同一个 bean 作为兜底，避免 Spring 因找不到默认 executor 而退化为
     * {@code SimpleAsyncTaskExecutor}（每次新建线程，性能差）。
     */
    @Override
    public Executor getAsyncExecutor() {
        return auditTaskExecutor();
    }

    /**
     * 异步任务未捕获异常处理器：仅记录日志，不向上抛出。
     *
     * <p>设计理由：审计写入失败不应影响业务主路径；即便审计落库失败，
     * 业务调用方在异步事件发布之后已经返回，向上抛出也无人接收。
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AuditAsyncExceptionHandler();
    }

    /**
     * 审计异步任务的未捕获异常处理器。
     */
    static final class AuditAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.warn("audit async task threw exception in method {} with {} param(s)",
                    method != null ? method.getName() : "<unknown>",
                    params == null ? 0 : params.length, ex);
        }
    }
}
