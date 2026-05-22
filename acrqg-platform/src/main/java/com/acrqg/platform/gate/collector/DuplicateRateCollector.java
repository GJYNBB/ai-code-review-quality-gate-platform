package com.acrqg.platform.gate.collector;

import com.acrqg.platform.gate.collector.report.CpdXmlParser;
import com.acrqg.platform.gate.collector.report.QualityReportLocator;
import com.acrqg.platform.gate.collector.report.ReportNotFoundException;
import com.acrqg.platform.gate.collector.report.ReportParseException;
import com.acrqg.platform.task.log.TaskLogger;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * {@code duplicate_rate} 指标采集（M11 跟进项接入实现）。
 *
 * <p>读取 Worker / CI 在 {@code GATE_EVALUATING} 阶段前写入的 PMD-CPD XML 报告：
 * 默认路径 {@code reports/cpd/task-{taskId}/cpd.xml} + 同目录 {@code total-loc.txt}；
 * 基目录可由 {@code system_param.gate.duplicate_rate.report.dir} 热更新。
 *
 * <p>退化路径（保持 R14 整体可用性）：
 * <ul>
 *   <li>报告文件不存在或 {@code total-loc.txt} 缺失 → 返回
 *       {@code system_param.gate.duplicate_rate.report.placeholder}（默认 0），
 *       写一条 INFO 级 task_log 提示；</li>
 *   <li>报告解析失败 → 同样返回 placeholder，但写一条 WARN 级 task_log。</li>
 * </ul>
 *
 * <p>计算口径（与 SonarQube 对齐）：
 * {@code duplicate_rate% = Σ (duplication.lines × duplication.files.count) × 100 / total-loc}，
 * 输出范围 [0,100]。
 *
 * <p>Covers: R14.1, R21.4, R23.4。
 */
@Component
public class DuplicateRateCollector implements MetricCollector {

    public static final String METRIC = "duplicate_rate";

    /** {@code system_param} 中保存 CPD XML 报告基目录的 key。 */
    static final String PARAM_REPORT_DIR = "gate.duplicate_rate.report.dir";
    /** 数据源缺失时使用的默认基目录。 */
    static final String DEFAULT_REPORT_DIR = "reports/cpd";
    /** 任务子目录下的 XML 文件名。 */
    static final String XML_FILENAME = "cpd.xml";

    /** {@code system_param} 中保存 placeholder 百分比的 key。 */
    static final String PARAM_PLACEHOLDER = "gate.duplicate_rate.report.placeholder";
    /** placeholder 兜底默认值。 */
    static final BigDecimal DEFAULT_PLACEHOLDER = BigDecimal.ZERO;

    private final TaskLogger taskLogger;
    private final QualityReportLocator locator;
    private final CpdXmlParser parser;

    public DuplicateRateCollector(TaskLogger taskLogger,
                                   QualityReportLocator locator,
                                   CpdXmlParser parser) {
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
        Path xml = locator.resolveReportPath(PARAM_REPORT_DIR, DEFAULT_REPORT_DIR,
                ctx.taskId(), XML_FILENAME);
        try {
            BigDecimal value = parser.parseDuplicateRatePercent(xml);
            taskLogger.info(ctx.taskId(), "GATE_EVALUATING",
                    "duplicate_rate from cpd.xml: " + value + "% (path=" + xml + ")");
            return value;
        } catch (ReportNotFoundException e) {
            BigDecimal placeholder = locator.readPlaceholder(PARAM_PLACEHOLDER, DEFAULT_PLACEHOLDER);
            taskLogger.info(ctx.taskId(), "GATE_EVALUATING",
                    "duplicate_rate report not found (" + e.getMessage()
                            + "), fallback to placeholder=" + placeholder);
            return placeholder;
        } catch (ReportParseException e) {
            BigDecimal placeholder = locator.readPlaceholder(PARAM_PLACEHOLDER, DEFAULT_PLACEHOLDER);
            taskLogger.warn(ctx.taskId(), "GATE_EVALUATING",
                    "duplicate_rate parse failed: " + e.getMessage()
                            + ", fallback to placeholder=" + placeholder);
            return placeholder;
        } catch (RuntimeException e) {
            BigDecimal placeholder = locator.readPlaceholder(PARAM_PLACEHOLDER, DEFAULT_PLACEHOLDER);
            taskLogger.warn(ctx.taskId(), "GATE_EVALUATING",
                    "duplicate_rate unexpected error: " + e.getMessage()
                            + ", fallback to placeholder=" + placeholder);
            return placeholder;
        }
    }
}
