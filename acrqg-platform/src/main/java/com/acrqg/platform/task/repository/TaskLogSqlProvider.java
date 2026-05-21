package com.acrqg.platform.task.repository;

import org.apache.ibatis.jdbc.SQL;

/**
 * 任务流水的动态 SQL 提供器。
 *
 * <p>把"按 taskId + 可选 stage / level + 分页"的 SQL 集中在此，便于阅读与单测。
 *
 * <p>Covers: R9.7, R16.5。
 */
public final class TaskLogSqlProvider {

    private static final String COLUMNS =
            "id, task_id, stage, level, message, detail::text AS detail_json, created_at";

    private TaskLogSqlProvider() {
        // utility class
    }

    public static String selectByTaskAndFiltersSql(final Long taskId,
                                                   final String stage,
                                                   final String level,
                                                   final int limit,
                                                   final int offset) {
        SQL sql = new SQL();
        sql.SELECT(COLUMNS).FROM("task_log");
        appendWhere(sql, taskId, stage, level);
        sql.ORDER_BY("created_at DESC", "id DESC");
        return sql.toString() + " LIMIT " + Math.max(limit, 0) + " OFFSET " + Math.max(offset, 0);
    }

    public static String countByTaskAndFiltersSql(final Long taskId,
                                                  final String stage,
                                                  final String level) {
        SQL sql = new SQL();
        sql.SELECT("COUNT(*)").FROM("task_log");
        appendWhere(sql, taskId, stage, level);
        return sql.toString();
    }

    private static void appendWhere(SQL sql, Long taskId, String stage, String level) {
        if (taskId != null) {
            sql.WHERE("task_id = #{taskId}");
        }
        if (stage != null && !stage.isBlank()) {
            sql.WHERE("stage = #{stage}");
        }
        if (level != null && !level.isBlank()) {
            sql.WHERE("level = #{level}");
        }
    }
}
