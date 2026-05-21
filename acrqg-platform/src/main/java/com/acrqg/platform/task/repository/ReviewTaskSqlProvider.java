package com.acrqg.platform.task.repository;

import java.util.Collection;
import java.util.Iterator;
import org.apache.ibatis.jdbc.SQL;

/**
 * 评审任务的动态 SQL 提供器。
 *
 * <p>承担两类拼接逻辑：
 * <ul>
 *   <li>{@link #findActiveByTripleSql} —— 把可空的 {@code prId} 翻译为
 *       {@code pr_id IS NULL} 或 {@code pr_id = #{prId}}（PostgreSQL 中
 *       {@code pr_id = NULL} 永远为 false，必须用 IS NULL 区分）；</li>
 *   <li>{@link #pageProjectsSql} / {@link #countProjectsSql} —— 把动态长度的
 *       {@code projectIds IN (...)} 与可空过滤条件统一拼接，避免在 Mapper 接口
 *       上写难读的 {@code <foreach>} XML 风格脚本。</li>
 * </ul>
 *
 * <p>SQL 全部使用 {@code #{xxx}} 占位符；{@code projectIds} 集合中的元素以
 * {@code #{projectIds[i]}} 引用，由 MyBatis 走预编译参数绑定，避免 SQL 注入。
 *
 * <p>Covers: R7.4, R8.3, R9.3, R16.5。
 */
public final class ReviewTaskSqlProvider {

    private static final String TASK_COLUMNS =
            "id, task_no, project_id, pr_id, source_branch, target_branch, commit_sha, "
                    + "status, trigger_type, score, ai_risk_score, ai_available, attempt, "
                    + "created_by, started_at, finished_at, created_at, updated_at";

    private ReviewTaskSqlProvider() {
        // utility class
    }

    /**
     * 三元组活跃任务查找 SQL。
     *
     * <p>"活跃"={@code status != 'EXECUTION_FAILED'}。{@code prId} 为 {@code null} /
     * 空字符串时使用 {@code pr_id IS NULL} 匹配；非空时使用 {@code pr_id = #{prId}}。
     */
    public static String findActiveByTripleSql(final Long projectId,
                                               final String prId,
                                               final String commitSha) {
        SQL sql = new SQL();
        sql.SELECT(TASK_COLUMNS).FROM("review_task");
        sql.WHERE("project_id = #{projectId}");
        if (prId == null || prId.isBlank()) {
            sql.WHERE("pr_id IS NULL");
        } else {
            sql.WHERE("pr_id = #{prId}");
        }
        sql.WHERE("commit_sha = #{commitSha}");
        sql.WHERE("status <> 'EXECUTION_FAILED'");
        sql.ORDER_BY("id ASC");
        return sql.toString() + " LIMIT 1";
    }

    /** 按项目集合 + 可选过滤条件分页列出任务。 */
    public static String pageProjectsSql(final Collection<Long> projectIds,
                                         final String status,
                                         final String triggerType,
                                         final Long projectId,
                                         final int limit,
                                         final int offset) {
        SQL sql = new SQL();
        sql.SELECT(TASK_COLUMNS).FROM("review_task");
        appendProjectsWhere(sql, projectIds, status, triggerType, projectId);
        sql.ORDER_BY("created_at DESC", "id DESC");
        return sql.toString() + " LIMIT " + Math.max(limit, 0) + " OFFSET " + Math.max(offset, 0);
    }

    /** 与 {@link #pageProjectsSql} 同条件的 COUNT(*)。 */
    public static String countProjectsSql(final Collection<Long> projectIds,
                                          final String status,
                                          final String triggerType,
                                          final Long projectId) {
        SQL sql = new SQL();
        sql.SELECT("COUNT(*)").FROM("review_task");
        appendProjectsWhere(sql, projectIds, status, triggerType, projectId);
        return sql.toString();
    }

    private static void appendProjectsWhere(SQL sql,
                                            Collection<Long> projectIds,
                                            String status,
                                            String triggerType,
                                            Long projectId) {
        if (projectIds == null || projectIds.isEmpty()) {
            // 空集合：永远不返回结果（避免泄漏其他项目数据）
            sql.WHERE("1 = 0");
            return;
        }
        // 拼接 IN (#{projectIds[0]}, #{projectIds[1]}, ...)
        StringBuilder in = new StringBuilder("project_id IN (");
        Iterator<Long> it = projectIds.iterator();
        int i = 0;
        while (it.hasNext()) {
            it.next(); // 不使用值；MyBatis 通过下标解引用
            if (i > 0) {
                in.append(", ");
            }
            in.append("#{projectIds[").append(i).append("]}");
            i++;
        }
        in.append(')');
        sql.WHERE(in.toString());

        if (projectId != null) {
            sql.WHERE("project_id = #{projectId}");
        }
        if (status != null && !status.isBlank()) {
            sql.WHERE("status = #{status}");
        }
        if (triggerType != null && !triggerType.isBlank()) {
            sql.WHERE("trigger_type = #{triggerType}");
        }
    }
}
