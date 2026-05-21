package com.acrqg.platform.gate.collector;

import com.acrqg.platform.gate.repository.GateMetricMapper;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * {@code security_issue_count} 指标采集（R14.1 安全规则维度）。
 *
 * <p>识别策略：{@code rule_code LIKE 'security.%' OR rule_code LIKE 'CWE-%'}。
 * 覆盖 Semgrep {@code security.*} 规则集与 SAST 通用 {@code CWE-***} 编码；
 * 排除 {@code FALSE_POSITIVE}（与 R17.6 一致）。
 *
 * <p>Covers: R14.1, R17.6。
 */
@Component
public class SecurityIssueCountCollector implements MetricCollector {

    public static final String METRIC = "security_issue_count";

    private final GateMetricMapper mapper;

    public SecurityIssueCountCollector(GateMetricMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String metric() {
        return METRIC;
    }

    @Override
    public BigDecimal collect(MetricContext ctx) {
        long n = mapper.countSecurity(ctx.taskId());
        return BigDecimal.valueOf(n);
    }
}
