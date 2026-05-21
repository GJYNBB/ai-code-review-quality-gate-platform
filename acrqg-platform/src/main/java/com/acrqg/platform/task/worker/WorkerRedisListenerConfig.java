package com.acrqg.platform.task.worker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * worker profile 专用：注册 {@link RedisMessageListenerContainer}，供
 * {@link ReviewTaskConsumer} 订阅 {@code param-changed:review.worker.concurrency}
 * 通道实现并发热更新（R24.3）。
 *
 * <p>仅在 worker profile 下注册：api 进程不需要消费消息也不需要订阅参数变更，
 * 避免无谓的 Lettuce 长连接占用。
 *
 * <p>Covers: R21.4, R24.3。
 */
@Configuration
@Profile("worker")
public class WorkerRedisListenerConfig {

    /**
     * Redis pub/sub 监听容器。Spring 在容器启动时自动调用 {@code start}。
     */
    @Bean(destroyMethod = "destroy")
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
