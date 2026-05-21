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
 * <p>Covers: R11.2, R16.2。
 */
public final class CodeIssueSqlProvider {

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
