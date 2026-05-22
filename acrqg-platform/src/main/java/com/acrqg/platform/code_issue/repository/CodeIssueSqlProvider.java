package com.acrqg.platform.code_issue.repository;

import java.util.Collection;
import java.util.Map;
import org.apache.ibatis.jdbc.SQL;

/**
 * {@link CodeIssueMapper} 的 {@code @SelectProvider} 实现类。
 *
 * <p>用 Java 拼装动态 SQL，避免 XML 与字符串模板。处理空集合的 {@code IN} /
 * {@code NOT IN} 子句也比 MyBatis 动态 SQL 写起来更直观。
 *
 * <p>Covers: R11.2, R16.2, R17.6。
 */
public final class CodeIssueSqlProvider {

    /** 列表查询的列集合（与 {@code codeIssueMap} ResultMap 对齐）。 */
    private static final String LIST_COLUMNS =
            "id, task_id, file_path, line_no, rule_code, source, severity, "
                    + "status, description, suggestion, confidence, created_at, updated_at";

    /**
     * severity 排序权重 SQL 表达式：CRITICAL 最高（1）→ INFO 最低（5），未知值兜底 6。
     * 与 {@link CodeIssueMapper#findByTask} 中的 CASE 表达式一致。
     */
    private static final String SEVERITY_RANK =
            "CASE severity "
                    + "WHEN 'CRITICAL' THEN 1 "
                    + "WHEN 'HIGH' THEN 2 "
                    + "WHEN 'MEDIUM' THEN 3 "
                    + "WHEN 'LOW' THEN 4 "
                    + "WHEN 'INFO' THEN 5 "
                    + "ELSE 6 END";

    private CodeIssueSqlProvider() {
        // utility
    }

    /**
     * 生成 {@code countByTaskWithFilter} 的 SQL。
     *
     * <p>注意：
     * <ul>
     *   <li>使用占位符列表 {@code (#{severityIn[0]}, #{severityIn[1]}, ...)}
     *       由 MyBatis 在执行时替换为实际参数；</li>
     *   <li>当集合为空时跳过对应谓词，避免生成 {@code IN ()} 非法 SQL。</li>
     * </ul>
     */
    public static String countByTaskWithFilterSql(Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        Collection<String> severityIn = (Collection<String>) params.get("severityIn");
        @SuppressWarnings("unchecked")
        Collection<String> statusNotIn = (Collection<String>) params.get("statusNotIn");

        SQL sql = new SQL();
        sql.SELECT("COUNT(1)").FROM("code_issue").WHERE("task_id = #{taskId}");

        if (severityIn != null && !severityIn.isEmpty()) {
            sql.WHERE("severity IN (" + buildPlaceholders("severityIn", severityIn.size()) + ")");
        }
        if (statusNotIn != null && !statusNotIn.isEmpty()) {
            sql.WHERE("status NOT IN (" + buildPlaceholders("statusNotIn", statusNotIn.size()) + ")");
        }
        return sql.toString();
    }

    /**
     * 生成 {@code pageByTaskWithFilter} 的 SQL（B4-A.2 列表查询）。
     *
     * <p>支持 severity / status / source / filePath / keyword 5 维过滤；
     * 排序：{@link #SEVERITY_RANK} ASC, {@code created_at ASC, id ASC}（R16.2）。
     */
    public static String pageByTaskWithFilterSql(Map<String, Object> params) {
        SQL sql = new SQL();
        sql.SELECT(LIST_COLUMNS).FROM("code_issue");
        appendMultiFilterWhere(sql, params);
        sql.ORDER_BY(SEVERITY_RANK + " ASC", "created_at ASC", "id ASC");

        // LIMIT / OFFSET 数值类型，无注入风险；MyBatis JDBC SQL Builder 不支持 LIMIT 占位
        Object limit = params.get("limit");
        Object offset = params.get("offset");
        int safeLimit = (limit instanceof Number n) ? Math.max(n.intValue(), 0) : 0;
        int safeOffset = (offset instanceof Number n) ? Math.max(n.intValue(), 0) : 0;
        return sql.toString() + " LIMIT " + safeLimit + " OFFSET " + safeOffset;
    }

    /** 与 {@link #pageByTaskWithFilterSql} 同条件下的 COUNT。 */
    public static String countByTaskWithMultiFilterSql(Map<String, Object> params) {
        SQL sql = new SQL();
        sql.SELECT("COUNT(1)").FROM("code_issue");
        appendMultiFilterWhere(sql, params);
        return sql.toString();
    }

    private static void appendMultiFilterWhere(SQL sql, Map<String, Object> params) {
        sql.WHERE("task_id = #{taskId}");

        @SuppressWarnings("unchecked")
        Collection<String> severityIn = (Collection<String>) params.get("severityIn");
        if (severityIn != null && !severityIn.isEmpty()) {
            sql.WHERE("severity IN (" + buildPlaceholders("severityIn", severityIn.size()) + ")");
        }

        @SuppressWarnings("unchecked")
        Collection<String> statusIn = (Collection<String>) params.get("statusIn");
        if (statusIn != null && !statusIn.isEmpty()) {
            sql.WHERE("status IN (" + buildPlaceholders("statusIn", statusIn.size()) + ")");
        }

        Object source = params.get("source");
        if (source instanceof String s && !s.isBlank()) {
            sql.WHERE("source = #{source}");
        }

        Object filePath = params.get("filePath");
        if (filePath instanceof String fp && !fp.isBlank()) {
            sql.WHERE("file_path ILIKE CONCAT('%', #{filePath}, '%')");
        }

        Object keyword = params.get("keyword");
        if (keyword instanceof String k && !k.isBlank()) {
            sql.WHERE("(description ILIKE CONCAT('%', #{keyword}, '%') "
                    + "OR rule_code ILIKE CONCAT('%', #{keyword}, '%') "
                    + "OR file_path ILIKE CONCAT('%', #{keyword}, '%'))");
        }
    }

    private static String buildPlaceholders(String paramName, int size) {
        StringBuilder sb = new StringBuilder(size * 16);
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("#{").append(paramName).append('[').append(i).append("]}");
        }
        return sb.toString();
    }
}
