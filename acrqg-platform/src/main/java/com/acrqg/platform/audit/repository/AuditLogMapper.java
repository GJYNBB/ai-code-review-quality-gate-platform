package com.acrqg.platform.audit.repository;

import com.acrqg.platform.audit.domain.AuditLog;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

/**
 * 审计日志 Mapper。
 *
 * <p><b>双重 append-only 保险（R22.4）</b>：
 * <ol>
 *   <li>数据库触发器 {@code trg_audit_no_update / trg_audit_no_delete} 会拒绝
 *       任何 UPDATE / DELETE，即便代码层调用了 update / delete API 也会在 DB 层
 *       抛 {@code audit_log is append-only}（V1__init.sql）。</li>
 *   <li>本 Mapper <b>不</b> 继承 {@code com.baomidou.mybatisplus.core.mapper.BaseMapper}，
 *       因此在 Java 层就不暴露 {@code updateById / deleteById / update(Wrapper)} 等方法。
 *       这让"违反 append-only"的代码无法编译，从源头杜绝错误。</li>
 * </ol>
 *
 * <p>暴露的能力被严格限制为：
 * <ul>
 *   <li>{@link #insert(AuditLog)} —— 唯一写入入口；{@code created_at} 由数据库
 *       {@code DEFAULT NOW()} 兜底，写入后通过 {@code @Options(useGeneratedKeys=true)}
 *       回填自增主键到 DO；</li>
 *   <li>{@link #selectById(Long)} —— 主键单行查询；</li>
 *   <li>{@link #count(String, String, OffsetDateTime, OffsetDateTime)}
 *       —— 计数（用于分页 total）；</li>
 *   <li>{@link #selectList(String, String, OffsetDateTime, OffsetDateTime, int, int)}
 *       —— 列表（带 LIMIT/OFFSET 分页与时间倒序）。</li>
 * </ul>
 *
 * <p>{@code detail} 字段在 PostgreSQL 中为 JSONB；本 Mapper 在 SELECT 时使用
 * {@code detail::text AS detail_json} 的 cast 把 JSONB 转换为字符串，由 Service
 * 层通过 {@link com.acrqg.platform.common.util.JsonUtils} 反序列化。INSERT 时通过
 * {@code CAST(#{detailJson} AS JSONB)} 把字符串转换为 JSONB，便于享受 PostgreSQL 的
 * JSON 索引能力。
 *
 * <p>Covers: R22.2, R22.3, R22.4。
 */
public interface AuditLogMapper {

    /**
     * 插入一条审计日志（唯一写入入口）。
     *
     * <p>{@code id} 为 {@code BIGSERIAL}；{@code created_at} 为
     * {@code DEFAULT NOW()}：当 DO 中 {@code createdAt=null} 时由 DB 兜底，
     * 否则使用 DO 中的值。{@code @Options(useGeneratedKeys=true, keyProperty="id")}
     * 让 PostgreSQL 通过 RETURNING 子句回填主键到 DO。
     *
     * @param log 审计日志领域对象
     * @return 受影响行数（成功为 1）
     */
    @Insert("""
            INSERT INTO audit_log
                (operator_id, operator_username, action, resource_type, resource_id,
                 ip, detail, created_at)
            VALUES
                (#{operatorId}, #{operatorUsername}, #{action}, #{resourceType}, #{resourceId},
                 #{ip},
                 CAST(#{detailJson} AS JSONB),
                 COALESCE(#{createdAt}, NOW()))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(AuditLog log);

    /**
     * 主键查询。
     *
     * @param id 主键
     * @return 命中时返回 {@link AuditLog}，否则 {@code null}
     */
    @Select("""
            SELECT id, operator_id, operator_username, action, resource_type,
                   resource_id, ip, detail::text AS detail_json, created_at
              FROM audit_log
             WHERE id = #{id}
            """)
    @Results(id = "auditLogResultMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "operator_id", property = "operatorId"),
            @Result(column = "operator_username", property = "operatorUsername"),
            @Result(column = "action", property = "action"),
            @Result(column = "resource_type", property = "resourceType"),
            @Result(column = "resource_id", property = "resourceId"),
            @Result(column = "ip", property = "ip"),
            @Result(column = "detail_json", property = "detailJson"),
            @Result(column = "created_at", property = "createdAt")
    })
    AuditLog selectById(@Param("id") Long id);

    /**
     * 统计满足查询条件的审计日志总条数（用于分页 total）。
     * 任一参数为 {@code null} 时表示该过滤条件不参与。
     */
    @SelectProvider(type = AuditLogSqlProvider.class, method = "countSql")
    long count(@Param("operatorUsername") String operatorUsername,
               @Param("action") String action,
               @Param("startDate") OffsetDateTime startDate,
               @Param("endDate") OffsetDateTime endDate);

    /**
     * 按条件列出审计日志。
     *
     * <p>排序固定为 {@code created_at DESC, id DESC}（与 design.md §22.2 一致）。
     *
     * @param operatorUsername 操作者用户名（精确匹配，{@code null} 表示不过滤）
     * @param action           动作名（精确匹配，{@code null} 表示不过滤）
     * @param startDate        创建时间下限（含），{@code null} 表示不过滤
     * @param endDate          创建时间上限（不含），{@code null} 表示不过滤
     * @param limit            最多返回条数（即 pageSize）
     * @param offset           偏移量 = (page-1) * pageSize
     * @return 当前页元素列表
     */
    @SelectProvider(type = AuditLogSqlProvider.class, method = "listSql")
    @Results(id = "auditLogListResultMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "operator_id", property = "operatorId"),
            @Result(column = "operator_username", property = "operatorUsername"),
            @Result(column = "action", property = "action"),
            @Result(column = "resource_type", property = "resourceType"),
            @Result(column = "resource_id", property = "resourceId"),
            @Result(column = "ip", property = "ip"),
            @Result(column = "detail_json", property = "detailJson"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<AuditLog> selectList(@Param("operatorUsername") String operatorUsername,
                              @Param("action") String action,
                              @Param("startDate") OffsetDateTime startDate,
                              @Param("endDate") OffsetDateTime endDate,
                              @Param("limit") int limit,
                              @Param("offset") int offset);
}
