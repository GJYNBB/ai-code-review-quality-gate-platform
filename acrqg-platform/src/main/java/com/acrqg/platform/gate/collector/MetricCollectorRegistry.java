package com.acrqg.platform.gate.collector;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MetricCollector 路由表（design.md §10.1 编排器）。
 *
 * <p>构造时注入容器中所有 {@link MetricCollector} 实现，按 {@link MetricCollector#metric()}
 * 装入只读 Map；{@code collect(metric, ctx)} 按 metric 名分发执行。
 *
 * <ul>
 *   <li>同名重复实现：日志告警，后注册者获胜（理论上不会发生，作为防御）；</li>
 *   <li>未知 metric：抛 {@link BusinessException}（{@link ErrorCode#GATE_RULE_INVALID}），
 *       表示规则配置中出现了不被支持的 metric——通常是 schema 外的人工 SQL 改写
 *       绕过了 CHECK 约束（极端兜底）。</li>
 * </ul>
 *
 * <p>本类无可变状态（{@code collectorsByMetric} 在构造期完成填充后不再修改），
 * 多线程并发安全。
 *
 * <p>Covers: R13.6, R14.1, R14.2。
 */
@Component
public class MetricCollectorRegistry {

    private static final Logger log = LoggerFactory.getLogger(MetricCollectorRegistry.class);

    private final Map<String, MetricCollector> collectorsByMetric;

    public MetricCollectorRegistry(List<MetricCollector> collectors) {
        Map<String, MetricCollector> map = new LinkedHashMap<>();
        if (collectors != null) {
            for (MetricCollector c : collectors) {
                String key = c.metric();
                if (key == null || key.isBlank()) {
                    log.warn("MetricCollectorRegistry: collector {} returns null/blank metric(); ignored",
                            c.getClass().getName());
                    continue;
                }
                MetricCollector prev = map.put(key, c);
                if (prev != null) {
                    log.warn("MetricCollectorRegistry: duplicate collectors for metric={} ({} -> {}); later wins",
                            key, prev.getClass().getName(), c.getClass().getName());
                }
            }
        }
        this.collectorsByMetric = Collections.unmodifiableMap(map);
        log.info("MetricCollectorRegistry initialised: metrics={}", collectorsByMetric.keySet());
    }

    /** 已注册的全部 metric 名（只读视图）。 */
    public java.util.Set<String> metrics() {
        return collectorsByMetric.keySet();
    }

    /**
     * 按 metric 名采集实际值。
     *
     * @param metric metric 名
     * @param ctx    上下文
     * @return 实际值（不会为 {@code null}）
     * @throws BusinessException {@link ErrorCode#GATE_RULE_INVALID} 当 metric 未知时
     */
    public BigDecimal collect(String metric, MetricContext ctx) {
        MetricCollector c = collectorsByMetric.get(metric);
        if (c == null) {
            throw new BusinessException(ErrorCode.GATE_RULE_INVALID,
                    "unknown metric: " + metric);
        }
        BigDecimal v = c.collect(ctx);
        return v == null ? BigDecimal.ZERO : v;
    }
}
