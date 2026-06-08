package com.acrqg.platform.infra.health;

import com.acrqg.platform.admin.domain.ModelConfig;
import com.acrqg.platform.admin.repository.ModelConfigMapper;
import com.acrqg.platform.infra.net.GuardedRestClientFactory;
import com.acrqg.platform.infra.net.OutboundUrlGuard;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * AI 评审服务健康指示器（B3-E.6）。
 *
 * <p>由 Spring Boot Actuator 自动发现并注册到 {@code /actuator/health}：
 * bean 名称去掉 {@code HealthIndicator} 后缀作为标识，本类暴露的指示器名称为
 * <b>{@code aiService}</b>。
 *
 * <h3>探活策略</h3>
 * <ul>
 *   <li>定时调度：{@link #probe()} 每 30 秒执行一次（首次延迟 5s 让上下文就绪）；</li>
 *   <li>探活动作：选 enabled 的第一条 {@link ModelConfig}，对其
 *       {@code base_url}（即 OpenAI 兼容根） 发起 HEAD 请求，5s 连 / 5s 读超时；</li>
 *   <li>结果缓存：成功 → {@link Status#UP}，失败 → {@link Status#DOWN}；
 *       未配置 enabled 模型 → {@link Status#UNKNOWN}（与 Boot 默认聚合策略中性）。</li>
 *   <li>4xx 视为"服务可达"（鉴权 / 路径错误不代表服务不可用）；
 *       5xx / 网络异常视为"不可用"。</li>
 * </ul>
 *
 * <p>聚合策略：默认 {@code HealthAggregator} 把 UNKNOWN 视为不影响整体的中性值
 * （仅 DOWN / OUT_OF_SERVICE 拉低整体），所以"未配置 AI 模型"不会导致整个进程被
 * K8s readiness 视为不健康。
 *
 * <p>线程安全：{@link #latest} 使用 {@link AtomicReference} 承载最近一次结果；
 * {@link #health()} 与 {@link #probe()} 之间通过原子引用做无锁同步。
 *
 * <p>Covers: R24.6。
 */
@Component
public class AiServiceHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(AiServiceHealthIndicator.class);

    /** 探活连接超时（毫秒）。 */
    static final int PROBE_CONNECT_MILLIS = 5_000;

    /** 探活读超时（毫秒）。 */
    static final int PROBE_READ_MILLIS = 5_000;

    /** 探活间隔（毫秒，30s）。 */
    static final long PROBE_INTERVAL_MILLIS = 30_000L;

    /** 启动后首次探活延迟（毫秒，5s）。 */
    static final long PROBE_INITIAL_DELAY_MILLIS = 5_000L;

    /**
     * 探活状态快照。三态：
     * <ul>
     *   <li>{@code status=UNKNOWN} —— 启动期未首次探活 / 未配置 enabled 模型；</li>
     *   <li>{@code status=UP}      —— 最近一次探活到达（含 4xx 鉴权错误）；</li>
     *   <li>{@code status=DOWN}    —— 最近一次探活失败（5xx / 超时 / 未知主机）。</li>
     * </ul>
     */
    record ProbeSnapshot(Status status, String detail, Instant timestamp) {

        static ProbeSnapshot unknown(String reason) {
            return new ProbeSnapshot(Status.UNKNOWN, reason, Instant.now());
        }

        static ProbeSnapshot up(String detail) {
            return new ProbeSnapshot(Status.UP, detail, Instant.now());
        }

        static ProbeSnapshot down(String detail) {
            return new ProbeSnapshot(Status.DOWN, detail, Instant.now());
        }
    }

    private final ModelConfigMapper modelConfigMapper;
    private final RestClient restClient;

    /** 最近一次探活结果；初始为 UNKNOWN，等待 {@link #probe()} 首次执行。 */
    private final AtomicReference<ProbeSnapshot> latest =
            new AtomicReference<>(ProbeSnapshot.unknown("not probed yet"));

    public AiServiceHealthIndicator(ModelConfigMapper modelConfigMapper) {
        this.modelConfigMapper = modelConfigMapper;
        this.restClient = buildClient();
    }

    @PostConstruct
    void init() {
        // 启动期不阻塞容器初始化；首次探活由 @Scheduled 在 5s 后触发。
        log.info("AiServiceHealthIndicator initialized; first probe in {}ms",
                PROBE_INITIAL_DELAY_MILLIS);
    }

    @Override
    public Health health() {
        ProbeSnapshot snapshot = latest.get();
        Health.Builder builder = Health.status(snapshot.status())
                .withDetail("lastProbeAt", snapshot.timestamp().toString());
        if (snapshot.detail() != null) {
            builder.withDetail("info", snapshot.detail());
        }
        return builder.build();
    }

    /**
     * 周期性探活：30s 一次（首次延迟 5s）。
     *
     * <p>本方法被 {@code @Scheduled} 触发，由 Spring 调度器线程执行；每次执行
     * 写入最新的 {@link ProbeSnapshot} 到 {@link #latest}，{@link #health()} 立刻
     * 读到最新值。
     */
    @Scheduled(initialDelay = PROBE_INITIAL_DELAY_MILLIS, fixedDelay = PROBE_INTERVAL_MILLIS)
    public void probe() {
        try {
            ModelConfig model = pickEnabledModel();
            if (model == null) {
                latest.set(ProbeSnapshot.unknown("no enabled model_config"));
                return;
            }
            String url = stripTrailingSlash(OutboundUrlGuard.requireHttpsPublicUrl(
                    model.getBaseUrl(), "AI model baseUrl").toString());
            // 用 GET 请求 / 根路径，HEAD 在很多 LLM 网关上不被支持。
            // 任何 2xx / 3xx / 4xx 都视为"服务可达"；5xx 与网络异常视为不可用。
            try {
                int status = restClient.get()
                        .uri(URI.create(url))
                        .retrieve()
                        .toBodilessEntity()
                        .getStatusCode()
                        .value();
                latest.set(ProbeSnapshot.up("model=" + model.getName()
                        + ", base=" + url + ", status=" + status));
            } catch (HttpClientErrorException e4xx) {
                // 4xx 表示"鉴权失败 / 路径不对"，但服务本身在线
                latest.set(ProbeSnapshot.up("model=" + model.getName()
                        + ", base=" + url + ", status=" + e4xx.getStatusCode().value()
                        + " (treated as reachable)"));
            } catch (HttpServerErrorException e5xx) {
                latest.set(ProbeSnapshot.down("model=" + model.getName()
                        + ", base=" + url + ", status=" + e5xx.getStatusCode().value()));
            } catch (ResourceAccessException ex) {
                latest.set(ProbeSnapshot.down("model=" + model.getName()
                        + ", base=" + url + ", network: " + simpleMessage(ex)));
            }
        } catch (RuntimeException ex) {
            // 兜底：任何未预期异常都置 DOWN，避免 indicator 被永远卡在 UNKNOWN
            log.warn("AiServiceHealthIndicator probe unexpected error: {}", ex.toString(), ex);
            latest.set(ProbeSnapshot.down("probe error: " + simpleMessage(ex)));
        }
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private ModelConfig pickEnabledModel() {
        try {
            QueryWrapper<ModelConfig> qw = new QueryWrapper<>();
            qw.eq("enabled", Boolean.TRUE).orderByAsc("id").last("LIMIT 1");
            return modelConfigMapper.selectOne(qw);
        } catch (RuntimeException ex) {
            // DB 抖动不应影响 indicator 的探活节奏；视为 UNKNOWN
            log.warn("AiServiceHealthIndicator pickEnabledModel failed: {}", ex.toString());
            return null;
        }
    }

    private static RestClient buildClient() {
        return GuardedRestClientFactory.build(
                Duration.ofMillis(PROBE_CONNECT_MILLIS),
                Duration.ofMillis(PROBE_READ_MILLIS));
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String simpleMessage(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }
}
