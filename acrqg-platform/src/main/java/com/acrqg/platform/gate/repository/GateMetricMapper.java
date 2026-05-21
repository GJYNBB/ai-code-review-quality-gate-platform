package com.acrqg.platform.gate.repository;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 门禁判定专用聚合查询 Mapper（B3-F.3）。
 *
 * <p>本接口集中收敛 GateRuleEngine 在评估阶段需要的 6 个 metric SQL；与
 * {@code CodeIssueMapper.countByTaskWithFilter} 不同，本类的查询字面量与
 * design.md §10.2 一一对应，便于独立审阅 / 演进，同时避免 collector 互相
 * 引用 SQL Provider 的胶水代码。
 *
 * <p>由 MyBatis 自动通过 {@code @MapperScan("com.acrqg.platform.**.repository")}
 * 识别并装配到 {@code SqlSession} 中。
 *
 * <p>Covers: R14.1, R12.6, R17.6。
 */
public interface GateMetricMapper {

    /**
     * {@code critical_issue_count}（design §10.2 / R14.1 / R17.6）。
     *
     * <pre>
     * SELECT COUNT(*) FROM code_issue
     *  WHERE task_id = ?
     *    AND severity IN ('CRITICAL','HIGH')
     *    AND status &lt;&gt; 'FALSE_POSITIVE';
     * </pre>
     */
    @Select("""
            SELECT COUNT(*) FROM code_issue
             WHERE task_id = #{taskId}
               AND severity IN ('CRITICAL','HIGH')
               AND status <> 'FALSE_POSITIVE'
            """)
    long countCritical(@Param("taskId") long taskId);

    /**
     * {@code security_issue_count}：安全规则前缀匹配。
     *
     * <p>识别策略：{@code rule_code LIKE 'security.%' OR rule_code LIKE 'CWE-%'}。
     * 与 design §10.2 / 任务交付清单一致，覆盖 Semgrep {@code security.*} 规则集
     * 与 SAST 通用 {@code CWE-***} 编码。仍排除 {@code FALSE_POSITIVE}。
     */
    @Select("""
            SELECT COUNT(*) FROM code_issue
             WHERE task_id = #{taskId}
               AND status <> 'FALSE_POSITIVE'
               AND (rule_code LIKE 'security.%' OR rule_code LIKE 'CWE-%')
            """)
    long countSecurity(@Param("taskId") long taskId);

    /**
     * {@code new_issue_count}：本次评审产生的"NEW"状态问题数（任意 severity / source）。
     *
     * <p>不排除 FALSE_POSITIVE：FALSE_POSITIVE 经由 status 改写后已不在 NEW 集合中。
     */
    @Select("""
            SELECT COUNT(*) FROM code_issue
             WHERE task_id = #{taskId}
               AND status = 'NEW'
            """)
    long countNew(@Param("taskId") long taskId);

    /** 任务级 ai_risk_score 行投影（仅取必要列）。 */
    record AiRiskRow(Integer aiRiskScore, Boolean aiAvailable) {
    }

    /**
     * 读取 {@code review_task.ai_risk_score} / {@code ai_available}（R12.6）。
     *
     * @param taskId 任务主键
     * @return 命中返回投影；不存在返回 {@code null}
     */
    @Select("""
            SELECT ai_risk_score AS aiRiskScore,
                   ai_available  AS aiAvailable
              FROM review_task
             WHERE id = #{taskId}
            """)
    AiRiskRow loadAiRisk(@Param("taskId") long taskId);
}
