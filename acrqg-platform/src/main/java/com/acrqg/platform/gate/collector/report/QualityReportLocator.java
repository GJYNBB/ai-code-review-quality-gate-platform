package com.acrqg.platform.gate.collector.report;

import com.acrqg.platform.admin.domain.SystemParam;
import com.acrqg.platform.admin.repository.SystemParamMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 质量指标报告文件定位器（M11 跟进项）。
 *
 * <p>{@link com.acrqg.platform.gate.collector.TestCoverageCollector} 与
 * {@link com.acrqg.platform.gate.collector.DuplicateRateCollector} 共用：
 * <ul>
 *   <li>从 {@code system_param} 读取报告基目录与兜底占位值；</li>
 *   <li>按约定 {@code {baseDir}/task-{taskId}/{fileName}} 拼接每任务报告路径；</li>
 *   <li>读取异常或文件缺失时由 collector 自身退化为 placeholder + WARN 日志，
 *       本类只承担"配置 + 路径"职责，不读文件内容。</li>
 * </ul>
 *
 * <p>线程安全：本类无可变状态，每次方法调用回库读取最新 system_param，
 * 与 {@link SystemParamMapper} 一致行为，可被 worker 线程池并发调用。
 *
 * <p>Covers: R14.1, R21.4。
 */
@Component
public class QualityReportLocator {

    private static final Logger log = LoggerFactory.getLogger(QualityReportLocator.class);

    /** 任务子目录前缀，与 Worker / CI 写入约定一致。 */
    public static final String TASK_DIR_PREFIX = "task-";

    private final SystemParamMapper systemParamMapper;

    public QualityReportLocator(SystemParamMapper systemParamMapper) {
        this.systemParamMapper = systemParamMapper;
    }

    /**
     * 解析某任务的报告文件绝对/相对路径（不校验存在性）。
     *
     * @param dirParamKey {@code system_param} 中保存基目录的 key
     * @param defaultDir  param 缺失时使用的默认基目录
     * @param taskId      任务主键
     * @param fileName    任务子目录下的文件名（如 {@code jacoco.csv}）
     * @return 拼接后的 {@link Path}（基目录可能是相对路径，由进程 cwd 解释）
     */
    public Path resolveReportPath(String dirParamKey, String defaultDir,
                                   long taskId, String fileName) {
        String baseDir = readStringOrDefault(dirParamKey, defaultDir);
        return Paths.get(baseDir, TASK_DIR_PREFIX + taskId, fileName);
    }

    /**
     * 读取占位 placeholder 百分比（0..100）。读取失败 / 越界时回落到 {@code defaultValue}。
     */
    public BigDecimal readPlaceholder(String paramKey, BigDecimal defaultValue) {
        String raw = readStringOrDefault(paramKey, null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            BigDecimal parsed = new BigDecimal(raw.trim());
            if (parsed.signum() < 0 || parsed.compareTo(BigDecimal.valueOf(100)) > 0) {
                log.warn("placeholder param {}={} out of [0,100], fallback to {}",
                        paramKey, parsed, defaultValue);
                return defaultValue;
            }
            return parsed;
        } catch (NumberFormatException e) {
            log.warn("placeholder param {}={} not a valid decimal, fallback to {}",
                    paramKey, raw, defaultValue);
            return defaultValue;
        }
    }

    private String readStringOrDefault(String paramKey, String defaultValue) {
        if (paramKey == null) {
            return defaultValue;
        }
        try {
            SystemParam sp = systemParamMapper.selectByKey(paramKey);
            if (sp == null || sp.getParamValue() == null || sp.getParamValue().isBlank()) {
                return defaultValue;
            }
            return sp.getParamValue();
        } catch (Exception e) {
            log.warn("read system_param {} failed, fallback to {}", paramKey, defaultValue, e);
            return defaultValue;
        }
    }
}
