package com.acrqg.platform.auth.repository;

import com.acrqg.platform.auth.domain.UserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户-角色关联 Mapper（B1-A.1）。
 *
 * <p>继承 {@link BaseMapper} 享受标准 INSERT；额外提供：
 * <ul>
 *   <li>{@link #selectRoleCodesByUserId(Long)} —— 一次查询获取用户的全部角色编码，
 *       供 Service 层在不需要完整 RoleEntity 时使用；</li>
 *   <li>{@link #deleteByUserId(Long)} —— 删除某用户的全部关联（暂未在 B1-A 使用，
 *       为将来用户删除场景预留）。</li>
 * </ul>
 *
 * <p>Covers: R2.1, R3.4。
 */
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /**
     * 按用户 id 查询其拥有的全部角色编码。
     *
     * <p>JOIN {@code role} 表是因为 {@code user_role} 仅存 {@code role_id}，
     * 而调用方需要的是字符串编码（与 JWT roles claim 一致）。
     *
     * @param userId 用户 id
     * @return 角色编码列表（可能为空）
     */
    @Select("""
            SELECT r.code
              FROM user_role ur
              JOIN role r ON r.id = ur.role_id
             WHERE ur.user_id = #{userId}
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 删除某用户的全部角色关联。
     *
     * @return 受影响行数
     */
    @Delete("DELETE FROM user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
