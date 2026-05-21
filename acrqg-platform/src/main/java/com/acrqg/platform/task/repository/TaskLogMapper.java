package com.acrqg.platform.task.repository;

import com.acrqg.platform.task.domain.TaskLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.SelectProvider;

/**
 * 任务流水 Mapper。
 *
 * <p>{@code task_log.detail} 列为 PostgreSQL JSONB；本 Mapper 在 INSERT 时通过
 * {@code CAST(#{detailJson} AS JSONB)} 完成字符串到 JSONB 的转换，SELECT 时通过
 * {@code detail::text AS detail_json} 转回字符串。这与 audit 模块保持一致。
 *
 * <p>Mapper 仍保留 {@link BaseMapper} 继承关系（与 design.md §6.3 / §7.2 一致），
 * 但 append-only 语义由调用方保证：业务代码仅通过
 * {@link com.acrqg.platform.task.log.TaskLogger} 的 info/warn/error 入口写入；
 * 任务删除时由 {@code ON DELETE CASCADE} 一并清理（V30）。
 *
 * <p>Covers: R9.7, R16.5。
 */
public interface TaskLogMapper extends BaseMapper<TaskLog> {

    /**
     * 插入一条任务流水。
     *
     * <p>覆盖 {@link BaseMapper#insert} 的默认实现，原因是默认实现无法对
     * {@code detail} 列做 JSONB cast。这里使用显式 SQL 完成。
     *
     * @param log 流水对象
     * @return 受影响行数（1）
     */
    @Insert("""
            INSERT INTO task_log (task_id, stage, level, message, detail, created_at)
            VALUES (#{taskId}, #{stage}, #{level}, #{message},
                    CAST(#{detailJson} AS JSONB),
                    COALESCE(#{createdAt}, NOW()))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertLog(TaskLog log);

    /**
     * 按任务 + 可选 stage / level 分页查询，{@code created_at DESC, id DESC} 排序。
     */
    @SelectProvider(type = TaskLogSqlProvider.class, method = "selectByTaskAndFiltersSql")
    @Results(id = "taskLogResultMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "task_id", property = "taskId"),
            @Result(column = "stage", property = "stage"),
            @Result(column = "level", property = "level"),
            @Result(column = "message", property = "message"),
            @Result(column = "detail_json", property = "detailJson"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<TaskLog> selectByTaskAndFilters(@Param("taskId") Long taskId,
                                         @Param("stage") String stage,
                                         @Param("level") String level,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    /** 与 {@link #selectByTaskAndFilters} 同条件下的总条数。 */
    @SelectProvider(type = TaskLogSqlProvider.class, method = "countByTaskAndFiltersSql")
    long countByTaskAndFilters(@Param("taskId") Long taskId,
                               @Param("stage") String stage,
                               @Param("level") String level);
}
