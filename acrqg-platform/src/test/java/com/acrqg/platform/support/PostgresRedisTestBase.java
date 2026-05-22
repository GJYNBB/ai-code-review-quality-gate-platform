package com.acrqg.platform.support;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 共享的 Postgres + Redis Testcontainers 基类（B6-A 验证批次）。
 *
 * <p>每个集成测试类 {@code extends} 本类即可获得：
 * <ul>
 *   <li>一个 Postgres 15 容器（{@code postgres:15-alpine}），同时被
 *       {@code spring.datasource.*} 与 {@code spring.flyway.*} 共享，
 *       Flyway 自动迁移 V1__init.sql 等所有迁移；</li>
 *   <li>一个 Redis 7 容器（{@code redis:7-alpine}），通过 {@code spring.data.redis.url}
 *       注入；</li>
 *   <li>固定的 {@code TOKEN_ENCRYPTION_KEY} / {@code JWT_SECRET}（避免随机化导致每次
 *       重跑 fixture 数据无法解密）；</li>
 *   <li>{@link DynamicPropertySource} 在容器启动后注入到 Spring 环境，无需
 *       application-test.yml 中硬编码地址。</li>
 * </ul>
 *
 * <p>容器使用 {@code .withReuse(true)} 暗示 testcontainers 在本地启用 reuse 时
 * 跨测试类共享同一个 daemon 容器；在 CI 环境（默认 reuse=false）会按测试类正常
 * 创建 / 销毁。
 */
@Testcontainers
public abstract class PostgresRedisTestBase {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("quality_gate_it")
            .withUsername("acrqg_it")
            .withPassword("acrqg_it_pwd")
            .withReuse(true);

    @Container
    protected static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort())
            .withReuse(true);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);

        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.url",
                () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379) + "/0");

        registry.add("app.security.jwt.secret",
                () -> "it-fixed-32bytes-jwt-secret-please-change-when-rotated");
        registry.add("app.security.token-encryption-key",
                () -> "it-fixed-32-bytes-token-encryption-key!!");

        // 关闭与外部资源相关的占位（默认 base-url 留空表示 AI 不可用，单测中按需覆盖）
        registry.add("app.ai.review.base-url", () -> "");
        registry.add("app.webhook.base-url", () -> "https://it.local:8080");
    }

    /**
     * 暴露给子类的便捷方法：探测容器是否启动完成（用于断言）。
     */
    protected static boolean containersStarted() {
        InspectContainerResponse pg = POSTGRES.getContainerInfo();
        InspectContainerResponse rd = REDIS.getContainerInfo();
        return pg != null && Boolean.TRUE.equals(pg.getState().getRunning())
                && rd != null && Boolean.TRUE.equals(rd.getState().getRunning());
    }
}
