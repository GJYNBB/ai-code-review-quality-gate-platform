package com.acrqg.platform.gate.collector;

import com.acrqg.platform.gate.repository.GateMetricMapper;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * {@code ai_risk_score} 指标采集（R12.6）。
 *
 * <p>从 {@code review_task} 表读取任务级 AI 风险分：
 * <pre>
 * SELECT ai_risk_score, ai_available
 *   FROM review_task
 *  WHERE id = ?;
 * </pre>
 *
 * <p>降级语义（R12.5）：当 {@code ai_available=false} 时，AI 阶段未生成有效输出，
 * 直接返回 {@link BigDecimal#ZERO}——这样配置 {@code ai_risk_score<=80 (WARN)}
 * 之类的规则仍能正常通过，避免因 AI 不可用而连锁阻塞门禁。{@code ai_risk_score}
 * 列为 {@code NULL} 时同样按 0 处理（任务尚未走过 AI 阶段，例如纯静态扫描场景）。
 *
 * <p>Covers: R12.6, R12.5。
 */
@Component
public class AiRiskScoreCollector implements MetricCollector {

    public static final String METRIC = "ai_risk_score";

    private final GateMetricMapper mapper;

    public AiRiskScoreCollector(GateMetricMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String metric() {
        return METRIC;
    }

    @Override
    public BigDecimal collect(MetricContext ctx) {
        GateMetricMapper.AiRiskRow row = mapper.loadAiRisk(ctx.taskId());
        if (row == null) {
            return BigDecimal.ZERO;
        }
        if (row.aiAvailable() != null && !row.aiAvailable()) {
            return BigDecimal.ZERO;
        }
        Integer s = row.aiRiskScore();
        return s == null ? BigDecimal.ZERO : BigDecimal.valueOf(s);
    }
}
