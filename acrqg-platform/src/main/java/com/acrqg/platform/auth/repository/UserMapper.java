package com.acrqg.platform.auth.repository;

import com.acrqg.platform.auth.domain.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 Mapper（B1-A.1）。
 *
 * <p>继承 {@link BaseMapper} 享受标准 CRUD（用于 INSERT / 主键查询等简单场景）；
 * 对于"用户 + 角色列表"的连接查询，单独写在 {@code UserMapper.xml} 中并通过
 * collection ResultMap 装配 {@code roles} 字段，避免 N+1。
 *
 * <p>暴露的能力：
 * <ul>
 *   <li>{@link #selectWithRolesByUsername(String)} —— 按用户名查询用户 + 角色列表，
 *       供登录场景使用；</li>
 *   <li>{@link #selectWithRolesById(Long)} —— 按 id 查询用户 + 角色列表，
 *       供 {@code AuthService.me} / 用户详情使用；</li>
 *   <li>{@link #selectByUsernameOrEmail(String, String)} —— 按用户名或邮箱查询
 *       （仅基础字段，不含 roles），用于唯一性校验（R3.4）；</li>
 *   <li>{@link #pageWithRoles} / {@link #countByQuery} —— 分页查询用户列表（R3.1）。</li>
 *   <li>{@link #updateStatus(Long, String, OffsetDateTime)} —— 切换用户状态。</li>
 * </ul>
 *
 * <p>Covers: R1.1, R1.2, R1.6, R3.1, R3.2, R3.4。
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按用户名连表查询用户与全局角色列表。
     *
     * <p>SQL 实现位于 {@code resources/mapper/UserMapper.xml}，使用 ResultMap
     * 把 {@code role.code} 列收集为 {@link User#getRoles()}。
     *
     * @param username 用户名
     * @return 命中时返回 {@link User}（含 roles），否则 {@code null}
     */
    User selectWithRolesByUsername(@Param("username") String username);

    /**
     * 按 id 连表查询用户与全局角色列表。
     */
    User selectWithRolesById(@Param("id") Long id);

    /**
     * 按用户名或邮箱查询基础用户（不含 roles）。
     *
     * <p>用于注册 / 创建用户时的唯一性校验，二者均为 {@code OR} 条件命中即返回。
     *
     * @param username 用户名（任一可空，但至少一个非 null）
     * @param email    邮箱
     * @return 命中的第一条用户；无命中返回 {@code null}
     */
    @Select("""
            <script>
            SELECT id, username, email, password_hash, status, created_at, updated_at
              FROM "user"
            <where>
              <if test='username != null and username != ""'>
                username = #{username}
              </if>
              <if test='email != null and email != ""'>
                <if test='username != null and username != ""'>OR</if>
                email = #{email}
              </if>
            </where>
            LIMIT 1
            </script>
            """)
    User selectByUsernameOrEmail(@Param("username") String username,
                                 @Param("email") String email);

    /**
     * 用户分页查询（含 roles）。
     *
     * <p>定义在 XML 中。使用 {@code DISTINCT} + 子查询的方式确保按 {@code u.id}
     * 分页时不会因 JOIN {@code user_role} 而产生重复行。
     *
     * @param keyword 关键字（模糊匹配 username/email；可空）
     * @param status  状态精确匹配（可空）
     * @param role    角色编码精确匹配（可空）
     * @param limit   每页条数
     * @param offset  偏移量
     */
    List<User> pageWithRoles(@Param("keyword") String keyword,
                             @Param("status") String status,
                             @Param("role") String role,
                             @Param("limit") int limit,
                             @Param("offset") int offset);

    /** 与 {@link #pageWithRoles} 同一过滤条件下的总条数。 */
    long countByQuery(@Param("keyword") String keyword,
                      @Param("status") String status,
                      @Param("role") String role);

    /**
     * 切换用户状态并显式更新 {@code updated_at}。
     *
     * <p>用 {@link Update} 单独写一条 SQL 而非走 {@code BaseMapper.updateById}，
     * 是因为我们只想更新 {@code status} 与 {@code updated_at} 两列；通用更新会
     * 走整个 entity 的字段填充逻辑。
     *
     * @param id        用户 id
     * @param status    新状态字符串
     * @param updatedAt 新 updated_at；可传 {@code null} 由 DB 触发器兜底
     * @return 受影响行数
     */
    @Update("""
            UPDATE "user"
               SET status = #{status},
                   updated_at = COALESCE(#{updatedAt}, NOW())
             WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("updatedAt") OffsetDateTime updatedAt);
}
