package com.acrqg.platform.infra.redis;

import java.util.function.Predicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 相关辅助 bean 装配。
 *
 * <p>本 {@link Configuration} 仅承担一个职责：把 {@link JwtBlacklist#contains(String)}
 * 暴露为名为 {@code jwtJtiBlacklist} 的 {@code Predicate<String>} bean，
 * 供 B0-A.4 实现的 {@code JwtAuthFilter} 在请求入口处通过
 * {@code ObjectProvider<Predicate<String>>} 软引用方式查询 jti 是否在黑名单中。
 *
 * <p>之所以单独建一个 {@link Configuration} 而不在 {@link JwtBlacklist} 上直接放
 * {@code @Bean} 方法，是因为 {@code @Bean} 必须声明在 {@code @Configuration}
 * （或 {@code @Component}）类的"普通方法"上，而我们希望 {@link JwtBlacklist} 自身保持
 * "纯组件"语义（仅 add / contains / removeUser），不参与 bean 工厂职责。
 *
 * <p>TODO B0-A.4：本 SPI bean 名称与 {@code JwtAuthFilter} 中
 * {@code blacklistProvider} 的注入声明保持一致；如调整任一处需同步修改另一处。
 *
 * <p>Covers: R3.2。
 */
@Configuration
public class RedisBeansConfig {

    /**
     * 暴露 jti 黑名单查询 SPI。
     *
     * <p>采用方法引用 {@code blacklist::contains}，避免 lambda 闭包带来的额外类生成；
     * 同时保证未来若 {@link JwtBlacklist} 实现切换为其他存储（例如本地 Caffeine
     * 二级缓存），调用方无需改动。
     */
    @Bean(name = "jwtJtiBlacklist")
    public Predicate<String> jwtJtiBlacklist(JwtBlacklist blacklist) {
        return blacklist::contains;
    }
}
