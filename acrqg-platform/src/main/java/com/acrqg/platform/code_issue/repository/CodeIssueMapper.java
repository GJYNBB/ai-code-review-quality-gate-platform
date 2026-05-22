package com.acrqg.platform.code_issue.repository;

import com.acrqg.platform.code_issue.domain.CodeIssue;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

/**
 * 代码问题 Mapper（B3-D / B3-E / B3-F / B4-A 共享）。
 *
 * <p>本 Mapper 关注高频写入与聚合查询：
 * <ul>
 *   <li>{@link #insertBatch(Collection)} —— 批量插入；用于 SAST / AI 阶段
 *       一次写入多条问题。失败时整体回滚（事务由 Service 层包裹）。</li>
 *   <li>{@link #findByTask(Long, String, String, String, String, int, int)}
 *       与 {@link #countByTask(Long, String, String, String, String)} ——
 *       报告页 / B4-A 列表分页 + 过滤。</li>
 *   <li>{@link #countByTaskWithFilter(Long, Collection, Collection)} ——
 *       B3-F MetricCollector 聚合（如 {@code critical_issue_count}）。</li>
 * </ul>
 *
 * <p>SQL 分页 / 过滤通过 MyBatis 动态 SQL 完成，{@code null} 入参不参与过滤。
 * 文件路径采用 {@code ILIKE %?%} 大小写不敏感匹配；列举集合（{@code severityIn}
 * / {@code statusNotIn}）使用 {@code IN} / {@code NOT IN} 子句，空集合时直接
 * 跳过该谓词，避免生成 {@code IN ()} 的非法 SQL。
 *
 * <p>Covers: R11.2, R12.3, R16.2, R17。
 */
public interface CodeIssueMapper extends BaseMapper<CodeIssue> {

    /**
     * 批量插入代码问题。
     *
     * <p>调用方应保证 {@code issues} 非空且每条记录的 {@code taskId} /
     * {@code filePath} / {@code source} / {@code severity} / {@code status} /
     * {@code description} 已正确赋值。{@code created_at} / {@code updated_at}
     * 由 DB 端 {@code DEFAULT NOW()} 兜底；若 DO 已赋值则使用该值。
     *
     * @param issues 待插入问题列表
     * @return 受影响行数
     */
    @Insert("""
            <script>
            INSERT INTO code_issue (
                task_id, file_path, line_no, rule_code, source, severity,
                status, description, suggestion, confidence
            ) VALUES
            <foreach collection='issues' item='it' separator=','>
                (#{it.taskId}, #{it.filePath}, #{it.lineNo}, #{it.ruleCode},
                 #{it.source}, #{it.severity}, COALESCE(#{it.status}, 'NEW'),
                 #{it.description}, #{it.suggestion}, #{it.confidence})
            </foreach>
            </script>
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertBatch(@Param("issues") Collection<CodeIssue> issues);

    /**
     * 按任务分页查询问题。
     *
     * <p>{@code severity} / {@code status} / {@code source} 为精确过滤，
     * {@code filePath} 为模糊匹配（{@code ILIKE %filePath%}）。所有过滤字段
     * 为 {@code null} 或空串时不过滤。
     *
     * <p>排序：{@code severity DESC（按枚举顺序，CRITICAL 最高）}，
     * {@code created_at DESC}，{@code id DESC}。
     */
    @Select("""
            <script>
            SELECT id, task_id, file_path, line_no, rule_code, source, severity,
                   status, description, suggestion, confidence,
                   created_at, updated_at
              FROM code_issue
             WHERE task_id = #{taskId}
            <if test='severity != null and severity != ""'>
              AND severity = #{severity}
            </if>
            <if test='status != null and status != ""'>
              AND status = #{status}
            </if>
            <if test='source != null and source != ""'>
              AND source = #{source}
            </if>
            <if test='filePath != null and filePath != ""'>
              AND file_path ILIKE CONCAT('%', #{filePath}, '%')
            </if>
             ORDER BY CASE severity
                        WHEN 'CRITICAL' THEN 1
                        WHEN 'HIGH'     THEN 2
                        WHEN 'MEDIUM'   THEN 3
                        WHEN 'LOW'      THEN 4
                        WHEN 'INFO'     THEN 5
                        ELSE 6 END ASC,
                      created_at DESC,
                      id DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @Results(id = "codeIssueMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "task_id", property = "taskId"),
            @Result(column = "file_path", property = "filePath"),
            @Result(column = "line_no", property = "lineNo"),
            @Result(column = "rule_code", property = "ruleCode"),
            @Result(column = "source", property = "source"),
            @Result(column = "severity", property = "severity"),
            @Result(column = "status", property = "status"),
            @Result(column = "description", property = "description"),
            @Result(column = "suggestion", property = "suggestion"),
            @Result(column = "confidence", property = "confidence"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<CodeIssue> findByTask(@Param("taskId") Long taskId,
                               @Param("severity") String severity,
                               @Param("status") String status,
                               @Param("source") String source,
                               @Param("filePath") String filePath,
                               @Param("limit") int limit,
                               @Param("offset") int offset);

    /** 与 {@link #findByTask} 同条件的总条数。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM code_issue
             WHERE task_id = #{taskId}
            <if test='severity != null and severity != ""'>
              AND severity = #{severity}
            </if>
            <if test='status != null and status != ""'>
              AND status = #{status}
            </if>
            <if test='source != null and source != ""'>
              AND source = #{source}
            </if>
            <if test='filePath != null and filePath != ""'>
              AND file_path ILIKE CONCAT('%', #{filePath}, '%')
            </if>
            </script>
            """)
    long countByTask(@Param("taskId") Long taskId,
                     @Param("severity") String severity,
                     @Param("status") String status,
                     @Param("source") String source,
                     @Param("filePath") String filePath);

    /**
     * B3-F MetricCollector 聚合用：按任务统计满足
     * {@code severity IN (severityIn) AND status NOT IN (statusNotIn)} 的问题数。
     *
     * <p>{@code severityIn} 为空集合时不限制 severity；{@code statusNotIn}
     * 为空集合时不排除任何状态。SQL 使用 {@code <if>} 防止生成空 IN 列表。
     *
     * <p>典型用法：
     * <pre>
     * critical_issue_count = countByTaskWithFilter(
     *     taskId,
     *     Set.of("CRITICAL"),
     *     Set.of("FALSE_POSITIVE", "CLOSED"));
     * </pre>
     *
     * @param taskId      任务主键
     * @param severityIn  severity 白名单
     * @param statusNotIn status 黑名单
     * @return 满足条件的问题总数
     */
    @SelectProvider(type = CodeIssueSqlProvider.class, method = "countByTaskWithFilterSql")
    long countByTaskWithFilter(@Param("taskId") Long taskId,
                               @Param("severityIn") Collection<String> severityIn,
                               @Param("statusNotIn") Collection<String> statusNotIn);

    /**
     * B4-A.2 列表分页：按任务 + 多值过滤（severity / status / source / filePath / keyword）查询。
     *
     * <p>排序：{@code severity_rank ASC, created_at ASC, id ASC}（R16.2：CRITICAL 最高 +
     * 历史问题排前），与 {@code idx_code_issue_task_severity_source} 索引方向兼容。
     *
     * <p>过滤语义：
     * <ul>
     *   <li>{@code severityIn} / {@code statusIn}：空 / null 不过滤；非空走 {@code IN (...)}；</li>
     *   <li>{@code source}：null/空 不过滤；否则 {@code source = ?}；</li>
     *   <li>{@code filePath}：null/空 不过滤；否则 {@code file_path ILIKE %?%}；</li>
     *   <li>{@code keyword}：null/空 不过滤；否则按 {@code description / rule_code / file_path}
     *       任一 ILIKE 模糊匹配（R16.2 "多维度筛选"）。</li>
     * </ul>
     *
     * @param taskId      任务主键，非空
     * @param severityIn  severity 集合过滤
     * @param statusIn    status 集合过滤
     * @param source      source 精确过滤
     * @param filePath    文件路径模糊
     * @param keyword     关键字（description / rule_code / file_path 多列模糊）
     * @param limit       每页条数
     * @param offset      偏移
     * @return 当前页问题列表
     */
    @SelectProvider(type = CodeIssueSqlProvider.class, method = "pageByTaskWithFilterSql")
    @ResultMap("codeIssueMap")
    List<CodeIssue> pageByTaskWithFilter(@Param("taskId") Long taskId,
                                         @Param("severityIn") Collection<String> severityIn,
                                         @Param("statusIn") Collection<String> statusIn,
                                         @Param("source") String source,
                                         @Param("filePath") String filePath,
                                         @Param("keyword") String keyword,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    /** 与 {@link #pageByTaskWithFilter} 同条件下的总条数。 */
    @SelectProvider(type = CodeIssueSqlProvider.class, method = "countByTaskWithMultiFilterSql")
    long countByTaskWithMultiFilter(@Param("taskId") Long taskId,
                                    @Param("severityIn") Collection<String> severityIn,
                                    @Param("statusIn") Collection<String> statusIn,
                                    @Param("source") String source,
                                    @Param("filePath") String filePath,
                                    @Param("keyword") String keyword);

    /**
     * 原子状态更新：仅当 {@code status = #{from}} 时把状态置为 {@code to} 并刷新 updated_at。
     *
     * <p>返回 0 表示并发抢占或非法迁移；Service 层应抛 {@code VALIDATION_ERROR}。
     *
     * @param id   问题主键
     * @param from 期望的当前状态
     * @param to   目标状态
     * @return 受影响行数（0 / 1）
     */
    @Update("UPDATE code_issue SET status = #{to}, updated_at = NOW() "
            + "WHERE id = #{id} AND status = #{from}")
    int updateStatusOnlyIfFrom(@Param("id") Long id,
                               @Param("from") String from,
                               @Param("to") String to);
}
