package com.acrqg.platform.project.repository;

import com.acrqg.platform.project.domain.ProjectMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 项目成员 Mapper（B1-C.2）。
 *
 * <p>继承 {@link BaseMapper} 享受标准 CRUD；额外提供 design / tasks 中要求的：
 * <ul>
 *   <li>{@link #countMembers(Long)} —— 项目成员数；</li>
 *   <li>{@link #isMember(Long, Long)} —— 是否为项目成员；返回非空即视为命中；</li>
 *   <li>{@link #roleOf(Long, Long)} —— 项目内角色字符串；不存在返回 {@code null}；</li>
 *   <li>{@link #listMembers(Long)} —— 项目成员列表（join "user" 表，返回 {@link ProjectMemberRow}）；</li>
 *   <li>{@link #deleteByProjectAndUser(Long, Long)} —— 按 (projectId, userId) 删除一行。</li>
 * </ul>
 *
 * <p>Covers: R6.1, R6.3, R6.4。
 */
public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    /** 统计指定项目的成员数量。 */
    @Select("SELECT COUNT(1) FROM project_member WHERE project_id = #{projectId}")
    int countMembers(@Param("projectId") Long projectId);

    /**
     * 判断 {@code userId} 是否为 {@code projectId} 的成员。
     *
     * <p>返回值：命中返回 {@code 1}，未命中返回 {@code 0}（或 {@code null} 时按 0 处理）。
     * Service 层应使用 {@link #roleOf} 同时拿到角色，避免一次额外查询。
     */
    @Select("SELECT COUNT(1) FROM project_member WHERE project_id = #{projectId} AND user_id = #{userId}")
    int isMember(@Param("projectId") Long projectId, @Param("userId") Long userId);

    /**
     * 查询用户在项目内的角色。
     *
     * @return 项目角色字符串（{@code DEVELOPER} / {@code REVIEWER} /
     *         {@code PROJECT_ADMIN}）；用户非成员返回 {@code null}
     */
    @Select("SELECT project_role FROM project_member WHERE project_id = #{projectId} AND user_id = #{userId}")
    String roleOf(@Param("projectId") Long projectId, @Param("userId") Long userId);

    /**
     * 列出项目成员，包含用户名快照。
     *
     * <p>SQL JOIN {@code "user"} 表保证 {@code username} 一次性返回，便于前端展示。
     * 排序：先按角色（PROJECT_ADMIN > REVIEWER > DEVELOPER）便于管理员置顶，
     * 同角色按 {@code joined_at} 升序。
     */
    @Select("""
            SELECT pm.user_id, u.username, pm.project_role, pm.joined_at
              FROM project_member pm
              JOIN "user" u ON u.id = pm.user_id
             WHERE pm.project_id = #{projectId}
             ORDER BY CASE pm.project_role
                          WHEN 'PROJECT_ADMIN' THEN 1
                          WHEN 'REVIEWER'      THEN 2
                          WHEN 'DEVELOPER'     THEN 3
                          ELSE 9
                      END,
                      pm.joined_at ASC, pm.id ASC
            """)
    @Results(id = "projectMemberRowMap", value = {
            @Result(column = "user_id", property = "userId"),
            @Result(column = "username", property = "username"),
            @Result(column = "project_role", property = "projectRole"),
            @Result(column = "joined_at", property = "joinedAt")
    })
    List<ProjectMemberRow> listMembers(@Param("projectId") Long projectId);

    /**
     * 按 (projectId, userId) 删除一行。
     *
     * @return 受影响行数（0 表示原本就不存在）
     */
    @Delete("DELETE FROM project_member WHERE project_id = #{projectId} AND user_id = #{userId}")
    int deleteByProjectAndUser(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
