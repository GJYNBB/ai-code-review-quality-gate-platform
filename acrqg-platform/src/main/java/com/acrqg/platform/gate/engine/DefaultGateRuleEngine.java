package com.acrqg.platform.gate.engine;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.common.util.JsonUtils;
import com.acrqg.platform.gate.collector.MetricCollectorRegistry;
import com.acrqg.platform.gate.collector.MetricContext;
import com.acrqg.platform.gate.domain.GateResult;
import com.acrqg.platform.gate.domain.GateResultStatus;
import com.acrqg.platform.gate.domain.GateRule;
import com.acrqg.platform.gate.domain.QualityGate;
import com.acrqg.platform.gate.domain.RuleEval;
import com.acrqg.platform.gate.dto.GateResultDTO;
import com.acrqg.platform.gate.dto.GateResultSummary;
import com.acrqg.platform.gate.dto.RuleEvalDTO;
import com.acrqg.platform.gate.repository.GateResultMapper;
import com.acrqg.platform.gate.repository.GateRuleMapper;
import com.acrqg.platform.gate.repository.QualityGateMapper;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.log.TaskLogger;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link GateRuleEngine} 默认实现（B3-F.4）。
 *
 * <p>评分公式（按本任务交付清单）：
 * <pre>
 * score = clamp( 100 - sum(weight_i * penalty_i ), 0, 100 )
 *
 * penalty_i:
 *   - BLOCKER 失败 → 20
 *   - WARN    失败 → 5
 *   - 通过         → 0
 *
 * weight_i 默认 1.0（暂未读取 system_param 覆盖；后续 M11 引入）
 * </pre>
 *
 * <p>状态聚合：任一 BLOCKER 规则失败 → {@link GateResultStatus#FAILED}；否则
 * {@link GateResultStatus#PASSED}（含 WARN 失败的情况）；无任何 enabled 规则时
 * 同样按 PASSED + score=100，并写一条 WARN 级 task_log（提示项目尚未配置门禁）。
 *
 * <p>持久化：{@link GateResultMapper#upsertByTaskId} 使用 PostgreSQL ON CONFLICT
 * 兼容 retry 场景；写完后再同步 {@code review_task.ai_risk_score / ai_available}。
 *
 * <p>Covers: R13.6, R14.1, R14.2, R14.3, R14.4, R14.5, R14.8, R12.6, R17.6。
 */
@Service
public class DefaultGateRuleEngine implements GateRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultGateRuleEngine.class);

    /** task_log.stage 字面量。 */
    static final String STAGE = "GATE_EVALUATING";

    /** BLOCKER 失败惩罚分。 */
    static final int PENALTY_BLOCKER = 20;
    /** WARN 失败惩罚分。 */
    static final int PENALTY_WARN = 5;

    /** 评分上下限。 */
    static final int SCORE_MIN = 0;
    static final int SCORE_MAX = 100;

    /** 默认权重（per-rule）。 */
    static final BigDecimal DEFAULT_WEIGHT = BigDecimal.ONE;

    /** severity 字面量（DB CHECK 约束）。 */
    static final String SEVERITY_BLOCKER = "BLOCKER";
    static final String SEVERITY_WARN = "WARN";

    private final ReviewTaskMapper reviewTaskMapper;
    private final QualityGateMapper qualityGateMapper;
    private final GateRuleMapper gateRuleMapper;
    private final GateResultMapper gateResultMapper;
    private final MetricCollectorRegistry collectorRegistry;
    private final OperatorEvaluator operatorEvaluator;
    private final TaskLogger taskLogger;

    public DefaultGateRuleEngine(ReviewTaskMapper reviewTaskMapper,
                                 QualityGateMapper qualityGateMapper,
                                 GateRuleMapper gateRuleMapper,
                                 GateResultMapper gateResultMapper,
                                 MetricCollectorRegistry collectorRegistry,
                                 OperatorEvaluator operatorEvaluator,
                                 TaskLogger taskLogger) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.qualityGateMapper = qualityGateMapper;
        this.gateRuleMapper = gateRuleMapper;
        this.gateResultMapper = gateResultMapper;
        this.collectorRegistry = collectorRegistry;
        this.operatorEvaluator = operatorEvaluator;
        this.taskLogger = taskLogger;
    }

    @Override
    @Transactional
    public GateResultDTO evaluate(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "taskId 不能为空");
        }
        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND,
                    "task not found: " + taskId);
        }
        Long projectId = task.getProjectId();
        boolean aiAvailable = task.getAiAvailable() == null || task.getAiAvailable();
        Integer aiRiskScore = task.getAiRiskScore();

        MetricContext ctx = new MetricContext(taskId, projectId);

        // 1) 取项目 enabled 的门禁版本 + 启用的规则
        QualityGate gate = qualityGateMapper.findEnabledByProject(projectId);
        List<GateRule> enabledRules = loadEnabledRules(gate);

        // 2) 遍历每条 enabled 规则：采集 + 比较
        List<RuleEval> evals = new ArrayList<>(enabledRules.size());
        Map<String, BigDecimal> metricValues = new LinkedHashMap<>();
        for (GateRule rule : enabledRules) {
            BigDecimal actual;
            BigDecimal threshold;
            try {
                actual = collectorRegistry.collect(rule.getMetric(), ctx);
            } catch (BusinessException ex) {
                // 未知 metric：写 WARN 并把该规则标记为失败（severity 维持原值）
                taskLogger.warn(taskId, STAGE,
                        "skip rule due to unknown metric=" + rule.getMetric(), ex);
                evals.add(new RuleEval(
                        rule.getMetric(),
                        rule.getOperator(),
                        rule.getThreshold(),
                        normaliseSeverity(rule.getSeverity()),
                        BigDecimal.ZERO,
                        false));
                continue;
            }
            try {
                threshold = parseThreshold(rule.getThreshold());
            } catch (BusinessException ex) {
                taskLogger.warn(taskId, STAGE,
                        "skip rule due to invalid threshold=" + rule.getThreshold(), ex);
                evals.add(new RuleEval(
                        rule.getMetric(),
                        rule.getOperator(),
                        rule.getThreshold(),
                        normaliseSeverity(rule.getSeverity()),
                        actual,
                        false));
                metricValues.put(rule.getMetric(), actual);
                continue;
            }

            boolean passed;
            try {
                passed = operatorEvaluator.evaluate(actual, rule.getOperator(), threshold);
            } catch (BusinessException ex) {
                // 非法 operator：标记为失败但不阻塞其他规则
                taskLogger.warn(taskId, STAGE,
                        "skip rule due to invalid operator=" + rule.getOperator(), ex);
                evals.add(new RuleEval(
                        rule.getMetric(),
                        rule.getOperator(),
                        rule.getThreshold(),
                        normaliseSeverity(rule.getSeverity()),
                        actual,
                        false));
                metricValues.put(rule.getMetric(), actual);
                continue;
            }
            evals.add(new RuleEval(
                    rule.getMetric(),
                    rule.getOperator(),
                    rule.getThreshold(),
                    normaliseSeverity(rule.getSeverity()),
                    actual,
                    passed));
            metricValues.put(rule.getMetric(), actual);
        }

        // 3) 状态聚合
        GateResultStatus status = aggregateStatus(evals);
        if (enabledRules.isEmpty()) {
            // 无规则：警告并按 PASSED + score=100 处理
            taskLogger.warn(taskId, STAGE,
                    "no enabled gate rules for project " + projectId
                            + "; default to PASSED with score=100");
        }

        // 4) score
        int score = computeScore(evals);

        // 5) summary 序列化
        GateResultSummary summary = buildSummary(evals, metricValues, aiAvailable);
        String summaryJson = JsonUtils.toJson(summary);

        // 6) upsert gate_result
        gateResultMapper.upsertByTaskId(
                taskId,
                status.name(),
                score,
                aiAvailable ? aiRiskScore : null,
                aiAvailable,
                summaryJson);

        // 7) 同步 review_task 的 AI 字段（按交付清单要求）
        try {
            reviewTaskMapper.updateAiResult(
                    taskId,
                    aiAvailable && aiRiskScore != null ? aiRiskScore : null,
                    aiAvailable);
        } catch (RuntimeException ex) {
            // 该写入失败不应阻塞门禁判定的提交（gate_result 已写完）
            log.warn("updateAiResult failed during gate evaluation: taskId={} err={}",
                    taskId, ex.toString(), ex);
        }

        // 8) 信息级流水
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("status", status.name());
        detail.put("score", score);
        detail.put("ruleCount", evals.size());
        detail.put("failedCount", countFailed(evals));
        detail.put("aiAvailable", aiAvailable);
        detail.put("gateId", gate == null ? null : gate.getId());
        taskLogger.info(taskId, STAGE,
                "gate evaluation completed: status=" + status + ", score=" + score, detail);

        // 9) 重新加载并返回 DTO（保证 createdAt / updatedAt 与 DB 一致）
        GateResult persisted = gateResultMapper.findByTaskId(taskId);
        return toDTO(persisted, summary);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /** 取 enabled 门禁版本下 enabled=true 的规则；任一为空时返回空 list。 */
    List<GateRule> loadEnabledRules(QualityGate gate) {
        if (gate == null) {
            return Collections.emptyList();
        }
        List<GateRule> all = gateRuleMapper.listByGate(gate.getId());
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        List<GateRule> enabled = new ArrayList<>(all.size());
        for (GateRule r : all) {
            if (Boolean.TRUE.equals(r.getEnabled())) {
                enabled.add(r);
            }
        }
        return enabled;
    }

    /**
     * 状态聚合：任一 BLOCKER 失败 → FAILED；否则 PASSED。
     */
    static GateResultStatus aggregateStatus(List<RuleEval> evals) {
        for (RuleEval e : evals) {
            if (!e.passed() && SEVERITY_BLOCKER.equals(e.severity())) {
                return GateResultStatus.FAILED;
            }
        }
        return GateResultStatus.PASSED;
    }

    /**
     * 评分公式：{@code clamp(100 - sum(weight × penalty), 0, 100)}。
     */
    static int computeScore(List<RuleEval> evals) {
        BigDecimal penaltySum = BigDecimal.ZERO;
        for (RuleEval e : evals) {
            if (e.passed()) {
                continue;
            }
            int penalty = SEVERITY_BLOCKER.equals(e.severity()) ? PENALTY_BLOCKER : PENALTY_WARN;
            // weight 默认 1.0；若后续接入 system_param 按 metric 覆盖，从此处替换
            BigDecimal weight = DEFAULT_WEIGHT;
            penaltySum = penaltySum.add(weight.multiply(BigDecimal.valueOf(penalty)));
        }
        BigDecimal score = BigDecimal.valueOf(SCORE_MAX)
                .subtract(penaltySum)
                .setScale(0, RoundingMode.HALF_UP);
        if (score.compareTo(BigDecimal.valueOf(SCORE_MIN)) < 0) {
            return SCORE_MIN;
        }
        if (score.compareTo(BigDecimal.valueOf(SCORE_MAX)) > 0) {
            return SCORE_MAX;
        }
        return score.intValue();
    }

    static GateResultSummary buildSummary(List<RuleEval> evals,
                                           Map<String, BigDecimal> metricValues,
                                           boolean aiAvailable) {
        List<RuleEvalDTO> failed = new ArrayList<>();
        List<RuleEvalDTO> passed = new ArrayList<>();
        for (RuleEval e : evals) {
            RuleEvalDTO dto = new RuleEvalDTO(
                    e.metric(),
                    e.operator(),
                    e.threshold(),
                    e.severity(),
                    e.actual(),
                    e.passed());
            if (e.passed()) {
                passed.add(dto);
            } else {
                failed.add(dto);
            }
        }
        return new GateResultSummary(failed, passed, metricValues, aiAvailable);
    }

    static int countFailed(List<RuleEval> evals) {
        int n = 0;
        for (RuleEval e : evals) {
            if (!e.passed()) {
                n++;
            }
        }
        return n;
    }

    static String normaliseSeverity(String s) {
        return SEVERITY_BLOCKER.equals(s) ? SEVERITY_BLOCKER : SEVERITY_WARN;
    }

    /** 解析规则阈值字符串为 {@link BigDecimal}；非法时抛 {@link BusinessException}。 */
    static BigDecimal parseThreshold(String threshold) {
        if (threshold == null || threshold.isBlank()) {
            throw new BusinessException(ErrorCode.GATE_RULE_INVALID,
                    "threshold is blank");
        }
        try {
            return new BigDecimal(threshold.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.GATE_RULE_INVALID,
                    "invalid threshold: " + threshold, ex);
        }
    }

    /** GateResult 行 → GateResultDTO（summary 已传入避免再次反序列化）。 */
    static GateResultDTO toDTO(GateResult row, GateResultSummary summaryFallback) {
        if (row == null) {
            return null;
        }
        GateResultSummary summary = summaryFallback;
        if (summary == null && row.getSummary() != null) {
            try {
                summary = JsonUtils.fromJson(row.getSummary(),
                        new TypeReference<GateResultSummary>() {});
            } catch (RuntimeException ex) {
                log.warn("toDTO: parse summary failed, fallback to empty: id={} err={}",
                        row.getId(), ex.toString());
                summary = GateResultSummary.empty(
                        row.getAiAvailable() == null || row.getAiAvailable());
            }
        }
        return new GateResultDTO(
                row.getId(),
                row.getTaskId(),
                row.getStatus(),
                row.getScore(),
                row.getAiRiskScore(),
                row.getAiAvailable() == null || row.getAiAvailable(),
                summary,
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
