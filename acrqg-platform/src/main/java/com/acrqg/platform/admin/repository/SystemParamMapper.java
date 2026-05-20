package com.acrqg.platform.admin.repository;

import com.acrqg.platform.admin.domain.SystemParam;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统参数 Mapper（B1-D.2）。
 *
 * <p>继承 {@link BaseMapper} 享受标准 CRUD。额外提供：
 * <ul>
 *   <li>{@link #selectByKey(String)} —— 按 {@code param_key} 唯一查询；</li>
 *   <li>{@link #listByPrefix(String)} —— 按 key 前缀过滤的列表查询；
 *       {@code prefix} 为 {@code null}/空时退化为全表列表。</li>
 * </ul>
 *
 * <p>SQL 中使用 {@code ILIKE} 完成大小写不敏感匹配，并通过 {@code CONCAT}
 * 在 SQL 端拼接百分号，避免应用层做字符串拼接。
 *
 * <p>Covers: R21.4, R21.5。
 */
public interface SystemParamMapper extends BaseMapper<SystemParam> {

    /**
     * 按 {@code param_key} 唯一查询。
     *
     * @param paramKey 参数键
     * @return 命中的 {@link SystemParam}；未命中返回 {@code null}
     */
    @Select("SELECT id, param_key, param_value, description, sensitive, "
            + "       updated_by, updated_at "
            + "  FROM system_param "
            + " WHERE param_key = #{paramKey}")
    SystemParam selectByKey(@Param("paramKey") String paramKey);

    /**
     * 按 key 前缀列表查询，按 {@code param_key} 升序。
     *
     * @param prefix 前缀；{@code null}/空 表示全表
     * @return 列表
     */
    @Select("""
            <script>
            SELECT id, param_key, param_value, description, sensitive,
                   updated_by, updated_at
              FROM system_param
            <where>
              <if test='prefix != null and prefix != ""'>
                AND param_key ILIKE CONCAT(#{prefix}, '%')
              </if>
            </where>
             ORDER BY param_key ASC
            </script>
            """)
    List<SystemParam> listByPrefix(@Param("prefix") String prefix);
}
