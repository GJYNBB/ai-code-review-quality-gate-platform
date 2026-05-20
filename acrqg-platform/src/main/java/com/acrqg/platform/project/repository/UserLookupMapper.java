package com.acrqg.platform.project.repository;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 极简用户查询 Mapper，专供 {@code ProjectService} 在添加成员时校验
 * "userId 存在且启用"。
 *
 * <p>放在 {@code project.repository} 包内是有意为之：
 * <ul>
 *   <li>B1-C 阶段尚未引入完整的 User Domain / UserMapper（B1-A 范畴）；</li>
 *   <li>{@link com.acrqg.platform.project.service.ProjectService} 只需要"用户是否存在 +
 *       是否启用"两条信息，把它放入 user 模块会引入跨模块依赖；</li>
 *   <li>当 B1-A 提供完整 {@code UserMapper} 后，本接口可以删除并改为复用 B1-A 的
 *       Mapper（迁移成本低）。</li>
 * </ul>
 *
 * <p>注意：表名 {@code "user"} 是 PostgreSQL 保留字，必须使用双引号包裹。
 *
 * <p>Covers: R6.2（验证 userId 存在且 status=ENABLED）。
 */
public interface UserLookupMapper {

    /**
     * 查询用户状态。
     *
     * @param userId 用户主键
     * @return 状态字符串（{@code ENABLED} / {@code DISABLED}）；用户不存在时返回 {@code null}
     */
    @Select("SELECT status FROM \"user\" WHERE id = #{userId}")
    String findStatusById(@Param("userId") Long userId);

    /**
     * 查询用户名快照。
     *
     * @param userId 用户主键
     * @return 用户名；用户不存在返回 {@code null}
     */
    @Select("SELECT username FROM \"user\" WHERE id = #{userId}")
    String findUsernameById(@Param("userId") Long userId);
}
