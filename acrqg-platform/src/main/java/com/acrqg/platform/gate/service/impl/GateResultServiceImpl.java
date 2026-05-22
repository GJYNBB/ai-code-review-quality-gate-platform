package com.acrqg.platform.gate.service.impl;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.common.util.JsonUtils;
import com.acrqg.platform.gate.domain.GateResult;
import com.acrqg.platform.gate.dto.GateResultDTO;
import com.acrqg.platform.gate.dto.GateResultSummary;
import com.acrqg.platform.gate.repository.GateResultMapper;
import com.acrqg.platform.gate.service.GateResultService;
import com.acrqg.platform.infra.permission.PermissionEvaluator;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * {@link GateResultService} 默认实现（B3-F.6）。
 *
 * <p>查询路径：
 * <ol>
 *   <li>校验任务存在；不存在抛 {@link ErrorCode#TASK_NOT_FOUND}；</li>
 *   <li>校验当前用户是任务所属项目成员；不是则抛 {@link ErrorCode#PERMISSION_DENIED}；</li>
 *   <li>按 {@code task_id} 查询 {@code gate_result}；不存在抛
 *       {@link ErrorCode#TASK_NOT_FOUND} (message {@code "gate result not generated"})；</li>
 *   <li>反序列化 {@code summary} JSON 后返回 {@link GateResultDTO}。</li>
 * </ol>
 *
 * <p>Covers: R14.8, R16.1, R2.2。
 */
@Service
public class GateResultServiceImpl implements GateResultService {

    private static final Logger log = LoggerFactory.getLogger(GateResultServiceImpl.class);

    private final ReviewTaskMapper reviewTaskMapper;
    private final GateResultMapper gateResultMapper;
    private final PermissionEvaluator permissionEvaluator;

    public GateResultServiceImpl(ReviewTaskMapper reviewTaskMapper,
                                  GateResultMapper gateResultMapper,
                                  PermissionEvaluator permissionEvaluator) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.gateResultMapper = gateResultMapper;
        this.permissionEvaluator = permissionEvaluator;
    }

    @Override
    public GateResultDTO get(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "taskId 不能为空");
        }

        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND,
                    ErrorCode.TASK_NOT_FOUND.getMessage());
        }

        // 项目成员校验（R2.2）
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();
        if (!permissionEvaluator.isProjectMember(caller.id(), task.getProjectId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }

        GateResult row = gateResultMapper.findByTaskId(taskId);
        if (row == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND,
                    "gate result not generated");
        }

        GateResultSummary summary = parseSummary(row);
        return new GateResultDTO(
                row.getId(),
                row.getTaskId(),
                row.getStatus(),
                row.getScore(),
                row.getAiRiskScore(),
                row.getAiAvailable() == null || row.getAiAvailable(),
                summary,
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private GateResultSummary parseSummary(GateResult row) {
        if (row.getSummary() == null || row.getSummary().isBlank()) {
            return GateResultSummary.empty(
                    row.getAiAvailable() == null || row.getAiAvailable());
        }
        try {
            return JsonUtils.fromJson(row.getSummary(),
                    new TypeReference<GateResultSummary>() {});
        } catch (RuntimeException ex) {
            log.warn("parse gate_result.summary failed: id={} err={}",
                    row.getId(), ex.toString());
            return GateResultSummary.empty(
                    row.getAiAvailable() == null || row.getAiAvailable());
        }
    }
}
