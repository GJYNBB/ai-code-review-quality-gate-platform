package com.acrqg.platform.dashboard.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 项目质量看板 Mapper（B4-C / R18）。
 *
 * <p>本接口承担两类聚合查询：
 * <ol>
 *   <li>{@link #aggregateTrend} —— 按 UTC 日 GROUP BY {@code review_task}，
 *       支撑 {@code QualityTrendDTO}（R18.1）；</li>
 *   <li>{@link #topRiskFiles}   —— 按 {@code file_path} GROUP BY {@code code_issue}，
 *       支撑 {@code RiskFileDTO}（R18.3）。</li>
 * </ol>
 *
 * <p>设计要点：
 * <ul>
 *   <li>聚合 SQL 都返回 {@code Map<String,Object>}，由 Service 层显式转 DTO。
 *       这样可以避免在 DTO 上挂载 MyBatis 注解；DTO 保持纯数据语义。</li>
 *   <li>所有日期参数采用 {@link OffsetDateTime}（UTC），上层把 {@link LocalDate}
 *       的开始 / 结束转换成 {@code [start UTC 00:00, endExclusive UTC 00:00)}
 *       的半开区间——避免在 SQL 中做时区转换。</li>
 *   <li>{@code branch} / {@code projectId} 等过滤条件采用 {@code <if>} 动态 SQL；
 *       {@code branch} 为 {@code null} 或空串时不过滤（R18.1 中 branch 可选）。</li>
 *   <li>权重值在 SQL 中硬编码：CRITICAL=10, HIGH=5, MEDIUM=2, LOW=1, INFO=0.5；
 *       与 {@link com.acrqg.platform.dashboard.dto.RiskFileDTO} 保持一致。
 *       使用 PostgreSQL 的 {@code CASE WHEN ... ELSE} 表达式（默认推断为 numeric，
 *       保留 0.5 精度）。</li>
 * </ul>
 *
 * <p>Covers: R18.1, R18.3。
 */
@Mapper
public interface DashboardMapper {

    /**
     * 按 UTC 日聚合 {@code review_task}。
     *
     * <p>查询：
     * <pre>
     * SELECT (created_at AT TIME ZONE 'UTC')::date AS day,
     *        COUNT(*)                              AS task_count,
     *        SUM(CASE WHEN status='PASSED'      THEN 1 ELSE 0 END) AS pass_count,
     *        SUM(CASE WHEN status='FAILED_GATE' THEN 1 ELSE 0 END) AS fail_count,
     *        AVG(score)                            AS avg_score,
     *        AVG(EXTRACT(EPOCH FROM (finished_at - created_at))) AS avg_duration_seconds
     *   FROM review_task
     *  WHERE project_id = #{projectId}
     *    AND created_at &gt;= #{startInclusive}
     *    AND created_at &lt;  #{endExclusive}
     *    [AND source_branch = #{branch}]
     *  GROUP BY 1
     *  ORDER BY 1
     * </pre>
     *
     * <p>返回的每个 Map 包含键：
     * <ul>
     *   <li>{@code day} —— {@link java.sql.Date} / {@link LocalDate}（按 JDBC 驱动差异），
     *       Service 层统一转换；</li>
     *   <li>{@code task_count} / {@code pass_count} / {@code fail_count} —— {@link Long}；</li>
     *   <li>{@code avg_score} / {@code avg_duration_seconds} —— {@link java.math.BigDecimal} 或 {@code null}。</li>
     * </ul>
     *
     * @param projectId      项目主键
     * @param startInclusive 起始时间（含），UTC
     * @param endExclusive   截止时间（不含），UTC；通常为 {@code endDate.plusDays(1)} 当天 00:00
     * @param branch         可选源分支过滤；{@code null} 或空串 = 不过滤
     * @return 按日升序的聚合行（缺失日由 Service 层补 0）
     */
    @Select("""
            <script>
            SELECT (created_at AT TIME ZONE 'UTC')::date AS day,
                   COUNT(*)                              AS task_count,
                   SUM(CASE WHEN status='PASSED'      THEN 1 ELSE 0 END) AS pass_count,
                   SUM(CASE WHEN status='FAILED_GATE' THEN 1 ELSE 0 END) AS fail_count,
                   AVG(score)                            AS avg_score,
                   AVG(EXTRACT(EPOCH FROM (finished_at - created_at))) AS avg_duration_seconds
              FROM review_task
             WHERE project_id = #{projectId}
               AND created_at &gt;= #{startInclusive}
               AND created_at &lt;  #{endExclusive}
            <if test='branch != null and branch != ""'>
               AND source_branch = #{branch}
            </if>
             GROUP BY 1
             ORDER BY 1
            </script>
            """)
    List<Map<String, Object>> aggregateTrend(@Param("projectId") Long projectId,
                                              @Param("startInclusive") OffsetDateTime startInclusive,
                                              @Param("endExclusive") OffsetDateTime endExclusive,
                                              @Param("branch") String branch);

    /**
     * 按文件路径聚合 {@code code_issue}，按加权得分降序取前 N 条（R18.3）。
     *
     * <p>查询：
     * <pre>
     * SELECT ci.file_path                                                            AS file_path,
     *        COUNT(*)                                                                AS issue_count,
     *        SUM(CASE ci.severity
     *              WHEN 'CRITICAL' THEN 10
     *              WHEN 'HIGH'     THEN 5
     *              WHEN 'MEDIUM'   THEN 2
     *              WHEN 'LOW'      THEN 1
     *              ELSE 0.5 END)                                                     AS weighted_score,
     *        SUM(CASE WHEN ci.severity='CRITICAL' THEN 1 ELSE 0 END)                 AS critical_count,
     *        SUM(CASE WHEN ci.severity='HIGH'     THEN 1 ELSE 0 END)                 AS high_count
     *   FROM code_issue ci
     *   JOIN review_task rt ON ci.task_id = rt.id
     *  WHERE rt.project_id = #{projectId}
     *    AND ci.status &lt;&gt; 'FALSE_POSITIVE'
     *  GROUP BY ci.file_path
     *  ORDER BY weighted_score DESC, issue_count DESC, ci.file_path ASC
     *  LIMIT #{limit}
     * </pre>
     *
     * <p>排序在分数相同时引入 {@code issue_count DESC} + {@code file_path ASC}
     * 兜底，确保结果稳定，便于前端缓存与单测断言。
     *
     * @param projectId 项目主键
     * @param limit     返回上限（典型 10，由 Service 层强制 1..100）
     * @return 风险文件聚合行
     */
    @Select("""
            SELECT ci.file_path                                                AS file_path,
                   COUNT(*)                                                    AS issue_count,
                   SUM(CASE ci.severity
                         WHEN 'CRITICAL' THEN 10
                         WHEN 'HIGH'     THEN 5
                         WHEN 'MEDIUM'   THEN 2
                         WHEN 'LOW'      THEN 1
                         ELSE 0.5 END)                                         AS weighted_score,
                   SUM(CASE WHEN ci.severity = 'CRITICAL' THEN 1 ELSE 0 END)   AS critical_count,
                   SUM(CASE WHEN ci.severity = 'HIGH'     THEN 1 ELSE 0 END)   AS high_count
              FROM code_issue ci
              JOIN review_task rt ON ci.task_id = rt.id
             WHERE rt.project_id = #{projectId}
               AND ci.status <> 'FALSE_POSITIVE'
             GROUP BY ci.file_path
             ORDER BY weighted_score DESC, issue_count DESC, ci.file_path ASC
             LIMIT #{limit}
            """)
    List<Map<String, Object>> topRiskFiles(@Param("projectId") Long projectId,
                                            @Param("limit") int limit);
}
