package com.acrqg.platform.gate.service;

import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.gate.dto.GateWaiverApprovalRequest;
import com.acrqg.platform.gate.dto.GateWaiverDTO;
import com.acrqg.platform.gate.dto.GateWaiverRequest;

/**
 * 门禁豁免审批服务（M07 / R15）。
 *
 * <p>对齐 design.md §6.7 与 §9 SD-5：申请 → 审批 → GateResult 转 WAIVED →
 * task 转 PASSED → Writeback 重新回写 success。
 *
 * <p>权限分层：
 * <ul>
 *   <li>{@link #apply}：项目成员均可调用（控制器 {@code projectMember=true}）；</li>
 *   <li>{@link #approve}：仅项目内 PROJECT_ADMIN 可调用；</li>
 *   <li>{@link #get} / {@link #pendingByProject}：项目成员均可读。</li>
 * </ul>
 *
 * <p>所有变更操作均发布 {@code AuditEvent}（R22.1）；
 * {@link #apply} 同时发布 {@code WaiverAppliedEvent}，
 * {@link #approve} 同时发布 {@code WaiverApprovedEvent}，由通知 / 回写模块订阅。
 *
 * <p>Covers: R15.1, R15.2, R15.3, R15.4, R15.5, R15.6, R20.6。
 */
public interface GateWaiverService {

    /**
     * 提交豁免申请（R15.1 / R15.2 / R15.6）。
     *
     * <p>校验：
     * <ul>
     *   <li>任务存在；</li>
     *   <li>任务状态必须是 {@code FAILED_GATE}，否则抛
     *       {@code BusinessException(VALIDATION_ERROR, "task not in FAILED_GATE")}；</li>
     *   <li>当前用户是任务所属项目的成员；</li>
     *   <li>同一任务无活跃 PENDING 申请，否则抛
     *       {@code BusinessException(WAIVER_DUPLICATED, "already pending waiver for this task")}（R15.6）。</li>
     * </ul>
     *
     * <p>成功后：插入 {@code gate_waiver}（status=PENDING） + 发布
     * {@code AuditEvent("WAIVER_APPLIED")} + 发布 {@code WaiverAppliedEvent}。
     *
     * @param taskId  任务主键
     * @param request 申请请求体
     * @return 申请视图
     */
    GateWaiverDTO apply(Long taskId, GateWaiverRequest request);

    /**
     * 审批豁免申请（R15.3 / R15.4 / R15.5）。
     *
     * <p>校验：
     * <ul>
     *   <li>申请存在；</li>
     *   <li>当前用户在申请所属项目内为 PROJECT_ADMIN（控制器层 {@code @RequirePermission} 已保证，
     *       Service 内再次防御性校验）；</li>
     *   <li>申请当前 status=PENDING，否则抛
     *       {@code BusinessException(VALIDATION_ERROR, "waiver not pending")}（CAS 兜底）。</li>
     * </ul>
     *
     * <p>成功路径：
     * <ul>
     *   <li>{@code approve=true} → status=APPROVED；同事务内把 review_task.status
     *       从 FAILED_GATE CAS 转 PASSED + 写 task_log(INFO)；</li>
     *   <li>{@code approve=false} → status=REJECTED；不修改任务状态。</li>
     * </ul>
     *
     * <p>事务提交后发布 {@code AuditEvent("WAIVER_APPROVED" / "WAIVER_REJECTED")} 与
     * {@code WaiverApprovedEvent}。
     *
     * @param waiverId 申请主键
     * @param request  审批请求体
     */
    void approve(Long waiverId, GateWaiverApprovalRequest request);

    /**
     * 主键查询单条豁免申请。
     *
     * <p>非项目成员调用抛 {@code BusinessException(PERMISSION_DENIED)}。
     */
    GateWaiverDTO get(Long waiverId);

    /**
     * 项目维度分页列出 PENDING 状态的豁免申请。
     *
     * <p>调用方应通过控制器层 {@code @RequirePermission(projectMember=true)} 限定
     * 仅项目成员可访问；本方法不再重复校验。
     *
     * @param projectId 项目主键
     * @param page      页码（从 1 起）
     * @param pageSize  每页条数（建议 20）
     * @return 分页结果
     */
    PageResult<GateWaiverDTO> pendingByProject(Long projectId, int page, int pageSize);
}
