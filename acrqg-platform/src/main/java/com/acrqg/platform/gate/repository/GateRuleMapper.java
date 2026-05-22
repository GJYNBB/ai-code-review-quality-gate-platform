package com.acrqg.platform.gate.repository;

import com.acrqg.platform.gate.domain.GateRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 门禁规则 Mapper（B2-B.2）。
 *
 * <p>继承 {@link BaseMapper} 享受标准 CRUD（含批量 {@code insert} 通过
 * {@code com.baomidou.mybatisplus.extension.toolkit.Db.saveBatch} 间接使用）；
 * 额外提供按 {@code gate_id} 列出规则。
 *
 * <p>Covers: R13.2。
 */
public interface GateRuleMapper extends BaseMapper<GateRule> {

    /**
     * 列出某个门禁版本下的所有规则。
     *
     * <p>按 {@code sort_order ASC, id ASC} 排序，保证前端按用户配置的顺序展示。
     *
     * @param gateId 门禁版本主键
     * @return 规则列表
     */
    @Select("""
            SELECT id, gate_id, metric, operator, threshold, severity, enabled, sort_order
              FROM gate_rule
             WHERE gate_id = #{gateId}
             ORDER BY sort_order ASC, id ASC
            """)
    List<GateRule> listByGate(@Param("gateId") Long gateId);
}
