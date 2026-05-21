package com.acrqg.platform.writeback.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Writeback 模块的异步配置。
 *
 * <p>注册名为 {@value #EXECUTOR_BEAN_NAME} 的 {@link ThreadPoolTaskExecutor}：
 * <ul>
 *   <li>core = 2 / max = 4 / queue = 256；</li>
 *   <li>线程名前缀 {@code writeback-}；</li>
 *   <li>队列满时退化为 {@link ThreadPoolExecutor.CallerRunsPolicy}，
 *       保证回写不丢失；</li>
 *   <li>{@code waitForTasksToCompleteOnShutdown=true} + {@code awaitTerminationSeconds=10}
 *       支持优雅关闭——回写最坏 1+2+4=7s 重试，10s 足够冲刷；</li>
 *   <li>同时启用 {@link EnableAsync}，使 {@code @Async("writebackTaskExecutor")}
 *       注解在本模块（及订阅 {@link com.acrqg.platform.writeback.listener.WritebackTaskListener
 *       TaskFinishedEvent}）的 listener 上生效。</li>
 * </ul>
 *
 * <p>{@link AuditAsyncExceptionHandler} 与 audit 模块同款：异步抛异常仅记 WARN，
 * 不影响业务调用方。
 *
 * <p>Covers: R20.4, R24.4。
 */
@Configuration
@EnableAsync
public class WritebackAsyncConfig {

    /** {@code @Async("writebackTaskExecutor")} 中使用的 bean 名称。 */
    public static final String EXECUTOR_BEAN_NAME = "writebackTaskExecutor";

    private static final Logger log = LoggerFactory.getLogger(WritebackAsyncConfig.class);

    /**
     * Writeback 专用线程池。core=2 / max=4 / queue=256 / 名称前缀 "writeback-"。
     *
     * <p>对外以 bean 名称 {@value EXECUTOR_BEAN_NAME} 暴露；
     * {@code @Async("writebackTaskExecutor")} 显式选用。
     */
    @Bean(name = EXECUTOR_BEAN_NAME)
    public Executor writebackTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(256);
        exec.setThreadNamePrefix("writeback-");
        exec.setKeepAliveSeconds(60);
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(10);
        exec.initialize();
        return exec;
    }

    /**
     * 异步任务未捕获异常处理器：仅记录日志，不向上抛出。
     *
     * <p>WritebackServiceImpl 内部已捕获所有 {@link com.acrqg.platform.writeback.exception.WritebackException
     * WritebackException} 并写 task_log，理论上不会有未捕获异常逃逸；本 handler
     * 仅作兜底（例如 mapper 抛 RuntimeException）。
     */
    @Bean
    public AsyncUncaughtExceptionHandler writebackAsyncExceptionHandler() {
        return new WritebackAsyncExceptionHandler();
    }

    static final class WritebackAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.warn("writeback async task threw uncaught exception in method {}",
                    method != null ? method.getName() : "<unknown>", ex);
        }
    }
}
