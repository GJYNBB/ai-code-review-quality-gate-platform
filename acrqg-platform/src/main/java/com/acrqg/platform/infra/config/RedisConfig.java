package com.acrqg.platform.infra.config;

import com.acrqg.platform.common.util.JsonUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 模板配置。
 *
 * <p>底层 {@link io.lettuce.core.api.StatefulRedisConnection Lettuce} 连接由 Spring Boot
 * 通过 {@code spring.data.redis.url} 自动装配（详见 {@code application.yml}），
 * 因此本类只声明上层 {@link RedisTemplate} / {@link StringRedisTemplate} bean，不再重复
 * 定义 {@code RedisConnectionFactory}。
 *
 * <p>Bean 概览：
 * <ul>
 *   <li>{@link #stringRedisTemplate(RedisConnectionFactory)}：纯字符串模板，供
 *       Redis Stream（{@code XADD} / {@code XLEN}）、JWT 黑名单
 *       （{@code SET jwt:bl:jti:* 1 EX *}）、幂等键（{@code SET idem:* NX EX *}）
 *       等纯字符串场景使用。这是 B0-A.6 三个组件的主要入口。</li>
 *   <li>{@link #redisTemplate(RedisConnectionFactory)}：通用对象模板，key/hashKey 使用
 *       {@link StringRedisSerializer} 保持人类可读，value/hashValue 使用
 *       {@link GenericJackson2JsonRedisSerializer}（共享 {@link JsonUtils#mapper()}
 *       的 JavaTime 配置），供后续业务层缓存领域对象使用。</li>
 * </ul>
 *
 * <p><b>注意</b>：当前版本 Spring Boot 自动装配的 {@code redisTemplate} 默认使用
 * {@code JdkSerializationRedisSerializer}（产生不可读的二进制内容），本类显式覆盖
 * 为 JSON 序列化以保证 Redis 中的内容可读且跨语言兼容。
 *
 * <p>Covers: R3.2 (JWT 黑名单), R7.4 / R8.4 (幂等键), R24.6 (依赖 Redis 连通性的
 * 健康指示器与 Stream 队列长度)。
 *
 * @see com.acrqg.platform.infra.redis.RedisStreamPublisher
 * @see com.acrqg.platform.infra.redis.IdempotencyStore
 * @see com.acrqg.platform.infra.redis.JwtBlacklist
 */
@Configuration
public class RedisConfig {

    /**
     * 纯字符串模板。
     *
     * <p>等价于 {@code new StringRedisTemplate(factory)}，所有 key/value 均用
     * {@link StringRedisSerializer} 序列化为 UTF-8 字符串；适合 Redis Stream
     * （fields 必须是字符串）与简单的 SET / EXPIRE / SADD 操作。
     *
     * <p>Spring Boot 默认已自动配置同名 bean，本方法显式声明以便后续替换或扩展
     * （例如增加 RedisSerializer 的 ASCII-only 校验）时改动收敛在一处。
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 通用对象模板。
     *
     * <p>序列化器组合：
     * <pre>
     *   key / hashKey      -> StringRedisSerializer
     *   value / hashValue  -> GenericJackson2JsonRedisSerializer(JsonUtils.mapper())
     * </pre>
     * 这样写入 Redis 的内容是 {@code key=java.lang.String, value=JSON 文本}，便于
     * {@code redis-cli MONITOR} 与运维排障。复用 {@link JsonUtils#mapper()} 主要为了
     * 共享 {@code JavaTimeModule} 配置（{@link java.time.LocalDateTime} 等可直接序列化）。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(JsonUtils.mapper());

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.setDefaultSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
