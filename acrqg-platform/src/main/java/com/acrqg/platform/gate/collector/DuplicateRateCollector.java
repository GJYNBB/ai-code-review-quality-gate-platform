package com.acrqg.platform.gate.collector;

import com.acrqg.platform.task.log.TaskLogger;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * {@code duplicate_rate} 指标采集（占位实现，B3-F.3 任务交付清单）。
 *
 * <p>重复率需要 SCC / cpd 等代码相似度工具支撑，目前未接入；按交付清单要求
 * 返回 {@link BigDecimal#ZERO} 并写一条 INFO 级 task_log 提示。当用户配置了
 * {@code duplicate_rate<=5}（百分比）类规则时，本任务返回 0 会让规则总是通过，
 * 这是符合预期的"占位"语义。
 *
 * <p>Covers: R14.1（占位），后续真实接入由 M11 跟进。
 */
@Component
public class DuplicateRateCollector implements MetricCollector {

    public static final String METRIC = "duplicate_rate";

    private final TaskLogger taskLogger;

    public DuplicateRateCollector(TaskLogger taskLogger) {
        this.taskLogger = taskLogger;
    }

    @Override
    public String metric() {
        return METRIC;
    }

    @Override
    public BigDecimal collect(MetricContext ctx) {
        taskLogger.info(ctx.taskId(), "GATE_EVALUATING",
                "duplicate_rate not implemented yet (returns 0 as placeholder)");
        return BigDecimal.ZERO;
    }
}
