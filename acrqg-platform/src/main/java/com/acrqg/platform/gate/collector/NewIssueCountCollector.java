package com.acrqg.platform.gate.collector;

import com.acrqg.platform.gate.repository.GateMetricMapper;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * {@code new_issue_count} 指标采集（R14.1）。
 *
 * <p>统计本次评审新增的"NEW"状态问题数（任意 severity / source）：
 * <pre>
 * SELECT COUNT(*) FROM code_issue
 *  WHERE task_id = ? AND status = 'NEW';
 * </pre>
 *
 * <p>不再额外排除 FALSE_POSITIVE：FALSE_POSITIVE 经状态改写后已不在 NEW 集合中。
 *
 * <p>Covers: R14.1。
 */
@Component
public class NewIssueCountCollector implements MetricCollector {

    public static final String METRIC = "new_issue_count";

    private final GateMetricMapper mapper;

    public NewIssueCountCollector(GateMetricMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String metric() {
        return METRIC;
    }

    @Override
    public BigDecimal collect(MetricContext ctx) {
        long n = mapper.countNew(ctx.taskId());
        return BigDecimal.valueOf(n);
    }
}
