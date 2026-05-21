package com.acrqg.platform.gate.collector;

import com.acrqg.platform.task.log.TaskLogger;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * {@code test_coverage} 指标采集（占位实现，B3-F.3 任务交付清单）。
 *
 * <p>当前未接入任何覆盖率数据源（Jacoco / Cobertura / lcov 等），按交付清单要求
 * 返回固定占位值 {@code 75}，并写一条 INFO 级 task_log 提示后续接入。这样保证
 * 即便用户配置了 {@code test_coverage>=70} 的门禁规则，本任务也能产出可解释的
 * 判定结果而非 NPE。后续接入真实数据源时只需替换 {@link #collect(MetricContext)}
 * 实现，规则配置无需调整。
 *
 * <p>Covers: R14.1（占位），后续真实接入由 M11 跟进。
 */
@Component
public class TestCoverageCollector implements MetricCollector {

    public static final String METRIC = "test_coverage";

    /** 占位返回值。design.md §13.5 默认模板使用 {@code test_coverage>=70 (BLOCKER)}，
     *  返回 75 时模板规则可通过，便于演示场景。 */
    static final BigDecimal PLACEHOLDER_VALUE = BigDecimal.valueOf(75);

    private final TaskLogger taskLogger;

    public TestCoverageCollector(TaskLogger taskLogger) {
        this.taskLogger = taskLogger;
    }

    @Override
    public String metric() {
        return METRIC;
    }

    @Override
    public BigDecimal collect(MetricContext ctx) {
        taskLogger.info(ctx.taskId(), "GATE_EVALUATING",
                "test_coverage placeholder (returns " + PLACEHOLDER_VALUE
                        + "; real coverage source not implemented yet)");
        return PLACEHOLDER_VALUE;
    }
}
