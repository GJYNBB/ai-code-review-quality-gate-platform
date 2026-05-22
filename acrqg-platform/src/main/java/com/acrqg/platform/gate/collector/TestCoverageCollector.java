package com.acrqg.platform.gate.collector;

import com.acrqg.platform.gate.collector.report.JacocoCsvParser;
import com.acrqg.platform.gate.collector.report.QualityReportLocator;
import com.acrqg.platform.gate.collector.report.ReportNotFoundException;
import com.acrqg.platform.gate.collector.report.ReportParseException;
import com.acrqg.platform.task.log.TaskLogger;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * {@code test_coverage} 指标采集（M11 跟进项接入实现）。
 *
 * <p>读取 Worker / CI 在 {@code GATE_EVALUATING} 阶段前写入的 JaCoCo CSV 报告：
 * 默认路径 {@code reports/coverage/task-{taskId}/jacoco.csv}；基目录可由
 * {@code system_param.gate.test_coverage.report.dir} 热更新。
 *
 * <p>退化路径（保持 R14 整体可用性，避免单 metric 故障阻塞门禁）：
 * <ul>
 *   <li>报告文件不存在 → 返回 {@code system_param.gate.test_coverage.report.placeholder}
 *       （默认 75，与历史占位一致），写一条 INFO 级 task_log 提示用户接入数据源；</li>
 *   <li>报告解析失败 → 同样返回 placeholder，但写一条 WARN 级 task_log 标注
 *       具体错误，便于运维定位。</li>
 * </ul>
 *
 * <p>计算口径：JaCoCo 默认 CSV 中 LINE_MISSED / LINE_COVERED 全工程汇总，
 * {@code coverage% = LINE_COVERED × 100 / (LINE_COVERED + LINE_MISSED)}，
 * 与 design.md §25.1 全工程语句覆盖率一致。
 *
 * <p>Covers: R14.1, R21.4, R25.1。
 */
@Component
public class TestCoverageCollector implements MetricCollector {

    public static final String METRIC = "test_coverage";

    /** {@code system_param} 中保存 JaCoCo CSV 报告基目录的 key。 */
    static final String PARAM_REPORT_DIR = "gate.test_coverage.report.dir";
    /** 数据源缺失时使用的默认基目录。 */
    static final String DEFAULT_REPORT_DIR = "reports/coverage";
    /** 任务子目录下的 CSV 文件名。 */
    static final String CSV_FILENAME = "jacoco.csv";

    /** {@code system_param} 中保存 placeholder 百分比的 key。 */
    static final String PARAM_PLACEHOLDER = "gate.test_coverage.report.placeholder";
    /** placeholder 兜底默认值。 */
    static final BigDecimal DEFAULT_PLACEHOLDER = BigDecimal.valueOf(75);

    private final TaskLogger taskLogger;
    private final QualityReportLocator locator;
    private final JacocoCsvParser parser;

    public TestCoverageCollector(TaskLogger taskLogger,
                                  QualityReportLocator locator,
                                  JacocoCsvParser parser) {
        this.taskLogger = taskLogger;
        this.locator = locator;
        this.parser = parser;
    }

    @Override
    public String metric() {
        return METRIC;
    }

    @Override
    public BigDecimal collect(MetricContext ctx) {
        Path csv = locator.resolveReportPath(PARAM_REPORT_DIR, DEFAULT_REPORT_DIR,
                ctx.taskId(), CSV_FILENAME);
        try {
            BigDecimal value = parser.parseLineCoveragePercent(csv);
            taskLogger.info(ctx.taskId(), "GATE_EVALUATING",
                    "test_coverage from jacoco.csv: " + value + "% (path=" + csv + ")");
            return value;
        } catch (ReportNotFoundException e) {
            BigDecimal placeholder = locator.readPlaceholder(PARAM_PLACEHOLDER, DEFAULT_PLACEHOLDER);
            taskLogger.info(ctx.taskId(), "GATE_EVALUATING",
                    "test_coverage report not found at " + csv
                            + ", fallback to placeholder=" + placeholder);
            return placeholder;
        } catch (ReportParseException e) {
            BigDecimal placeholder = locator.readPlaceholder(PARAM_PLACEHOLDER, DEFAULT_PLACEHOLDER);
            taskLogger.warn(ctx.taskId(), "GATE_EVALUATING",
                    "test_coverage parse failed: " + e.getMessage()
                            + ", fallback to placeholder=" + placeholder);
            return placeholder;
        } catch (RuntimeException e) {
            BigDecimal placeholder = locator.readPlaceholder(PARAM_PLACEHOLDER, DEFAULT_PLACEHOLDER);
            taskLogger.warn(ctx.taskId(), "GATE_EVALUATING",
                    "test_coverage unexpected error: " + e.getMessage()
                            + ", fallback to placeholder=" + placeholder);
            return placeholder;
        }
    }
}
