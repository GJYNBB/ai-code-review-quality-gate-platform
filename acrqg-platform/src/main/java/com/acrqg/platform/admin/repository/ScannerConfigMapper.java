package com.acrqg.platform.admin.repository;

import com.acrqg.platform.admin.domain.ScannerConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 静态扫描器配置 Mapper（B1-D.2）。
 *
 * <p>继承 {@link BaseMapper} 享受标准 CRUD。额外提供按业务键查询，便于
 * {@code AdminService.upsertScanner} 实现"按 name 存在则更新、不存在则插入"
 * 的语义。
 *
 * <p>Covers: R21.3。
 */
public interface ScannerConfigMapper extends BaseMapper<ScannerConfig> {

    /**
     * 按 name 唯一查询。
     *
     * @param name 扫描器名称
     * @return 命中的 {@link ScannerConfig}；未命中返回 {@code null}
     */
    @Select("SELECT id, name, language, enabled, command, result_parser_type, "
            + "       created_at, updated_at "
            + "  FROM scanner_config "
            + " WHERE name = #{name}")
    ScannerConfig selectByName(@Param("name") String name);
}
