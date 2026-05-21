package com.acrqg.platform.task.service;

import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.domain.TriggerType;
import com.acrqg.platform.task.dto.CancelRequest;
import com.acrqg.platform.task.dto.ReviewTaskCreateRequest;
import com.acrqg.platform.task.dto.ReviewTaskDTO;
import com.acrqg.platform.task.dto.ReviewTaskQuery;
import com.acrqg.platform.task.dto.RetryRequest;
import com.acrqg.platform.task.dto.TaskLogDTO;
import com.acrqg.platform.task.dto.TaskLogQuery;

/**
 * 评审任务服务（M03 / R7~R9）。
 *
 * <p>对齐 design.md §6.3。承担：
 * <ol>
 *   <li>任务创建：双重幂等（{@code Idempotency-Key} 头 + {@code (projectId, prId, commitSha)}
 *       数据库唯一约束）；触发来源由调用方传入（webhook 走 WEBHOOK，REST 接口走 MANUAL/CI_CD）。</li>
 *   <li>状态机：通过 {@link #transitTo(Long, ReviewTaskStatus)} 暴露给
 *       {@code TaskOrchestrator}；CAS 失败时抛 {@code VALIDATION_ERROR}。</li>
 *   <li>列表 / 详情：基于项目成员关系做行级过滤。</li>
 *   <li>重试 / 取消：受角色与状态约束；写审计 + 重新入队。</li>
 * </ol>
 *
 * <p>所有变更操作发布 {@code AuditEvent}（R22.1）；
 * 所有抛出的业务异常通过
 * {@link com.acrqg.platform.common.exception.GlobalExceptionHandler} 统一映射。
 *
 * <p>Covers: R7.3, R7.4, R7.6, R8, R9, R16.5。
 */
public interface ReviewTaskService {

    /**
     * 创建评审任务。
     *
     * <p>双重幂等：
     * <ol>
     *   <li>若 {@code idempotencyKey} 非空：先查 Redis {@code idem:task:{key}}；
     *       命中则直接返回上一次创建的任务；否则执行创建并把 taskId 写入该 key
     *       （TTL=24h，R8.4）。</li>
     *   <li>无论是否有 idempotencyKey，再按 {@code (projectId, prId, commitSha)}
     *       三元组查活跃任务（{@code status != EXECUTION_FAILED}）；命中时：
     *       <ul>
     *         <li>{@code WEBHOOK} 触发 → 直接返回（R7.4）；</li>
     *         <li>{@code MANUAL} / {@code CI_CD} 触发且无 idempotencyKey
     *             → 抛 {@code TASK_DUPLICATED}（R8.3）。</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * <p>校验：{@link ReviewTaskCreateRequest#isHasCommitOrPr()} 已由 Bean Validation 兜底；
     * Service 层再次确认以避免反射调用旁路。
     *
     * <p>插入成功后通过 {@code RedisStreamPublisher} 入队 {@code review-task-stream}，
     * 并发布 {@code REVIEW_TASK_CREATED} 审计事件。
     *
     * @param request        创建请求
     * @param idempotencyKey {@code Idempotency-Key} 头（可空）
     * @param trigger        触发来源
     * @return 任务 DTO
     */
    ReviewTaskDTO create(ReviewTaskCreateRequest request, String idempotencyKey, TriggerType trigger);

    /**
     * 分页列出当前用户可见的评审任务。
     *
     * <p>列表过滤：仅返回当前用户参与项目下的任务（R2.2）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ReviewTaskDTO> page(ReviewTaskQuery query);

    /**
     * 主键查询任务详情。
     *
     * <p>非项目成员调用抛 {@code BusinessException(PERMISSION_DENIED)}（R2.2）；
     * 任务不存在抛 {@code TASK_NOT_FOUND}。
     */
    ReviewTaskDTO get(Long id);

    /**
     * 列出任务流水。
     *
     * <p>非项目成员调用抛 {@code BusinessException(PERMISSION_DENIED)}。
     */
    PageResult<TaskLogDTO> pageLogs(Long taskId, TaskLogQuery query);

    /**
     * 重试评审任务（R9.4）。
     *
     * <p>约束：
     * <ul>
     *   <li>权限：项目内 REVIEWER 或 PROJECT_ADMIN；其他角色抛
     *       {@code PERMISSION_DENIED}。</li>
     *   <li>状态：必须是 {@code PASSED} / {@code FAILED_GATE} / {@code EXECUTION_FAILED}；
     *       否则抛 {@code TASK_NOT_RETRYABLE}（R9.5）。</li>
     * </ul>
     *
     * <p>实现：CAS 把状态从当前终态跃迁到 {@code PENDING}；attempt+1；
     * 重新入队；写审计。
     */
    ReviewTaskDTO retry(Long id, RetryRequest request);

    /**
     * 取消评审任务（R9.6）。
     *
     * <p>约束：
     * <ul>
     *   <li>权限：项目内 PROJECT_ADMIN；否则 {@code PERMISSION_DENIED}。</li>
     *   <li>状态：仅 {@code PENDING} 允许取消；其他状态抛
     *       {@code BusinessException(VALIDATION_ERROR)}。</li>
     * </ul>
     *
     * <p>实现：CAS 把状态从 PENDING 转 EXECUTION_FAILED；写 {@code task_log}
     * （WARN 级别，message 包含原因）；写审计 {@code TASK_CANCELLED}。
     */
    ReviewTaskDTO cancel(Long id, CancelRequest request);

    /**
     * 状态机迁移入口。
     *
     * <p>由 {@code TaskOrchestrator} 调用。先经
     * {@code StateMachine.tryTransit} 校验合法性；再通过
     * {@code ReviewTaskMapper.updateStatusOnlyIfFrom} 做 CAS 更新；CAS 失败时
     * 抛 {@code BusinessException(VALIDATION_ERROR)}（"status changed concurrently or illegal transition"）。
     *
     * <p>本方法<b>不</b>校验项目成员关系——它是 Worker 内部的状态推进，
     * 不涉及外部访问控制。
     */
    void transitTo(Long id, ReviewTaskStatus target);
}
