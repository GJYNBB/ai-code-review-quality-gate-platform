package com.acrqg.platform.gate.repository;

import com.acrqg.platform.gate.domain.GateResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 门禁判定结果 Mapper（B3-F.2）。
 *
 * <p>继承 {@link BaseMapper} 享受标准 CRUD；额外提供：
 * <ul>
 *   <li>{@link #findByTaskId} —— 一对一映射查询，依赖
 *       {@code uk_gate_result_task} 唯一索引保证至多一行；</li>
 *   <li>{@link #upsertByTaskId} —— PostgreSQL {@code ON CONFLICT (task_id) DO UPDATE}
 *       原生 upsert：B3-F GateRuleEngine 在 retry / 重新评估场景下会重复写入同一
 *       task_id，必须用 upsert 兼顾"首次插入"与"覆盖更新"两种语义；</li>
 *   <li>{@link #updateStatusByTaskId} —— B4-E 豁免审批专用：仅切换 status
 *       字段为 {@code WAIVED}，不修改 score / summary。</li>
 * </ul>
 *
 * <p>{@code summary} 列在 SQL 中显式 cast 为 {@code JSONB}，保证 JDBC 端按字符串
 * 传参时 PostgreSQL 仍然接受。
 *
 * <p>Covers: R14.3, R14.4, R14.5, R14.8。
 */
public interface GateResultMapper extends BaseMapper<GateResult> {

    /**
     * 按 {@code task_id} 查找门禁判定结果（一对一映射）。
     *
     * @param taskId 任务主键
     * @return 命中返回结果；不存在返回 {@code null}
     */
    @Select("""
            SELECT id, task_id, status, score, ai_risk_score, ai_available,
                   summary::text AS summary, created_at, updated_at
              FROM gate_result
             WHERE task_id = #{taskId}
             LIMIT 1
            """)
    GateResult findByTaskId(@Param("taskId") Long taskId);

    /**
     * PostgreSQL upsert：按 {@code task_id} 插入或覆盖更新。
     *
     * <p>SQL：
     * <pre>
     * INSERT INTO gate_result(task_id, status, score, ai_risk_score, ai_available, summary)
     * VALUES (..., ?::JSONB)
     * ON CONFLICT (task_id) DO UPDATE
     *   SET status        = EXCLUDED.status,
     *       score         = EXCLUDED.score,
     *       ai_risk_score = EXCLUDED.ai_risk_score,
     *       ai_available  = EXCLUDED.ai_available,
     *       summary       = EXCLUDED.summary,
     *       updated_at    = NOW();
     * </pre>
     *
     * <p>不主动 SET {@code created_at}：插入时由 DB {@code DEFAULT NOW()} 兜底，
     * 后续覆盖更新保留首次写入时间（更易于追溯）。{@code updated_at} 上层有触发器
     * 维护，但 upsert 路径下显式 SET 一次更直观。
     *
     * @param taskId       任务主键，唯一
     * @param status       状态枚举名
     * @param score        质量评分（可空）
     * @param aiRiskScore  AI 风险分（可空）
     * @param aiAvailable  AI 可用性
     * @param summaryJson  {@link com.acrqg.platform.gate.dto.GateResultSummary} 序列化结果
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO gate_result
                (task_id, status, score, ai_risk_score, ai_available, summary)
            VALUES
                (#{taskId}, #{status}, #{score}, #{aiRiskScore}, #{aiAvailable}, #{summaryJson}::jsonb)
            ON CONFLICT (task_id) DO UPDATE
               SET status        = EXCLUDED.status,
                   score         = EXCLUDED.score,
                   ai_risk_score = EXCLUDED.ai_risk_score,
                   ai_available  = EXCLUDED.ai_available,
                   summary       = EXCLUDED.summary,
                   updated_at    = NOW()
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int upsertByTaskId(@Param("taskId") Long taskId,
                       @Param("status") String status,
                       @Param("score") Integer score,
                       @Param("aiRiskScore") Integer aiRiskScore,
                       @Param("aiAvailable") Boolean aiAvailable,
                       @Param("summaryJson") String summaryJson);

    /**
     * 仅切换状态字段（B4-E 豁免专用）。
     *
     * <p>不会重写 summary / score；{@code updated_at} 由触发器维护，但显式 SET
     * 也无害——仅记一次更新。
     *
     * @param taskId 任务主键
     * @param status 目标状态字符串
     * @return 受影响行数
     */
    @Update("UPDATE gate_result SET status = #{status}, updated_at = NOW() "
            + "WHERE task_id = #{taskId}")
    int updateStatusByTaskId(@Param("taskId") Long taskId,
                             @Param("status") String status);
}
