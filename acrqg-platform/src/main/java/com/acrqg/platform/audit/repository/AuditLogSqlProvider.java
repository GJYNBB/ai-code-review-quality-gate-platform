package com.acrqg.platform.audit.repository;

import java.time.OffsetDateTime;
import org.apache.ibatis.jdbc.SQL;

/**
 * 审计日志的动态 SQL 提供器。
 *
 * <p>独立成类便于：
 * <ul>
 *   <li>把"过滤条件可空 + ORDER BY 固定 + LIMIT/OFFSET"的拼接逻辑集中维护；</li>
 *   <li>避免在 {@link AuditLogMapper} 上写难读的 {@code @Select} XML 风格 if 块；</li>
 *   <li>使 SQL 拼接行为可被单元测试单独覆盖（无需启动 Spring）。</li>
 * </ul>
 *
 * <p>SQL 全部使用 {@code #{xxx}} 占位符进行参数绑定，避免 SQL 注入。
 *
 * <p>Covers: R22.2, R22.3。
 */
public final class AuditLogSqlProvider {

    private AuditLogSqlProvider() {
        // utility class
    }

    /**
     * 生成"按条件列表 + 分页"的 SQL。
     *
     * <p>排序固定为 {@code created_at DESC, id DESC}：
     * <ul>
     *   <li>{@code created_at DESC} 与 {@code idx_audit_operator_action_time} /
     *       {@code idx_audit_action_time} 索引方向一致；</li>
     *   <li>{@code id DESC} 作为相同时间戳下的稳定 tie-breaker，确保分页边界确定。</li>
     * </ul>
     */
    public static String listSql(final String operatorUsername,
                                 final String action,
                                 final OffsetDateTime startDate,
                                 final OffsetDateTime endDate,
                                 final int limit,
                                 final int offset) {
        SQL sql = new SQL();
        sql.SELECT("id", "operator_id", "operator_username", "action", "resource_type",
                "resource_id", "ip", "detail::text AS detail_json", "created_at");
        sql.FROM("audit_log");
        appendWhere(sql, operatorUsername, action, startDate, endDate);
        sql.ORDER_BY("created_at DESC", "id DESC");
        // LIMIT / OFFSET 通过字符串拼接（数值类型，无注入风险），不能用 #{}（MyBatis JDBC SQL Builder 不支持 LIMIT 占位）
        return sql.toString() + " LIMIT " + Math.max(limit, 0) + " OFFSET " + Math.max(offset, 0);
    }

    /** 生成与 {@link #listSql} 同条件的 COUNT(*) SQL。 */
    public static String countSql(final String operatorUsername,
                                  final String action,
                                  final OffsetDateTime startDate,
                                  final OffsetDateTime endDate) {
        SQL sql = new SQL();
        sql.SELECT("COUNT(*)");
        sql.FROM("audit_log");
        appendWhere(sql, operatorUsername, action, startDate, endDate);
        return sql.toString();
    }

    private static void appendWhere(SQL sql,
                                    String operatorUsername,
                                    String action,
                                    OffsetDateTime startDate,
                                    OffsetDateTime endDate) {
        if (operatorUsername != null && !operatorUsername.isBlank()) {
            sql.WHERE("operator_username = #{operatorUsername}");
        }
        if (action != null && !action.isBlank()) {
            sql.WHERE("action = #{action}");
        }
        if (startDate != null) {
            sql.WHERE("created_at >= #{startDate}");
        }
        if (endDate != null) {
            sql.WHERE("created_at < #{endDate}");
        }
    }
}
