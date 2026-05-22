package com.acrqg.platform.gate.collector;

import com.acrqg.platform.gate.repository.GateMetricMapper;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * critical_issue_count metric collector (design.md section 10.2 / R14.1 / R17.6).
 *
 * <p>SQL (matches design section 10.2):
 * <pre>
 * SELECT COUNT(*) FROM code_issue
 *  WHERE task_id = ?
 *    AND severity IN ('CRITICAL','HIGH')
 *    AND status &lt;&gt; 'FALSE_POSITIVE';
 * </pre>
 *
 * <p>Covers: R14.1, R17.6.
 */
@Component
public class CriticalIssueCountCollector implements MetricCollector {

    public static final String METRIC = "critical_issue_count";

    private final GateMetricMapper mapper;

    public CriticalIssueCountCollector(GateMetricMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String metric() {
        return METRIC;
    }

    @Override
    public BigDecimal collect(MetricContext ctx) {
        long n = mapper.countCritical(ctx.taskId());
        return BigDecimal.valueOf(n);
    }
}

