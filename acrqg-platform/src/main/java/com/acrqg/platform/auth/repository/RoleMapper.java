package com.acrqg.platform.auth.repository;

import com.acrqg.platform.auth.domain.RoleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 角色 Mapper（B1-A.1）。
 *
 * <p>仅提供按 {@code code} 查询的能力，配合
 * {@link com.acrqg.platform.auth.repository.UserRoleMapper} 完成"按角色编码批量
 * 反查 id"的需求；其余 CRUD 由 {@link BaseMapper} 提供（业务上不会主动写入 / 删除）。
 *
 * <p>Covers: R3.4。
 */
public interface RoleMapper extends BaseMapper<RoleEntity> {

    /**
     * 按角色编码精确查询。
     *
     * @param code 角色编码（如 {@code SYSTEM_ADMIN}）
     * @return 命中返回 {@link RoleEntity}，否则 {@code null}
     */
    @Select("SELECT id, code, name, description FROM role WHERE code = #{code}")
    RoleEntity selectByCode(@Param("code") String code);

    /**
     * 按角色编码集合批量查询。
     *
     * <p>由 {@code UserService.create} 在写 {@code user_role} 关联表前批量
     * 反查角色 id；空集合时返回空列表（避免动态 SQL 中的 IN () 语法错误）。
     *
     * @param codes 角色编码集合
     * @return 与入参编码一一对应的角色列表（顺序未必一致；调用方应自行按编码 map）
     */
    @Select("""
            <script>
            SELECT id, code, name, description
              FROM role
             WHERE code IN
               <foreach collection='codes' item='c' open='(' separator=',' close=')'>
                 #{c}
               </foreach>
            </script>
            """)
    List<RoleEntity> selectByCodes(@Param("codes") Collection<String> codes);
}
