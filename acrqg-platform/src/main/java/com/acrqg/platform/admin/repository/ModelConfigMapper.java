package com.acrqg.platform.admin.repository;

import com.acrqg.platform.admin.domain.ModelConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * AI 模型配置 Mapper（B1-D.2）。
 *
 * <p>继承 {@link BaseMapper} 享受标准 CRUD（{@code insert} / {@code updateById} /
 * {@code selectById} / {@code selectList} 等）。额外提供按业务键查询：
 * <ul>
 *   <li>{@link #selectByName(String)} —— 按 {@code name} 唯一查询。
 *       Service 层在 update 路径上偶尔需要按名称定位，避免要求调用方先查
 *       id 再 update。</li>
 * </ul>
 *
 * <p>Covers: R21.1, R21.2。
 */
public interface ModelConfigMapper extends BaseMapper<ModelConfig> {

    /**
     * 按 name 唯一查询。
     *
     * @param name 模型名称
     * @return 命中的 {@link ModelConfig}；未命中返回 {@code null}
     */
    @Select("SELECT id, name, base_url, api_key_encrypted, timeout_seconds, enabled, "
            + "       created_at, updated_at "
            + "  FROM model_config "
            + " WHERE name = #{name}")
    ModelConfig selectByName(@Param("name") String name);
}
