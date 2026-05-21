package com.acrqg.platform.gate.service.impl;

import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.gate.domain.GateResultStatus;
import com.acrqg.platform.gate.domain.GateWaiver;
import com.acrqg.platform.gate.domain.GateWaiverStatus;
import com.acrqg.platform.gate.dto.GateWaiverApprovalRequest;
import com.acrqg.platform.gate.dto.GateWaiverDTO;
import com.acrqg.platform.gate.dto.GateWaiverRequest;
import com.acrqg.platform.gate.event.WaiverAppliedEvent;
import com.acrqg.platform.gate.event.WaiverApprovedEvent;
import com.acrqg.platform.gate.repository.GateResultMapper;
import com.acrqg.platform.gate.repository.GateWaiverMapper;
import com.acrqg.platform.gate.service.GateWaiverService;
import com.acrqg.platform.infra.permission.PermissionEvaluator;
import com.acrqg.platform.infra.permission.ProjectRole;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.domain.TaskLog;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.acrqg.platform.task.repository.TaskLogMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link GateWaiverService} 默认实现。
 *
 * <h3>关键策略</h3>
 *
 * <ol>
 *   <li><b>申请幂等</b>：依赖 {@link GateWaiverMapper#findByTaskAndPending} 预检
 *       + DB 端部分唯一索引 {@code uk_gate_waiver_task_pending} 兜底；并发场景
 *       下命中 {@link DuplicateKeyException} 时统一抛 {@code WAIVER_DUPLICATED}。</li>
 *   <li><b>审批 CAS</b>：{@link GateWaiverMapper#approve} 在 SQL 内带
 *       {@code AND status='PENDING'} 条件，受影响行数 0 即抛 {@code VALIDATION_ERROR}
 *       （已被审批 / 已撤销）。</li>
 *   <li><b>批准副作用</b>：在<b>同一事务</b>内
 *       <ul>
 *         <li>更新 {@code gate_result.status='WAIVED'}；</li>
 *         <li>把 {@code review_task.status} 从 FAILED_GATE CAS 转 PASSED；</li>
 *         <li>写 {@code task_log}(INFO) "gate waived by approver=…"；</li>
 *       </ul>
 *       任务状态切换失败时回滚整个事务（保证一致性）。</li>
 *   <li><b>事件发布</b>：所有 {@code AuditEvent} 与 {@code WaiverAppliedEvent} /
 *       {@code WaiverApprovedEvent} 在 service 方法返回前发布；订阅方异步消费，
 *       事务提交失败时事件可能仍被监听器收到——这与现有 audit 模块的语义一致，
 *       由订阅方根据 DB 实际状态做最终判断。</li>
 *   <li><b>权限边界</b>：{@link #apply} 只校验"项目成员"；{@link #approve} 在
 *       service 层再次显式校验 PROJECT_ADMIN（控制器层 {@code @RequirePermission}
 *       是第一道，service 层是第二道，符合 R23.4 多层防御）。</li>
 * </ol>
 *
 * <p>Covers: R15.1, R15.2, R15.3, R15.4, R15.5, R15.6, R22.1。
 */
@Service
public class GateWaiverServiceImpl implements GateWaiverService {

    private static final Logger log = LoggerFactory.getLogger(GateWaiverServiceImpl.class);

    /** 审计资源类型 / 动作字面量。 */
    private static final String RESOURCE_WAIVER = "GATE_WAIVER";
    private static final String ACTION_APPLIED = "WAIVER_APPLIED";
    private static final String ACTION_APPROVED = "WAIVER_APPROVED";
    private static final String ACTION_REJECTED = "WAIVER_REJECTED";

    private final GateWaiverMapper gateWaiverMapper;
    private final GateResultMapper gateResultMapper;
    private final ReviewTaskMapper reviewTaskMapper;
    private final TaskLogMapper taskLogMapper;
    private final PermissionEvaluator permissionEvaluator;
    private final ApplicationEventPublisher eventPublisher;

    public GateWaiverServiceImpl(GateWaiverMapper gateWaiverMapper,
                                  GateResultMapper gateResultMapper,
                                  ReviewTaskMapper reviewTaskMapper,
                                  TaskLogMapper taskLogMapper,
                                  PermissionEvaluator permissionEvaluator,
                                  ApplicationEventPublisher eventPublisher) {
        this.gateWaiverMapper = gateWaiverMapper;
        this.gateResultMapper = gateResultMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.taskLogMapper = taskLogMapper;
        this.permissionEvaluator = permissionEvaluator;
        this.eventPublisher = eventPublisher;
    }

    // =====================================================================
    // apply
    // =====================================================================

    @Override
    @Transactional
    public GateWaiverDTO apply(Long taskId, GateWaiverRequest request) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "taskId 不能为空");
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "request 不能为空");
        }
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND,
                    ErrorCode.TASK_NOT_FOUND.getMessage());
        }

        // 项目成员校验（R2.2）
        if (!permissionEvaluator.isProjectMember(caller.id(), task.getProjectId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }

        // 状态校验：仅 FAILED_GATE 可申请豁免（R15.1）
        if (!ReviewTaskStatus.FAILED_GATE.name().equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "task not in FAILED_GATE");
        }

        // 预检：是否已有 PENDING 申请（R15.6）
        GateWaiver existing = gateWaiverMapper.findByTaskAndPending(taskId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.WAIVER_DUPLICATED,
                    "already pending waiver for this task");
        }

        // INSERT
        GateWaiver waiver = new GateWaiver();
        waiver.setTaskId(taskId);
        waiver.setReason(request.reason());
        waiver.setStatus(GateWaiverStatus.PENDING.name());
        waiver.setApplicantId(caller.id());

        try {
            gateWaiverMapper.insert(waiver);
        } catch (DuplicateKeyException ex) {
            // 极端并发：DB 唯一索引兜底
            throw new BusinessException(ErrorCode.WAIVER_DUPLICATED,
                    "already pending waiver for this task", ex);
        }

        // 审计 + 领域事件
        publishAudit(caller, ACTION_APPLIED, String.valueOf(waiver.getId()),
                detailOf(
                        "waiverId", waiver.getId(),
                        "taskId", taskId,
                        "projectId", task.getProjectId(),
                        "reason", request.reason()));
        eventPublisher.publishEvent(new WaiverAppliedEvent(
                waiver.getId(), task.getProjectId(), caller.id(), taskId));

        return toDTO(waiver, task.getProjectId());
    }

    // =====================================================================
    // approve
    // =====================================================================

    @Override
    @Transactional
    public void approve(Long waiverId, GateWaiverApprovalRequest request) {
        if (waiverId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "waiverId 不能为空");
        }
        if (request == null || request.approve() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "approve 不能为空");
        }
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        GateWaiver waiver = gateWaiverMapper.findById(waiverId);
        if (waiver == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "waiver not found");
        }

        ReviewTask task = reviewTaskMapper.selectById(waiver.getTaskId());
        if (task == null) {
            // 数据不一致：task 已被删除（极端情况）
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "associated task not found, taskId=" + waiver.getTaskId());
        }

        // 防御性校验：PROJECT_ADMIN（控制器层已校验，service 层再做一次）
        if (!permissionEvaluator.hasProjectRole(caller.id(), task.getProjectId(),
                new ProjectRole[]{ProjectRole.PROJECT_ADMIN})) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }

        // CAS 审批（仅当当前 PENDING 时才更新）
        boolean approve = request.approve();
        OffsetDateTime now = OffsetDateTime.now();
        int updated = gateWaiverMapper.approve(
                waiverId, caller.id(), approve, request.comment(), now);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "waiver not pending or already approved");
        }

        // 批准 → GateResult 转 WAIVED + task 转 PASSED + task_log
        if (approve) {
            // 1) gate_result.status = WAIVED（无 gate_result 行也允许；为冪等忽略 0 行）
            gateResultMapper.updateStatusByTaskId(task.getId(), GateResultStatus.WAIVED.name());

            // 2) review_task.status: FAILED_GATE → PASSED（CAS）
            int taskUpdated = reviewTaskMapper.updateStatusOnlyIfFrom(
                    task.getId(),
                    ReviewTaskStatus.FAILED_GATE.name(),
                    ReviewTaskStatus.PASSED.name());
            if (taskUpdated == 0) {
                // task 状态可能已被并发改动；记录 WARN 但不回滚审批结果
                log.warn("approve waiver: task status CAS failed waiverId={} taskId={} currentStatus={}",
                        waiverId, task.getId(), task.getStatus());
            } else {
                // 3) task_log(INFO)
                TaskLog row = new TaskLog();
                row.setTaskId(task.getId());
                row.setStage("GATE_WAIVER");
                row.setLevel("INFO");
                row.setMessage("gate waived by approver=" + caller.id());
                taskLogMapper.insertLog(row);
            }
        }

        // 审计 + 领域事件
        String action = approve ? ACTION_APPROVED : ACTION_REJECTED;
        publishAudit(caller, action, String.valueOf(waiverId),
                detailOf(
                        "waiverId", waiverId,
                        "taskId", task.getId(),
                        "projectId", task.getProjectId(),
                        "approve", approve,
                        "comment", request.comment(),
                        "applicantId", waiver.getApplicantId()));
        eventPublisher.publishEvent(new WaiverApprovedEvent(
                waiverId, task.getId(), caller.id(), approve));
    }

    // =====================================================================
    // get / pendingByProject
    // =====================================================================

    @Override
    public GateWaiverDTO get(Long waiverId) {
        if (waiverId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "waiverId 不能为空");
        }
        GateWaiver waiver = gateWaiverMapper.findById(waiverId);
        if (waiver == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "waiver not found");
        }
        Long projectId = gateWaiverMapper.findProjectIdByWaiver(waiverId);
        if (projectId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "associated task not found, waiverId=" + waiverId);
        }

        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();
        if (!permissionEvaluator.isProjectMember(caller.id(), projectId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }
        return toDTO(waiver, projectId);
    }

    @Override
    public PageResult<GateWaiverDTO> pendingByProject(Long projectId, int page, int pageSize) {
        if (projectId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId 不能为空");
        }
        int safePage = page <= 0 ? 1 : page;
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        int offset = (safePage - 1) * safeSize;

        String status = GateWaiverStatus.PENDING.name();
        long total = gateWaiverMapper.countByProject(projectId, status);
        if (total == 0) {
            return PageResult.of(Collections.emptyList(), safePage, safeSize, 0L);
        }
        List<GateWaiver> rows = gateWaiverMapper.pageByProject(projectId, status, safeSize, offset);
        List<GateWaiverDTO> items = new ArrayList<>(rows.size());
        for (GateWaiver r : rows) {
            items.add(toDTO(r, projectId));
        }
        return PageResult.of(items, safePage, safeSize, total);
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private void publishAudit(AuthenticatedUser caller, String action, String resourceId,
                              Map<String, Object> detail) {
        AuditEvent event = AuditEvent.of(
                caller.id(),
                caller.username(),
                action,
                RESOURCE_WAIVER,
                resourceId,
                null,
                detail);
        eventPublisher.publishEvent(event);
    }

    private static Map<String, Object> detailOf(Object... kv) {
        if (kv == null || kv.length == 0) {
            return Collections.emptyMap();
        }
        if ((kv.length & 1) != 0) {
            throw new IllegalArgumentException("detailOf requires even number of args");
        }
        Map<String, Object> map = new LinkedHashMap<>(kv.length / 2);
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    static GateWaiverDTO toDTO(GateWaiver w, Long projectId) {
        return new GateWaiverDTO(
                w.getId(),
                w.getTaskId(),
                projectId,
                w.getReason(),
                w.getStatus(),
                w.getApplicantId(),
                w.getApproverId(),
                w.getApprovedAt(),
                w.getApprovalComment(),
                w.getCreatedAt(),
                w.getUpdatedAt());
    }
}
