package com.acrqg.platform.project.repository;

import com.acrqg.platform.project.domain.Project;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 项目 Mapper（B1-C.2）。
 *
 * <p>继承 {@link BaseMapper} 享受标准 CRUD 与
 * {@code com.baomidou.mybatisplus.core.conditions.Wrapper} 查询；额外提供：
 * <ul>
 *   <li>{@link #pageList} —— 关键字模糊 + 分页（{@code id DESC}），
 *       同时通过子查询返回 {@code member_count}，避免 N+1。</li>
 *   <li>{@link #countByKeyword} —— 与 pageList 配套的 count 查询。</li>
 *   <li>{@link #selectWithMemberCount} —— 单行详情，附带 memberCount。</li>
 * </ul>
 *
 * <p>关键字匹配通过 {@code ILIKE %keyword%} 完成（PostgreSQL 大小写不敏感），
 * 命中 {@code name} 或 {@code description} 任一即可。{@code keyword} 为
 * {@code null} 或空时不参与过滤。
 *
 * <p>Covers: R4.1, R4.3。
 */
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 项目分页查询：按 id 倒序、按关键字模糊匹配，附带成员数量。
     *
     * <p>结果集列名 {@code member_count} 通过 {@link Result} 映射到
     * {@code memberCount} 字段（位于 {@link ProjectListRow}）。
     *
     * @param keyword  关键字（{@code null}/空 表示不过滤）
     * @param limit    每页条数
     * @param offset   偏移量 = {@code (page-1)*pageSize}
     */
    @Select("""
            <script>
            SELECT p.id, p.name, p.description, p.default_branch, p.language,
                   p.created_by, p.created_at, p.updated_at,
                   (SELECT COUNT(1) FROM project_member m WHERE m.project_id = p.id) AS member_count
              FROM project p
            <where>
              <if test='keyword != null and keyword != ""'>
                AND (p.name ILIKE CONCAT('%', #{keyword}, '%')
                  OR COALESCE(p.description,'') ILIKE CONCAT('%', #{keyword}, '%'))
              </if>
            </where>
             ORDER BY p.id DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @Results(id = "projectListRowMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "name", property = "name"),
            @Result(column = "description", property = "description"),
            @Result(column = "default_branch", property = "defaultBranch"),
            @Result(column = "language", property = "language"),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt"),
            @Result(column = "member_count", property = "memberCount")
    })
    List<ProjectListRow> pageList(@Param("keyword") String keyword,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    /** 与 {@link #pageList} 同一过滤条件下的总条数。 */
    @Select("""
            <script>
            SELECT COUNT(1)
              FROM project p
            <where>
              <if test='keyword != null and keyword != ""'>
                AND (p.name ILIKE CONCAT('%', #{keyword}, '%')
                  OR COALESCE(p.description,'') ILIKE CONCAT('%', #{keyword}, '%'))
              </if>
            </where>
            </script>
            """)
    long countByKeyword(@Param("keyword") String keyword);

    /** 普通用户仅能分页查看自己参与的项目。 */
    @Select("""
            <script>
            SELECT p.id, p.name, p.description, p.default_branch, p.language,
                   p.created_by, p.created_at, p.updated_at,
                   (SELECT COUNT(1) FROM project_member m2 WHERE m2.project_id = p.id) AS member_count
              FROM project p
              JOIN project_member pm ON pm.project_id = p.id
             WHERE pm.user_id = #{userId}
              <if test='keyword != null and keyword != ""'>
                AND (p.name ILIKE CONCAT('%', #{keyword}, '%')
                  OR COALESCE(p.description,'') ILIKE CONCAT('%', #{keyword}, '%'))
              </if>
             ORDER BY p.id DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @Results(id = "projectVisibleListRowMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "name", property = "name"),
            @Result(column = "description", property = "description"),
            @Result(column = "default_branch", property = "defaultBranch"),
            @Result(column = "language", property = "language"),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt"),
            @Result(column = "member_count", property = "memberCount")
    })
    List<ProjectListRow> pageVisibleList(@Param("userId") Long userId,
                                         @Param("keyword") String keyword,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    /** 普通用户可见项目计数。 */
    @Select("""
            <script>
            SELECT COUNT(1)
              FROM project p
              JOIN project_member pm ON pm.project_id = p.id
             WHERE pm.user_id = #{userId}
              <if test='keyword != null and keyword != ""'>
                AND (p.name ILIKE CONCAT('%', #{keyword}, '%')
                  OR COALESCE(p.description,'') ILIKE CONCAT('%', #{keyword}, '%'))
              </if>
            </script>
            """)
    long countVisibleByKeyword(@Param("userId") Long userId,
                               @Param("keyword") String keyword);

    /**
     * 主键单行查询，附带 memberCount。
     *
     * @return 命中返回 {@link ProjectListRow}；否则 {@code null}
     */
    @Select("""
            SELECT p.id, p.name, p.description, p.default_branch, p.language,
                   p.created_by, p.created_at, p.updated_at,
                   (SELECT COUNT(1) FROM project_member m WHERE m.project_id = p.id) AS member_count
              FROM project p
             WHERE p.id = #{projectId}
            """)
    @Results(id = "projectListRowMap2", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "name", property = "name"),
            @Result(column = "description", property = "description"),
            @Result(column = "default_branch", property = "defaultBranch"),
            @Result(column = "language", property = "language"),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt"),
            @Result(column = "member_count", property = "memberCount")
    })
    ProjectListRow selectWithMemberCount(@Param("projectId") Long projectId);
}
