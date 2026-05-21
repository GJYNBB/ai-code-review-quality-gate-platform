package com.acrqg.platform.gate.repository;

import com.acrqg.platform.gate.domain.QualityGate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 质量门禁版本 Mapper（B2-B.2）。
 *
 * <p>继承 {@link BaseMapper} 享受标准 CRUD（{@code insert} / {@code selectById}
 * / {@code selectList} 等）；额外提供：
 * <ul>
 *   <li>{@link #findEnabledByProject} —— 取启用版本（依赖部分唯一索引保证至多一条）；</li>
 *   <li>{@link #maxVersionByProject} —— 计算下一个版本号；</li>
 *   <li>{@link #disableEnabledByProject} —— 把当前 enabled 版本翻为 disabled，
 *       与新版本插入在同一事务内完成（R13.4）；</li>
 *   <li>{@link #listByProject} —— 列出项目所有历史版本（按版本号倒序）。</li>
 * </ul>
 *
 * <p>Covers: R13.1, R13.4。
 */
public interface QualityGateMapper extends BaseMapper<QualityGate> {

    /**
     * 取项目当前 enabled=TRUE 的版本（依赖
     * {@code uk_quality_gate_one_enabled} 部分唯一索引，至多一条）。
     *
     * @param projectId 项目主键
     * @return 命中返回门禁；不存在返回 {@code null}
     */
    @Select("""
            SELECT id, project_id, name, version, enabled, created_by, created_at
              FROM quality_gate
             WHERE project_id = #{projectId} AND enabled = TRUE
             LIMIT 1
            """)
    QualityGate findEnabledByProject(@Param("projectId") Long projectId);

    /**
     * 当前项目最大 version；不存在返回 {@code null}（由 Service 视为 0，新版本从 1 开始）。
     *
     * @param projectId 项目主键
     * @return 当前最大版本号；不存在返回 {@code null}
     */
    @Select("SELECT MAX(version) FROM quality_gate WHERE project_id = #{projectId}")
    Integer maxVersionByProject(@Param("projectId") Long projectId);

    /**
     * 把项目下当前 enabled=TRUE 的版本翻为 enabled=FALSE。
     *
     * <p>必须在事务内、新版本插入<b>之前</b>调用：先释放部分唯一索引
     * {@code uk_quality_gate_one_enabled} 的占位，新版本 INSERT 才不会冲突。
     *
     * @param projectId 项目主键
     * @return 影响行数（0 = 当前无启用版本；1 = 翻为 disabled 成功）
     */
    @Update("UPDATE quality_gate SET enabled = FALSE "
            + "WHERE project_id = #{projectId} AND enabled = TRUE")
    int disableEnabledByProject(@Param("projectId") Long projectId);

    /**
     * 列出项目所有版本（按 version 倒序）。
     *
     * @param projectId 项目主键
     * @return 版本列表，按 version DESC 排序
     */
    @Select("""
            SELECT id, project_id, name, version, enabled, created_by, created_at
              FROM quality_gate
             WHERE project_id = #{projectId}
             ORDER BY version DESC
            """)
    List<QualityGate> listByProject(@Param("projectId") Long projectId);
}
