package com.acrqg.platform.gate.repository;

import com.acrqg.platform.gate.domain.GateWaiver;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 门禁豁免申请 Mapper（B4-E.2）。
 *
 * <p>继承 {@link BaseMapper} 享受标准 CRUD（{@link BaseMapper#insert insert} /
 * {@link BaseMapper#selectById selectById}）；额外提供：
 * <ul>
 *   <li>{@link #findByTaskAndPending} —— 在 INSERT 之前预检"是否已有 PENDING 申请"，
 *       与 {@code uk_gate_waiver_task_pending} 唯一约束共同实现 R15.6 的幂等。</li>
 *   <li>{@link #approve} —— 审批操作的原子 SQL：仅当当前 status='PENDING' 时
 *       才把字段一次性更新为 APPROVED / REJECTED，避免双人同时审批的竞态。</li>
 *   <li>{@link #pageByProject} / {@link #countByProject} —— 项目维度分页列表，
 *       通过 {@code review_task} JOIN 反查 {@code project_id}。</li>
 * </ul>
 *
 * <p>Covers: R15.1, R15.3, R15.6, R20.6。
 */
public interface GateWaiverMapper extends BaseMapper<GateWaiver> {

    /**
     * 按 {@code task_id} 查找当前 PENDING 状态的豁免申请。
     *
     * @param taskId 任务主键
     * @return 命中返回豁免申请；不存在返回 {@code null}
     */
    @Select("""
            SELECT id, task_id, reason, status, applicant_id, approver_id,
                   approved_at, approval_comment, created_at, updated_at
              FROM gate_waiver
             WHERE task_id = #{taskId} AND status = 'PENDING'
             LIMIT 1
            """)
    GateWaiver findByTaskAndPending(@Param("taskId") Long taskId);

    /**
     * 主键查询完整字段（与 {@link BaseMapper#selectById} 等价但显式列出列名，
     * 避免 MyBatis-Plus 自动 SELECT * 在某些 @TableField(exist=false) 演进时
     * 的兼容性问题）。
     */
    @Select("""
            SELECT id, task_id, reason, status, applicant_id, approver_id,
                   approved_at, approval_comment, created_at, updated_at
              FROM gate_waiver
             WHERE id = #{id}
            """)
    GateWaiver findById(@Param("id") Long id);

    /**
     * 审批操作：仅当当前 status='PENDING' 时才更新；否则受影响行数为 0，
     * 由 Service 层抛 {@code VALIDATION_ERROR}（已被审批 / 已撤销）。
     *
     * @param id              主键
     * @param approverId      审批人用户主键
     * @param approve         {@code true} → 转 APPROVED；{@code false} → 转 REJECTED
     * @param comment         审批意见（可空）
     * @param approvedAt      审批时间
     * @return 受影响行数（0 / 1）
     */
    @Update("""
            UPDATE gate_waiver
               SET status           = CASE WHEN #{approve} THEN 'APPROVED' ELSE 'REJECTED' END,
                   approver_id      = #{approverId},
                   approval_comment = #{comment},
                   approved_at      = #{approvedAt},
                   updated_at       = NOW()
             WHERE id = #{id}
               AND status = 'PENDING'
            """)
    int approve(@Param("id") Long id,
                @Param("approverId") Long approverId,
                @Param("approve") boolean approve,
                @Param("comment") String comment,
                @Param("approvedAt") OffsetDateTime approvedAt);

    /**
     * 项目维度分页列表：JOIN review_task 取出 {@code project_id} 等于参数；
     * {@code status} 可选过滤（{@code null} 表示不过滤）。
     *
     * @param projectId 项目主键
     * @param status    状态过滤（{@code null} 表示不过滤）
     * @param limit     每页条数
     * @param offset    偏移
     * @return 当前页列表，按 {@code created_at DESC, id DESC} 排序
     */
    @Select("""
            <script>
            SELECT w.id, w.task_id, w.reason, w.status, w.applicant_id, w.approver_id,
                   w.approved_at, w.approval_comment, w.created_at, w.updated_at
              FROM gate_waiver w
              JOIN review_task t ON t.id = w.task_id
             WHERE t.project_id = #{projectId}
            <if test='status != null and status != ""'>
               AND w.status = #{status}
            </if>
             ORDER BY w.created_at DESC, w.id DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<GateWaiver> pageByProject(@Param("projectId") Long projectId,
                                   @Param("status") String status,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    /** 与 {@link #pageByProject} 同条件下的总条数。 */
    @Select("""
            <script>
            SELECT COUNT(1)
              FROM gate_waiver w
              JOIN review_task t ON t.id = w.task_id
             WHERE t.project_id = #{projectId}
            <if test='status != null and status != ""'>
               AND w.status = #{status}
            </if>
            </script>
            """)
    long countByProject(@Param("projectId") Long projectId,
                        @Param("status") String status);

    /**
     * 反查 waiver 所属项目主键（service 层做权限校验时使用）。
     *
     * <p>JOIN review_task 一次取回 (waiverId, taskId, projectId)；不存在返回 {@code null}。
     */
    @Select("""
            SELECT t.project_id
              FROM gate_waiver w
              JOIN review_task t ON t.id = w.task_id
             WHERE w.id = #{waiverId}
            """)
    Long findProjectIdByWaiver(@Param("waiverId") Long waiverId);
}
