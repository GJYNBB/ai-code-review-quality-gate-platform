package com.acrqg.platform.gate.service.impl;

import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.api.FieldError;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.gate.domain.GateRule;
import com.acrqg.platform.gate.domain.QualityGate;
import com.acrqg.platform.gate.dto.GateRuleDTO;
import com.acrqg.platform.gate.dto.QualityGateDTO;
import com.acrqg.platform.gate.dto.QualityGateSaveRequest;
import com.acrqg.platform.gate.repository.GateRuleMapper;
import com.acrqg.platform.gate.repository.QualityGateMapper;
import com.acrqg.platform.gate.service.QualityGateService;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.acrqg.platform.project.repository.ProjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link QualityGateService} 默认实现。
 *
 * <h3>关键策略</h3>
 *
 * <ol>
 *   <li><b>三步事务保存</b>：{@link #save} 标记为 {@code @Transactional}：
 *       <ol>
 *         <li>校验规则取值集合（metric / operator / severity）；非法时抛
 *             {@code BusinessException(GATE_RULE_INVALID)} 并在 details 中
 *             携带 {@code rules[i].metric} 形式的字段路径（R13.3）；</li>
 *         <li>UPDATE 旧 enabled 版本为 disabled，<b>必须先于</b> INSERT 新版本，
 *             否则部分唯一索引 {@code uk_quality_gate_one_enabled} 会冲突；</li>
 *         <li>INSERT 新版本（version = maxVersion+1, enabled=true），然后批量
 *             INSERT 规则。</li>
 *       </ol>
 *       任意一步异常都会回滚，不会留下"半启用"状态。
 *   </li>
 *
 *   <li><b>事后审计</b>：发布 {@link AuditEvent} 事件
 *       ({@code QUALITY_GATE_SAVED})；订阅方 {@code AuditEventListener}
 *       异步落库（R22.1）。事件 detail 含 {@code projectId} / {@code version} /
 *       {@code ruleCount}。</li>
 *
 *   <li><b>默认模板</b>：{@link #getDefaultTemplate} 在每次调用时构造一个新对象，
 *       不缓存（成本极低；避免和后续可能的"系统级模板表"实现耦合）。模板规则
 *       严格对齐 R13.5：critical_issue_count<=0 BLOCKER、test_coverage>=70 BLOCKER、
 *       ai_risk_score<=80 WARN。</li>
 *
 *   <li><b>批量插入</b>：规则集通常 ≤ 6 条，逐条
 *       {@link GateRuleMapper#insert} 已足够；批量插入需要额外的
 *       {@code <foreach>} XML，对此处场景收益有限。</li>
 * </ol>
 *
 * <p>Covers: R13.1, R13.2, R13.3, R13.4, R13.5, R13.6, R22.1。
 */
@Service
public class QualityGateServiceImpl implements QualityGateService {

    private static final Logger log = LoggerFactory.getLogger(QualityGateServiceImpl.class);

    /** 审计资源类型 / 动作字面量。 */
    private static final String RESOURCE_QUALITY_GATE = "QUALITY_GATE";
    private static final String ACTION_QUALITY_GATE_SAVED = "QUALITY_GATE_SAVED";

    /** 合法 metric 集合（design §7.2 CHECK 约束）。 */
    static final Set<String> ALLOWED_METRICS = Set.of(
            "critical_issue_count",
            "security_issue_count",
            "test_coverage",
            "duplicate_rate",
            "ai_risk_score",
            "new_issue_count");

    /** 合法 operator 集合。 */
    static final Set<String> ALLOWED_OPERATORS = Set.of("<=", ">=", "<", ">", "==", "!=");

    /** 合法 severity 集合。 */
    static final Set<String> ALLOWED_SEVERITIES = Set.of("BLOCKER", "WARN");

    /** 默认模板名称（不会真正落库）。 */
    static final String DEFAULT_TEMPLATE_NAME = "默认模板";

    private final QualityGateMapper qualityGateMapper;
    private final GateRuleMapper gateRuleMapper;
    private final ProjectMapper projectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public QualityGateServiceImpl(QualityGateMapper qualityGateMapper,
                                   GateRuleMapper gateRuleMapper,
                                   ProjectMapper projectMapper,
                                   ApplicationEventPublisher eventPublisher) {
        this.qualityGateMapper = qualityGateMapper;
        this.gateRuleMapper = gateRuleMapper;
        this.projectMapper = projectMapper;
        this.eventPublisher = eventPublisher;
    }

    // ---------------------------------------------------------------------
    // save
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    public QualityGateDTO save(Long projectId, QualityGateSaveRequest request) {
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目不存在");
        }

        // 1) 校验规则取值集合，非法时一次性收集所有违例（R13.3）
        validateRuleEnums(request.rules());

        // 2) 把当前 enabled=true 的版本翻为 disabled（释放部分唯一索引占位）
        int disabled = qualityGateMapper.disableEnabledByProject(projectId);
        if (log.isDebugEnabled()) {
            log.debug("save quality_gate: projectId={} disabled={} previous enabled version(s)",
                    projectId, disabled);
        }

        // 3) 计算下一个 version
        Integer maxVer = qualityGateMapper.maxVersionByProject(projectId);
        int nextVersion = (maxVer == null ? 0 : maxVer) + 1;

        // 4) 插入新版本
        QualityGate gate = new QualityGate();
        gate.setProjectId(projectId);
        gate.setName(request.name());
        gate.setVersion(nextVersion);
        gate.setEnabled(Boolean.TRUE);
        gate.setCreatedBy(caller.id());
        qualityGateMapper.insert(gate);

        // 5) 批量插入规则
        List<GateRuleDTO> requestRules = request.rules();
        for (int i = 0; i < requestRules.size(); i++) {
            GateRuleDTO ruleDTO = requestRules.get(i);
            GateRule rule = new GateRule();
            rule.setGateId(gate.getId());
            rule.setMetric(ruleDTO.metric());
            rule.setOperator(ruleDTO.operator());
            rule.setThreshold(ruleDTO.threshold());
            rule.setSeverity(ruleDTO.severity());
            // null 视为启用，与 DB 列默认值一致
            rule.setEnabled(ruleDTO.enabled() == null ? Boolean.TRUE : ruleDTO.enabled());
            // null 视为按入参顺序的 sort_order
            rule.setSortOrder(i);
            gateRuleMapper.insert(rule);
        }

        // 6) 发布审计事件
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", projectId);
        detail.put("gateId", gate.getId());
        detail.put("version", nextVersion);
        detail.put("ruleCount", requestRules.size());
        eventPublisher.publishEvent(AuditEvent.of(
                caller.id(),
                caller.username(),
                ACTION_QUALITY_GATE_SAVED,
                RESOURCE_QUALITY_GATE,
                String.valueOf(gate.getId()),
                null,
                detail));

        // 7) 二次查询返回，确保 createdAt 等 DB 默认值已回填
        return loadAsDTO(gate.getId());
    }

    // ---------------------------------------------------------------------
    // queries
    // ---------------------------------------------------------------------

    @Override
    public QualityGateDTO getEnabled(Long projectId) {
        QualityGate gate = qualityGateMapper.findEnabledByProject(projectId);
        if (gate == null) {
            return null;
        }
        return toDTO(gate, gateRuleMapper.listByGate(gate.getId()));
    }

    @Override
    public List<QualityGateDTO> listVersions(Long projectId) {
        List<QualityGate> rows = qualityGateMapper.listByProject(projectId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<QualityGateDTO> result = new ArrayList<>(rows.size());
        for (QualityGate g : rows) {
            // listVersions 返回结构按"列表项不携带规则"的策略，避免 N+1；
            // 调用方在前端展开某条版本时再走 getVersion(gateId) 加载规则。
            result.add(toDTO(g, Collections.emptyList()));
        }
        return result;
    }

    @Override
    public QualityGateDTO getVersion(Long gateId) {
        QualityGate gate = qualityGateMapper.selectById(gateId);
        if (gate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "门禁版本不存在");
        }
        return toDTO(gate, gateRuleMapper.listByGate(gateId));
    }

    @Override
    public QualityGateDTO getDefaultTemplate() {
        // R13.5 / design §13.5 三条默认规则
        List<GateRuleDTO> rules = List.of(
                new GateRuleDTO(null, "critical_issue_count", "<=", "0", "BLOCKER", Boolean.TRUE),
                new GateRuleDTO(null, "test_coverage", ">=", "70", "BLOCKER", Boolean.TRUE),
                new GateRuleDTO(null, "ai_risk_score", "<=", "80", "WARN", Boolean.TRUE));
        return new QualityGateDTO(
                null,
                null,
                DEFAULT_TEMPLATE_NAME,
                null,
                false,
                null,
                null,
                rules);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /**
     * 校验规则集合中每条规则的 metric / operator / severity 是否取自合法集合。
     *
     * <p>非法时一次性收集所有违例的字段路径（如 {@code rules[2].operator}），抛出
     * {@code BusinessException(GATE_RULE_INVALID)}，details 数组中携带每条违例
     * 的 {@link FieldError}（R13.3）。
     *
     * @param rules 规则列表（已通过 Bean Validation 的格式校验）
     */
    private static void validateRuleEnums(List<GateRuleDTO> rules) {
        List<FieldError> violations = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            GateRuleDTO r = rules.get(i);
            if (r == null) {
                violations.add(new FieldError("rules[" + i + "]", "规则不能为空"));
                continue;
            }
            if (!ALLOWED_METRICS.contains(r.metric())) {
                violations.add(new FieldError("rules[" + i + "].metric",
                        "metric 取值非法: " + r.metric()));
            }
            if (!ALLOWED_OPERATORS.contains(r.operator())) {
                violations.add(new FieldError("rules[" + i + "].operator",
                        "operator 取值非法: " + r.operator()));
            }
            if (!ALLOWED_SEVERITIES.contains(r.severity())) {
                violations.add(new FieldError("rules[" + i + "].severity",
                        "severity 取值非法: " + r.severity()));
            }
        }
        if (!violations.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.GATE_RULE_INVALID,
                    ErrorCode.GATE_RULE_INVALID.getMessage(),
                    violations);
        }
    }

    /** 主键加载已存在的版本，返回带规则的 DTO（save 后回查使用）。 */
    private QualityGateDTO loadAsDTO(Long gateId) {
        QualityGate gate = qualityGateMapper.selectById(gateId);
        if (gate == null) {
            // 在事务内刚 INSERT 不应当查不到；防御性处理避免 NPE
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "刚保存的门禁版本不可读");
        }
        return toDTO(gate, gateRuleMapper.listByGate(gateId));
    }

    /** 将 DO + 规则列表转为对外 DTO。 */
    private static QualityGateDTO toDTO(QualityGate gate, List<GateRule> rules) {
        List<GateRuleDTO> ruleDTOs;
        if (rules == null || rules.isEmpty()) {
            ruleDTOs = Collections.emptyList();
        } else {
            ruleDTOs = new ArrayList<>(rules.size());
            for (GateRule r : rules) {
                ruleDTOs.add(new GateRuleDTO(
                        r.getId(),
                        r.getMetric(),
                        r.getOperator(),
                        r.getThreshold(),
                        r.getSeverity(),
                        r.getEnabled() == null ? Boolean.TRUE : r.getEnabled()));
            }
        }
        return new QualityGateDTO(
                gate.getId(),
                gate.getProjectId(),
                gate.getName(),
                gate.getVersion(),
                Boolean.TRUE.equals(gate.getEnabled()),
                gate.getCreatedBy(),
                gate.getCreatedAt(),
                ruleDTOs);
    }
}
