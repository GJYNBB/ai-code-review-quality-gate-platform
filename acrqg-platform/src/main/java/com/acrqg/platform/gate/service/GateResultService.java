package com.acrqg.platform.gate.service;

import com.acrqg.platform.gate.dto.GateResultDTO;

/**
 * 门禁判定结果查询服务（B3-F.6）。
 *
 * <p>对外暴露 {@code GET /review-tasks/{id}/gate-result} 的业务逻辑：
 * 校验任务存在 + 项目成员关系，然后返回 {@link GateResultDTO}。
 *
 * <p>{@link com.acrqg.platform.gate.engine.GateRuleEngine} 写入由 B3-F.5 触发，
 * 本服务仅做查询。
 *
 * <p>Covers: R14.8, R16.1, R2.2。
 */
public interface GateResultService {

    /**
     * 按任务主键查询门禁判定结果。
     *
     * <p>异常路径：
     * <ul>
     *   <li>任务不存在 → {@link com.acrqg.platform.common.exception.BusinessException}
     *       ({@link com.acrqg.platform.common.api.ErrorCode#TASK_NOT_FOUND})；</li>
     *   <li>当前用户不是任务所属项目的成员 → {@link com.acrqg.platform.common.exception.BusinessException}
     *       ({@link com.acrqg.platform.common.api.ErrorCode#PERMISSION_DENIED})；</li>
     *   <li>任务存在但 {@code gate_result} 尚未生成（任务未走完 GATE_EVALUATING 阶段）
     *       → {@link com.acrqg.platform.common.exception.BusinessException}
     *       ({@link com.acrqg.platform.common.api.ErrorCode#TASK_NOT_FOUND}, message
     *       {@code "gate result not generated"})。</li>
     * </ul>
     *
     * @param taskId 任务主键
     * @return 门禁判定结果 DTO
     */
    GateResultDTO get(Long taskId);
}
