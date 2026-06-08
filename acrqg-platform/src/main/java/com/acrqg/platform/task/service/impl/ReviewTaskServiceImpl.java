package com.acrqg.platform.task.service.impl;

import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.common.util.JsonUtils;
import com.acrqg.platform.infra.permission.PermissionEvaluator;
import com.acrqg.platform.infra.permission.ProjectRole;
import com.acrqg.platform.infra.redis.IdempotencyStore;
import com.acrqg.platform.infra.redis.RedisStreamPublisher;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.domain.ReviewTaskStatus;
import com.acrqg.platform.task.domain.StateMachine;
import com.acrqg.platform.task.domain.TaskLog;
import com.acrqg.platform.task.domain.TriggerType;
import com.acrqg.platform.task.dto.CancelRequest;
import com.acrqg.platform.task.dto.ReviewTaskCreateRequest;
import com.acrqg.platform.task.dto.ReviewTaskDTO;
import com.acrqg.platform.task.dto.ReviewTaskQuery;
import com.acrqg.platform.task.dto.RetryRequest;
import com.acrqg.platform.task.dto.TaskLogDTO;
import com.acrqg.platform.task.dto.TaskLogQuery;
import com.acrqg.platform.task.event.TaskFinishedEvent;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.acrqg.platform.task.repository.TaskLogMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link com.acrqg.platform.task.service.ReviewTaskService} 默认实现。
 *
 * <h3>关键策略</h3>
 *
 * <ol>
 *   <li><b>任务编号 task_no</b>：格式 {@code RT{yyyyMMdd}{4 位序列}}，
 *       通过 Redis {@code INCR task:no:seq:{date}} 获取当日序列；首次自增
 *       （返回 1）时把 key 设置为 2 天 TTL，避免无限增长。日切后序列自然重置为 1。</li>
 *   <li><b>幂等</b>：双重防御（design.md §7.4）。请求头 {@code Idempotency-Key}
 *       命中 → 直接返回缓存的 taskId；否则按 {@code (projectId, prId, commitSha)}
 *       三元组查活跃任务，命中后按 trigger 决定返回还是抛 {@code TASK_DUPLICATED}。
 *       插入仍可能因极端并发触发 {@link DuplicateKeyException}，捕获后再次按三元组
 *       查询返回——保证最坏情况下也只会有 1 条活跃任务。</li>
 *   <li><b>触发来源</b>：webhook → {@code WEBHOOK}（不抛重复异常，直接幂等返回）；
 *       MANUAL/CI_CD → 没有 idempotencyKey 时遇到三元组冲突直接拒绝（R8.3）。</li>
 *   <li><b>事务</b>：{@link #create} 是 {@code @Transactional}：插入与计数变更
 *       同事务；{@link RedisStreamPublisher#enqueue} 在事务<b>提交后</b>调用
 *       （Spring 的标准约定：事务内调用 Redis 操作不会被回滚，但 Stream 入队
 *       本身具有 at-least-once 语义，由 worker 端的状态判断兜底）。本实现把
 *       enqueue 放在 {@link Transactional} 方法尾部即可——若整体失败则不会到达
 *       enqueue 行；若 enqueue 之后事务提交失败，worker 拿到的 taskId 找不到对应
 *       行会被忽略并 ACK。</li>
 *   <li><b>状态机迁移</b>：通过 {@code StateMachine.tryTransit} + Mapper CAS 双步
 *       完成。CAS 失败抛 {@code VALIDATION_ERROR} 让调用方决定是否重读后再试。</li>
 * </ol>
 *
 * <p>Covers: R7, R8, R9, R16.5, R22.1。
 */
@Service
public class ReviewTaskServiceImpl implements com.acrqg.platform.task.service.ReviewTaskService {

    private static final Logger log = LoggerFactory.getLogger(ReviewTaskServiceImpl.class);

    /** Redis Stream key（design.md §6.3.2）。 */
    public static final String STREAM_KEY = "review-task-stream";

    /** task_no 当日序列 key 前缀。 */
    private static final String TASK_NO_SEQ_KEY_PREFIX = "task:no:seq:";

    /** task_no 序列 key TTL：2 天（确保跨过日界仍能被新写入触发的过期回收）。 */
    private static final Duration TASK_NO_SEQ_TTL = Duration.ofDays(2);

    /** 业务时区（与 application.yml 中 spring.jackson.time-zone 对齐）。 */
    private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    /** 审计资源类型 / action 字面量。 */
    private static final String RESOURCE_TASK = "REVIEW_TASK";
    private static final String ACTION_TASK_CREATED = "REVIEW_TASK_CREATED";
    private static final String ACTION_TASK_RETRIED = "REVIEW_TASK_RETRIED";
    private static final String ACTION_TASK_CANCELLED = "REVIEW_TASK_CANCELLED";

    /** 终态可重试集合（R9.5）。 */
    private static final List<ReviewTaskStatus> RETRYABLE_FROM = List.of(
            ReviewTaskStatus.PASSED,
            ReviewTaskStatus.FAILED_GATE,
            ReviewTaskStatus.EXECUTION_FAILED);

    private final ReviewTaskMapper reviewTaskMapper;
    private final TaskLogMapper taskLogMapper;
    private final IdempotencyStore idempotencyStore;
    private final RedisStreamPublisher redisStreamPublisher;
    private final StringRedisTemplate stringRedisTemplate;
    private final PermissionEvaluator permissionEvaluator;
    private final ApplicationEventPublisher eventPublisher;

    public ReviewTaskServiceImpl(ReviewTaskMapper reviewTaskMapper,
                                 TaskLogMapper taskLogMapper,
                                 IdempotencyStore idempotencyStore,
                                 RedisStreamPublisher redisStreamPublisher,
                                 StringRedisTemplate stringRedisTemplate,
                                 PermissionEvaluator permissionEvaluator,
                                 ApplicationEventPublisher eventPublisher) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.taskLogMapper = taskLogMapper;
        this.idempotencyStore = idempotencyStore;
        this.redisStreamPublisher = redisStreamPublisher;
        this.stringRedisTemplate = stringRedisTemplate;
        this.permissionEvaluator = permissionEvaluator;
        this.eventPublisher = eventPublisher;
    }

    // =====================================================================
    // create
    // =====================================================================

    @Override
    @Transactional
    public ReviewTaskDTO create(ReviewTaskCreateRequest request,
                                String idempotencyKey,
                                TriggerType trigger) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "request 不能为空");
        }
        if (trigger == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "triggerType 不能为空");
        }
        // R8.2：commitSha 与 prId 至少其一非空
        if (!request.isHasCommitOrPr()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "commitSha 与 prId 至少填写一项");
        }

        Long projectId = request.projectId();
        String prId = nullIfBlank(request.prId());
        String commitSha = nullIfBlank(request.commitSha());
        AuthenticatedUser caller = CurrentUserHolder.optional().orElse(null);

        // 公开 webhook 会在 WebhookService 通过绑定与签名校验得到 projectId；
        // 其他入口必须要求当前用户至少是项目成员，避免任意登录用户对任意项目创建任务。
        if (trigger != TriggerType.WEBHOOK) {
            if (caller == null || !permissionEvaluator.isProjectMember(caller.id(), projectId)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                        ErrorCode.PERMISSION_DENIED.getMessage());
            }
        }

        // 1) Idempotency-Key 命中：直接返回上一次的任务。
        // 手动 / CI_CD 幂等 key 必须按 userId + projectId 隔离，避免跨项目复用同一 header
        // 值时命中其他项目的缓存 taskId。Webhook 幂等由 WebhookService 使用 webhookKey 处理。
        String idemFullKey = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            if (trigger == TriggerType.WEBHOOK) {
                idemFullKey = IdempotencyStore.taskKey(idempotencyKey);
            } else {
                idemFullKey = IdempotencyStore.taskKey(caller.id(), projectId, idempotencyKey);
            }
            Optional<String> cached = idempotencyStore.get(idemFullKey);
            if (cached.isPresent() && !cached.get().isBlank()) {
                Long cachedTaskId = parseLongSafely(cached.get());
                if (cachedTaskId != null) {
                    ReviewTask hit = reviewTaskMapper.selectById(cachedTaskId);
                    if (hit != null) {
                        ensureCachedTaskVisible(hit, projectId, caller, trigger);
                        log.debug("create idempotency hit: key={} taskId={}", idemFullKey, cachedTaskId);
                        return toDTO(hit);
                    }
                    // 缓存指向已被清理的任务（极少见）；继续走正常路径
                }
            }
        }

        // 2) 三元组查活跃任务（commitSha 必须非 null 才能匹配——三元组要求）
        if (commitSha != null) {
            ReviewTask active = reviewTaskMapper.findActiveByTriple(projectId, prId, commitSha);
            if (active != null) {
                if (trigger == TriggerType.WEBHOOK) {
                    // R7.4 webhook 幂等：直接返回
                    log.debug("create triple hit (webhook): taskId={}", active.getId());
                    return toDTO(active);
                }
                // R8.3：手动 / CI_CD 重复触发拒绝
                throw new BusinessException(ErrorCode.TASK_DUPLICATED,
                        "评审任务重复，已存在 taskId=" + active.getId());
            }
        }
        // commitSha == null（仅有 prId）的情况：三元组 UNIQUE 约束允许 (projectId, prId, null) 多行；
        // 此时无法精确去重。Service 层不主动查；插入后若仍冲突，由 DB 唯一约束兜底。

        // 3) 生成 task_no 并插入
        String taskNo = generateTaskNo();

        Long createdBy = caller != null ? caller.id() : null;

        ReviewTask task = new ReviewTask();
        task.setTaskNo(taskNo);
        task.setProjectId(projectId);
        task.setPrId(prId);
        task.setSourceBranch(request.sourceBranch());
        task.setTargetBranch(request.targetBranch());
        task.setCommitSha(commitSha == null ? "" : commitSha);
        task.setStatus(ReviewTaskStatus.PENDING.name());
        task.setTriggerType(trigger.name());
        task.setAttempt(1);
        task.setAiAvailable(Boolean.TRUE);
        task.setCreatedBy(createdBy);

        try {
            reviewTaskMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            // 极端并发：另一线程在我们查 active 与 insert 之间插入了同三元组任务
            // 重新查询返回（保证幂等）
            log.debug("create DuplicateKeyException: re-fetching by triple");
            ReviewTask race = commitSha != null
                    ? reviewTaskMapper.findActiveByTriple(projectId, prId, commitSha)
                    : null;
            if (race != null) {
                if (trigger == TriggerType.WEBHOOK) {
                    return toDTO(race);
                }
                throw new BusinessException(ErrorCode.TASK_DUPLICATED,
                        "评审任务重复，已存在 taskId=" + race.getId(), ex);
            }
            // 真的找不到（task_no 冲突等极端情况）：抛回 VALIDATION_ERROR
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "创建评审任务失败：唯一约束冲突", ex);
        }

        // 4) 写 Idempotency-Key 缓存
        if (idemFullKey != null) {
            idempotencyStore.putIfAbsent(idemFullKey, String.valueOf(task.getId()));
        }

        // 5) 入队（at-least-once；worker 端按 status / id 兜底）
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("taskId", String.valueOf(task.getId()));
        fields.put("attempt", "1");
        try {
            redisStreamPublisher.enqueue(STREAM_KEY, fields);
        } catch (RuntimeException ex) {
            // Redis 临时不可用时仍允许任务以 PENDING 状态留在 DB，由
            // TaskRecoveryRunner 在下次启动时（或运维主动 re-enqueue）拉起；
            // 这里仅记 WARN，不让创建接口对调用方报错。
            log.warn("enqueue review-task-stream failed: taskId={} err={}",
                    task.getId(), ex.toString());
        }

        // 6) 审计
        publishAudit(caller, ACTION_TASK_CREATED, RESOURCE_TASK,
                String.valueOf(task.getId()),
                detailOf(
                        "taskId", task.getId(),
                        "taskNo", task.getTaskNo(),
                        "projectId", projectId,
                        "prId", prId,
                        "commitSha", commitSha,
                        "triggerType", trigger.name(),
                        "attempt", 1));

        return toDTO(task);
    }

    /**
     * 生成 task_no：{@code RT{yyyyMMdd}{4 位序列}}。
     *
     * <p>使用 {@code INCR} 原子计数；首次自增（返回 1）时设置 2 天 TTL。
     * 当 Redis 不可用时退化为基于 {@link System#nanoTime} 的兜底，避免阻塞任务创建。
     */
    String generateTaskNo() {
        String date = LocalDate.now(BIZ_ZONE).format(DATE_FMT);
        String key = TASK_NO_SEQ_KEY_PREFIX + date;
        long seq;
        try {
            Long incr = stringRedisTemplate.opsForValue().increment(key);
            seq = incr == null ? 0L : incr;
            if (seq == 1L) {
                stringRedisTemplate.expire(key, TASK_NO_SEQ_TTL);
            }
        } catch (RuntimeException ex) {
            // Redis 不可用 → 用本机 nanoTime 末 4 位兜底；DB uk_review_task_no 仍兜底冲突
            log.warn("task_no INCR failed, fallback to nanoTime: err={}", ex.toString());
            seq = (System.nanoTime() & 0xFFFF) % 10_000L;
        }
        // 截断到 4 位（10000 之外按模回绕）
        long fourDigit = seq % 10_000L;
        return String.format(Locale.ROOT, "RT%s%04d", date, fourDigit);
    }

    // =====================================================================
    // page / get / pageLogs
    // =====================================================================

    @Override
    public PageResult<ReviewTaskDTO> page(ReviewTaskQuery query) {
        ReviewTaskQuery q = (query == null)
                ? new ReviewTaskQuery(null, null, null, 1, 20)
                : query;
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        int page = q.safePage();
        int pageSize = q.safePageSize();
        int offset = (page - 1) * pageSize;

        // 项目集合：当查询指定 projectId，先校验成员；否则收敛为该用户参与的全部项目
        // 由于 ProjectMemberMapper 没有 listMyProjectIds 方法（M02 阶段未提供），
        // 这里采用"必须显式传 projectId"的契约：调用方未提供时返回空页。
        // 这与 design.md §16.5 的"先选项目再看任务"前端流程一致。
        List<Long> projectIds = new ArrayList<>(1);
        if (q.projectId() != null) {
            if (!permissionEvaluator.isProjectMember(caller.id(), q.projectId())) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                        ErrorCode.PERMISSION_DENIED.getMessage());
            }
            projectIds.add(q.projectId());
        }

        if (projectIds.isEmpty()) {
            return PageResult.of(Collections.emptyList(), page, pageSize, 0L);
        }

        long total = reviewTaskMapper.countProjects(projectIds, q.status(), q.triggerType(), q.projectId());
        List<ReviewTaskDTO> items;
        if (total == 0) {
            items = Collections.emptyList();
        } else {
            List<ReviewTask> rows = reviewTaskMapper.pageProjects(
                    projectIds, q.status(), q.triggerType(), q.projectId(), pageSize, offset);
            items = new ArrayList<>(rows.size());
            for (ReviewTask r : rows) {
                items.add(toDTO(r));
            }
        }
        return PageResult.of(items, page, pageSize, total);
    }

    @Override
    public ReviewTaskDTO get(Long id) {
        ReviewTask task = requireTaskById(id);
        ensureProjectMember(task.getProjectId());
        return toDTO(task);
    }

    @Override
    public PageResult<TaskLogDTO> pageLogs(Long taskId, TaskLogQuery query) {
        ReviewTask task = requireTaskById(taskId);
        ensureProjectMember(task.getProjectId());

        TaskLogQuery q = (query == null)
                ? new TaskLogQuery(null, null, 1, 50)
                : query;
        int page = q.safePage();
        int pageSize = q.safePageSize();
        int offset = (page - 1) * pageSize;

        long total = taskLogMapper.countByTaskAndFilters(taskId, q.stage(), q.level());
        List<TaskLogDTO> items;
        if (total == 0) {
            items = Collections.emptyList();
        } else {
            List<TaskLog> rows = taskLogMapper.selectByTaskAndFilters(
                    taskId, q.stage(), q.level(), pageSize, offset);
            items = new ArrayList<>(rows.size());
            for (TaskLog r : rows) {
                items.add(toLogDTO(r));
            }
        }
        return PageResult.of(items, page, pageSize, total);
    }

    // =====================================================================
    // retry / cancel
    // =====================================================================

    @Override
    @Transactional
    public ReviewTaskDTO retry(Long id, RetryRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "reason 不能为空");
        }
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();
        ReviewTask task = requireTaskById(id);

        // 权限：项目内 REVIEWER 或 PROJECT_ADMIN
        if (!permissionEvaluator.isProjectMember(caller.id(), task.getProjectId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }
        if (!permissionEvaluator.hasProjectRole(caller.id(), task.getProjectId(),
                new ProjectRole[]{ProjectRole.REVIEWER, ProjectRole.PROJECT_ADMIN})) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }

        // 状态：仅终态可重试
        ReviewTaskStatus current = parseStatusOrFail(task.getStatus());
        if (!RETRYABLE_FROM.contains(current)) {
            throw new BusinessException(ErrorCode.TASK_NOT_RETRYABLE,
                    "任务当前状态不可重试: " + current.name());
        }

        // CAS 把状态从当前终态置 PENDING；若并发另一终态也尝试 retry，按需迭代
        // 简单实现：基于本次读到的 current 做一次 CAS；失败则抛 VALIDATION_ERROR
        int affected = reviewTaskMapper.updateStatusOnlyIfFrom(
                id, current.name(), ReviewTaskStatus.PENDING.name());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "status changed concurrently or illegal transition");
        }
        // attempt + 1，重置 finished_at / started_at / score
        reviewTaskMapper.incrementAttempt(id);

        // 重新入队
        ReviewTask reloaded = reviewTaskMapper.selectById(id);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("taskId", String.valueOf(id));
        fields.put("attempt", String.valueOf(reloaded.getAttempt()));
        try {
            redisStreamPublisher.enqueue(STREAM_KEY, fields);
        } catch (RuntimeException ex) {
            log.warn("enqueue retry failed: taskId={} err={}", id, ex.toString());
        }

        publishAudit(caller, ACTION_TASK_RETRIED, RESOURCE_TASK, String.valueOf(id),
                detailOf(
                        "taskId", id,
                        "fromStatus", current.name(),
                        "attempt", reloaded.getAttempt(),
                        "reason", request.reason()));

        return toDTO(reloaded);
    }

    @Override
    @Transactional
    public ReviewTaskDTO cancel(Long id, CancelRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "reason 不能为空");
        }
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();
        ReviewTask task = requireTaskById(id);

        // 权限：项目内 PROJECT_ADMIN
        if (!permissionEvaluator.isProjectMember(caller.id(), task.getProjectId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }
        if (!permissionEvaluator.hasProjectRole(caller.id(), task.getProjectId(),
                new ProjectRole[]{ProjectRole.PROJECT_ADMIN})) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }

        // 状态：仅 PENDING 允许取消（R9.6）
        ReviewTaskStatus current = parseStatusOrFail(task.getStatus());
        if (current != ReviewTaskStatus.PENDING) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "仅 PENDING 状态可取消，当前: " + current.name());
        }

        int affected = reviewTaskMapper.updateStatusOnlyIfFrom(
                id, ReviewTaskStatus.PENDING.name(), ReviewTaskStatus.EXECUTION_FAILED.name());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "status changed concurrently or illegal transition");
        }
        // 写 finished_at（cancel 视为终态完成）
        reviewTaskMapper.setFinishedAt(id, null, null, null);

        // 写 task_log（WARN）
        TaskLog cancelLog = new TaskLog();
        cancelLog.setTaskId(id);
        cancelLog.setStage("SYSTEM");
        cancelLog.setLevel("WARN");
        cancelLog.setMessage("cancelled by user: " + request.reason());
        cancelLog.setDetailJson(JsonUtils.toJson(detailOf(
                "operatorId", caller.id(),
                "operatorUsername", caller.username(),
                "reason", request.reason())));
        taskLogMapper.insertLog(cancelLog);

        publishAudit(caller, ACTION_TASK_CANCELLED, RESOURCE_TASK, String.valueOf(id),
                detailOf(
                        "taskId", id,
                        "reason", request.reason()));

        ReviewTask reloaded = reviewTaskMapper.selectById(id);
        return toDTO(reloaded);
    }

    // =====================================================================
    // transitTo（状态机入口；供 TaskOrchestrator 调用）
    // =====================================================================

    @Override
    public void transitTo(Long id, ReviewTaskStatus target) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "id 不能为空");
        }
        if (target == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "target 不能为空");
        }
        ReviewTask task = reviewTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND,
                    ErrorCode.TASK_NOT_FOUND.getMessage());
        }
        ReviewTaskStatus current = parseStatusOrFail(task.getStatus());

        // 合法性校验
        StateMachine.tryTransit(current, target);

        // CAS 更新
        int affected = reviewTaskMapper.updateStatusOnlyIfFrom(id, current.name(), target.name());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "status changed concurrently or illegal transition");
        }

        // 终态发布 TaskFinishedEvent（B4-D 通知 / B4-E 回写订阅）
        if (target.isTerminal()) {
            Long triggerUserId = CurrentUserHolder.optional()
                    .map(AuthenticatedUser::id)
                    .orElse(null);
            eventPublisher.publishEvent(new TaskFinishedEvent(
                    id,
                    task.getProjectId(),
                    target,
                    triggerUserId,
                    task.getCreatedBy()));
        }
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private ReviewTask requireTaskById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "id 不能为空");
        }
        ReviewTask task = reviewTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND,
                    ErrorCode.TASK_NOT_FOUND.getMessage());
        }
        return task;
    }

    /** 校验当前用户是项目成员；非成员抛 PERMISSION_DENIED。 */
    private void ensureProjectMember(Long projectId) {
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();
        if (!permissionEvaluator.isProjectMember(caller.id(), projectId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }
    }

    private void ensureCachedTaskVisible(ReviewTask hit,
                                         Long requestProjectId,
                                         AuthenticatedUser caller,
                                         TriggerType trigger) {
        if (hit.getProjectId() == null || !hit.getProjectId().equals(requestProjectId)) {
            throw new BusinessException(ErrorCode.TASK_DUPLICATED,
                    "Idempotency-Key 已被其他请求使用");
        }
        if (trigger != TriggerType.WEBHOOK) {
            if (caller == null || !permissionEvaluator.isProjectMember(caller.id(), hit.getProjectId())) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                        ErrorCode.PERMISSION_DENIED.getMessage());
            }
        }
    }

    private static ReviewTaskStatus parseStatusOrFail(String s) {
        if (s == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "status 字段为空");
        }
        try {
            return ReviewTaskStatus.valueOf(s);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "未知状态: " + s, ex);
        }
    }

    private void publishAudit(AuthenticatedUser caller, String action, String resourceType,
                              String resourceId, Map<String, Object> detail) {
        Long operatorId = caller != null ? caller.id() : null;
        String username = caller != null ? caller.username() : "SYSTEM";
        AuditEvent event = new AuditEvent(
                operatorId, username, action, resourceType, resourceId, null, detail);
        eventPublisher.publishEvent(event);
    }

    /** 构造保留插入顺序的 detail Map。可变长键值对参数。 */
    static Map<String, Object> detailOf(Object... kv) {
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

    private static String nullIfBlank(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Long parseLongSafely(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static ReviewTaskDTO toDTO(ReviewTask t) {
        return new ReviewTaskDTO(
                t.getId(),
                t.getTaskNo(),
                t.getProjectId(),
                t.getPrId(),
                t.getSourceBranch(),
                t.getTargetBranch(),
                t.getCommitSha(),
                t.getStatus(),
                t.getTriggerType(),
                t.getScore(),
                t.getAiRiskScore(),
                Boolean.TRUE.equals(t.getAiAvailable()),
                t.getAttempt() == null ? 1 : t.getAttempt(),
                t.getCreatedBy(),
                t.getStartedAt(),
                t.getFinishedAt(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    static TaskLogDTO toLogDTO(TaskLog l) {
        Map<String, Object> detail = null;
        if (l.getDetailJson() != null && !l.getDetailJson().isBlank()) {
            try {
                detail = JsonUtils.fromJson(l.getDetailJson(),
                        new TypeReference<Map<String, Object>>() {});
            } catch (RuntimeException ex) {
                log.warn("toLogDTO: failed to parse detail json (id={}): {}", l.getId(), ex.toString());
            }
        }
        return new TaskLogDTO(
                l.getId(),
                l.getTaskId(),
                l.getStage(),
                l.getLevel(),
                l.getMessage(),
                detail,
                l.getCreatedAt());
    }
}
