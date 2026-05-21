package com.acrqg.platform.repository.client;

import com.acrqg.platform.repository.domain.Provider;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link ProviderClient} 工厂（design.md §6.2）。
 *
 * <p>Spring 容器启动时自动注入所有 {@link ProviderClient} 实现 bean，
 * 在 {@link #init()} 阶段按 {@link ProviderClient#name()} 建立索引；
 * 业务模块（{@code RepositoryServiceImpl}、B3-C 的 {@code DiffOrchestrator}、
 * B4-E 的 {@code WritebackService}）通过 {@link #byProvider(Provider)}
 * 取实例，避免在业务代码内 hardcode 任一具体实现。
 *
 * <p>线程安全：构建一次后 {@link #registry} 只读，map 字段标记为 {@code final}
 * 且引用不再变更，可被任意线程并发查询。
 *
 * <p>Covers: R5.1, R10.1, R20.1。
 */
@Component
public class ProviderClientFactory {

    private static final Logger log = LoggerFactory.getLogger(ProviderClientFactory.class);

    private final List<ProviderClient> clients;
    private final Map<String, ProviderClient> registry = new HashMap<>();

    public ProviderClientFactory(List<ProviderClient> clients) {
        this.clients = clients;
    }

    /** 启动期建索引；缺少任一 Provider 实现即抛错阻止上下文启动。 */
    @PostConstruct
    void init() {
        for (ProviderClient client : clients) {
            String name = client.name();
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException(
                        "ProviderClient bean returned null/empty name(): " + client.getClass().getName());
            }
            String key = name.toUpperCase(Locale.ROOT);
            ProviderClient previous = registry.put(key, client);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate ProviderClient for name=" + key
                                + " : " + previous.getClass().getName()
                                + " vs " + client.getClass().getName());
            }
        }
        // 三大平台必须齐全；缺失即配置错误
        for (Provider p : Provider.values()) {
            if (!registry.containsKey(p.name())) {
                throw new IllegalStateException(
                        "Missing ProviderClient implementation for provider=" + p.name());
            }
        }
        log.info("ProviderClientFactory initialized: {}", registry.keySet());
    }

    /**
     * 按 {@link Provider} 取对应 {@link ProviderClient}。
     *
     * @param provider 平台枚举
     * @return 实现 bean，永不为 {@code null}
     * @throws IllegalArgumentException 当 {@code provider} 为 {@code null}
     * @throws IllegalStateException    当未找到匹配实现（理论上 {@link #init()} 已经兜底）
     */
    public ProviderClient byProvider(Provider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        ProviderClient client = registry.get(provider.name());
        if (client == null) {
            throw new IllegalStateException("no ProviderClient registered for " + provider.name());
        }
        return client;
    }
}
