package com.acrqg.platform.code_issue.service.impl;

import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.code_issue.domain.CodeIssue;
import com.acrqg.platform.code_issue.domain.CodeIssueStatus;
import com.acrqg.platform.code_issue.domain.IssueComment;
import com.acrqg.platform.code_issue.domain.IssueHistory;
import com.acrqg.platform.code_issue.domain.IssueStateMachine;
import com.acrqg.platform.code_issue.dto.CodeIssueDTO;
import com.acrqg.platform.code_issue.dto.IssueCommentDTO;
import com.acrqg.platform.code_issue.dto.IssueHistoryDTO;
import com.acrqg.platform.code_issue.dto.IssueQuery;
import com.acrqg.platform.code_issue.dto.IssueStatusChangeRequest;
import com.acrqg.platform.code_issue.repository.CodeIssueMapper;
import com.acrqg.platform.code_issue.repository.IssueCommentMapper;
import com.acrqg.platform.code_issue.repository.IssueHistoryMapper;
import com.acrqg.platform.code_issue.service.IssueService;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.api.FieldError;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.infra.permission.PermissionEvaluator;
import com.acrqg.platform.infra.permission.Role;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.acrqg.platform.project.repository.UserLookupMapper;
import com.acrqg.platform.task.domain.ReviewTask;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link IssueService} 默认实现（design.md §6.8 / R16.2-3, R17）。
 *
 * <h3>关键策略</h3>
 *
 * <ol>
 *   <li><b>权限</b>：所有方法在 service 层而非 controller 层校验，因为 projectId 必须
 *       通过 {@code issue → review_task} 关联实时解析。模式与 {@code ReviewTaskController.get}
 *       保持一致。{@code DEVELOPER} 用户在 R17.5 下"拒绝转换而非鉴权错误"——返回
 *       {@code VALIDATION_ERROR} 而不是 {@code PERMISSION_DENIED}。</li>
 *   <li><b>状态机</b>：调用 {@link IssueStateMachine#tryTransit} 校验合法性；
 *       FALSE_POSITIVE / CLOSED 强制 comment ≥ 5（R17.3，独立于状态机校验）。</li>
 *   <li><b>事务</b>：{@link #changeStatus} 与 {@link #addComment} 标记
 *       {@code @Transactional}，UPDATE + INSERT 同事务回滚。审计事件通过
 *       {@link ApplicationEventPublisher} 发布；事件监听器 {@code @Async} 异步落库，
 *       业务事务失败时审计不会写入（订阅方 {@code @TransactionalEventListener} 默认在
 *       {@code AFTER_COMMIT} 触发；本 implementation 与 ReviewTaskService 一致使用
 *       即时事件，由订阅方决定）。</li>
 *   <li><b>R17.6 立即生效</b>：状态从其他状态变更为 FALSE_POSITIVE 后，下一次
 *       {@code GateRuleEngine.evaluate} 通过 {@code CodeIssueMapper.countByTaskWithFilter}
 *       的 {@code statusNotIn=[FALSE_POSITIVE, CLOSED]} 谓词自然排除该问题。
 *       本服务不需额外清理缓存（B3-F MetricCollector 直接查 DB）。</li>
 * </ol>
 *
 * <p>Covers: R16.2, R16.3, R17.1-R17.6。
 */
@Service
public class IssueServiceImpl implements IssueService {

    private static final Logger log = LoggerFactory.getLogger(IssueServiceImpl.class);

    /** 审计资源类型 / action 字面量。 */
    private static final String RESOURCE_ISSUE = "code_issue";
    private static final String ACTION_STATUS_CHANGED = "ISSUE_STATUS_CHANGED";
    private static final String ACTION_COMMENT_ADDED = "ISSUE_COMMENT_ADDED";

    /** 评论长度边界（R16.3 任务约定 1..1000）。 */
    private static final int COMMENT_MIN_LENGTH = 1;
    private static final int COMMENT_MAX_LENGTH = 1000;

    /** R17.3：FALSE_POSITIVE / CLOSED 时 comment 最小有效长度（trim 后）。 */
    private static final int STATUS_COMMENT_MIN_LENGTH = 5;

    private final CodeIssueMapper codeIssueMapper;
    private final IssueHistoryMapper issueHistoryMapper;
    private final IssueCommentMapper issueCommentMapper;
    private final ReviewTaskMapper reviewTaskMapper;
    private final PermissionEvaluator permissionEvaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<UserLookupMapper> userLookupProvider;

    public IssueServiceImpl(CodeIssueMapper codeIssueMapper,
                            IssueHistoryMapper issueHistoryMapper,
                            IssueCommentMapper issueCommentMapper,
                            ReviewTaskMapper reviewTaskMapper,
                            PermissionEvaluator permissionEvaluator,
                            ApplicationEventPublisher eventPublisher,
                            ObjectProvider<UserLookupMapper> userLookupProvider) {
        this.codeIssueMapper = codeIssueMapper;
        this.issueHistoryMapper = issueHistoryMapper;
        this.issueCommentMapper = issueCommentMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.permissionEvaluator = permissionEvaluator;
        this.eventPublisher = eventPublisher;
        this.userLookupProvider = userLookupProvider;
    }

    // =====================================================================
    // page
    // =====================================================================

    @Override
    public PageResult<CodeIssueDTO> page(Long taskId, IssueQuery query) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "taskId 不能为空");
        }
        ReviewTask task = requireTask(taskId);
        ensureProjectMember(task.getProjectId());

        IssueQuery q = (query == null)
                ? new IssueQuery(null, null, null, null, null, 1, 20)
                : query;
        int page = q.safePage();
        int pageSize = q.safePageSize();
        int offset = (page - 1) * pageSize;

        List<String> severityIn = nullableUpperCaseList(q.severity());
        List<String> statusIn = nullableUpperCaseList(q.status());
        String source = nullableUpperCase(q.source());
        String filePath = trimToNull(q.filePath());
        String keyword = trimToNull(q.keyword());

        long total = codeIssueMapper.countByTaskWithMultiFilter(
                taskId, severityIn, statusIn, source, filePath, keyword);
        List<CodeIssueDTO> items;
        if (total == 0) {
            items = Collections.emptyList();
        } else {
            List<CodeIssue> rows = codeIssueMapper.pageByTaskWithFilter(
                    taskId, severityIn, statusIn, source, filePath, keyword,
                    pageSize, offset);
            items = new ArrayList<>(rows.size());
            for (CodeIssue r : rows) {
                items.add(toBasicDTO(r));
            }
        }
        return PageResult.of(items, page, pageSize, total);
    }

    // =====================================================================
    // get
    // =====================================================================

    @Override
    public CodeIssueDTO get(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "id 不能为空");
        }
        CodeIssue issue = codeIssueMapper.selectById(id);
        if (issue == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "问题不存在: id=" + id);
        }
        ReviewTask task = requireTask(issue.getTaskId());
        ensureProjectMember(task.getProjectId());

        List<IssueHistory> historyRows = issueHistoryMapper.listByIssue(id);
        List<IssueComment> commentRows = issueCommentMapper.listByIssue(id);

        // 收集 operator_id 一次查 username（避免 N+1）
        Set<Long> operatorIds = new LinkedHashSet<>();
        for (IssueHistory h : historyRows) {
            if (h.getOperatorId() != null) {
                operatorIds.add(h.getOperatorId());
            }
        }
        for (IssueComment c : commentRows) {
            if (c.getOperatorId() != null) {
                operatorIds.add(c.getOperatorId());
            }
        }
        Map<Long, String> usernameByOperator = resolveUsernames(operatorIds);

        List<IssueHistoryDTO> historyDtos = new ArrayList<>(historyRows.size());
        for (IssueHistory h : historyRows) {
            historyDtos.add(new IssueHistoryDTO(
                    h.getId(),
                    h.getCodeIssueId(),
                    h.getFromStatus(),
                    h.getToStatus(),
                    h.getComment(),
                    h.getOperatorId(),
                    h.getOperatorId() == null ? null : usernameByOperator.get(h.getOperatorId()),
                    h.getChangedAt()
            ));
        }
        List<IssueCommentDTO> commentDtos = new ArrayList<>(commentRows.size());
        for (IssueComment c : commentRows) {
            commentDtos.add(new IssueCommentDTO(
                    c.getId(),
                    c.getCodeIssueId(),
                    c.getContent(),
                    c.getOperatorId(),
                    c.getOperatorId() == null ? null : usernameByOperator.get(c.getOperatorId()),
                    c.getCreatedAt()
            ));
        }

        return CodeIssueDTO.withDetails(
                issue.getId(),
                issue.getTaskId(),
                issue.getFilePath(),
                issue.getLineNo(),
                issue.getRuleCode(),
                issue.getSource(),
                issue.getSeverity(),
                issue.getStatus(),
                issue.getDescription(),
                issue.getSuggestion(),
                issue.getConfidence(),
                issue.getCreatedAt(),
                issue.getUpdatedAt(),
                historyDtos,
                commentDtos
        );
    }

    // =====================================================================
    // changeStatus
    // =====================================================================

    @Override
    @Transactional
    public void changeStatus(Long id, IssueStatusChangeRequest req) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "id 不能为空");
        }
        if (req == null || req.status() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status 不能为空");
        }
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        CodeIssue issue = codeIssueMapper.selectById(id);
        if (issue == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "问题不存在: id=" + id);
        }
        ReviewTask task = requireTask(issue.getTaskId());

        // 1) 项目成员校验
        if (!permissionEvaluator.isProjectMember(caller.id(), task.getProjectId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }

        // 2) R17.5：仅 DEVELOPER 角色（无更高全局角色）只能操作自己创建任务的 issue
        //    REVIEWER / PROJECT_ADMIN / SYSTEM_ADMIN 不受此限制
        if (isPureDeveloper(caller)
                && (task.getCreatedBy() == null || !task.getCreatedBy().equals(caller.id()))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "developer can only operate own task issues");
        }

        // 3) 状态机合法性校验
        CodeIssueStatus from = parseStatusOrFail(issue.getStatus());
        CodeIssueStatus to = req.status();
        IssueStateMachine.tryTransit(from, to);

        // 4) R17.3：FALSE_POSITIVE / CLOSED 强制 comment.trim().length() ≥ 5
        if (to == CodeIssueStatus.FALSE_POSITIVE || to == CodeIssueStatus.CLOSED) {
            String trimmed = req.comment() == null ? "" : req.comment().trim();
            if (trimmed.length() < STATUS_COMMENT_MIN_LENGTH) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "comment required for FALSE_POSITIVE/CLOSED",
                        List.of(new FieldError("comment",
                                "comment required for FALSE_POSITIVE/CLOSED")));
            }
        }

        // 5) CAS 更新 status；冲突抛 VALIDATION_ERROR（被并发抢占视为非法迁移）
        int affected = codeIssueMapper.updateStatusOnlyIfFrom(id, from.name(), to.name());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "status changed concurrently or illegal transition");
        }

        // 6) 写 issue_history
        IssueHistory history = new IssueHistory();
        history.setCodeIssueId(id);
        history.setFromStatus(from.name());
        history.setToStatus(to.name());
        history.setComment(req.comment());
        history.setOperatorId(caller.id());
        // changed_at 由 DB DEFAULT NOW() 兜底；显式不设置 / 由 DB 维护
        issueHistoryMapper.insert(history);

        // 7) 发布审计
        Map<String, Object> detail = detailOf(
                "issueId", id,
                "taskId", issue.getTaskId(),
                "projectId", task.getProjectId(),
                "from", from.name(),
                "to", to.name(),
                "comment", req.comment());
        publishAudit(caller, ACTION_STATUS_CHANGED, RESOURCE_ISSUE,
                String.valueOf(id), detail);

        log.debug("issue status changed: id={} {}->{} operator={}", id, from, to, caller.id());
    }

    // =====================================================================
    // addComment
    // =====================================================================

    @Override
    @Transactional
    public void addComment(Long id, String content) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "id 不能为空");
        }
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.length() < COMMENT_MIN_LENGTH || trimmed.length() > COMMENT_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "评论内容长度需在 1..1000 字符之间",
                    List.of(new FieldError("content", "评论内容长度需在 1..1000 字符之间")));
        }
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        CodeIssue issue = codeIssueMapper.selectById(id);
        if (issue == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "问题不存在: id=" + id);
        }
        ReviewTask task = requireTask(issue.getTaskId());
        if (!permissionEvaluator.isProjectMember(caller.id(), task.getProjectId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }

        IssueComment comment = new IssueComment();
        comment.setCodeIssueId(id);
        comment.setContent(content);
        comment.setOperatorId(caller.id());
        // created_at 由 DB DEFAULT NOW() 兜底
        issueCommentMapper.insert(comment);

        publishAudit(caller, ACTION_COMMENT_ADDED, RESOURCE_ISSUE,
                String.valueOf(id),
                detailOf(
                        "issueId", id,
                        "taskId", issue.getTaskId(),
                        "projectId", task.getProjectId(),
                        "commentId", comment.getId()));
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private ReviewTask requireTask(Long taskId) {
        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND,
                    "任务不存在: id=" + taskId);
        }
        return task;
    }

    private void ensureProjectMember(Long projectId) {
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();
        if (!permissionEvaluator.isProjectMember(caller.id(), projectId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }
    }

    /**
     * 是否仅持有 DEVELOPER 全局角色（不含 REVIEWER / PROJECT_ADMIN / SYSTEM_ADMIN）。
     *
     * <p>R17.5 仅对"纯 DEVELOPER"用户生效；同时持有 REVIEWER / PROJECT_ADMIN /
     * SYSTEM_ADMIN 等更高角色的用户可以跨任务流转。
     */
    private static boolean isPureDeveloper(AuthenticatedUser caller) {
        if (caller == null || caller.roles() == null || caller.roles().isEmpty()) {
            return false;
        }
        boolean hasDeveloper = caller.hasRole(Role.DEVELOPER.code());
        if (!hasDeveloper) {
            return false;
        }
        return !caller.hasRole(Role.REVIEWER.code())
                && !caller.hasRole(Role.PROJECT_ADMIN.code())
                && !caller.hasRole(Role.SYSTEM_ADMIN.code());
    }

    private static CodeIssueStatus parseStatusOrFail(String s) {
        if (s == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "status 字段为空");
        }
        try {
            return CodeIssueStatus.valueOf(s);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "未知问题状态: " + s, ex);
        }
    }

    /**
     * 把字符串列表去 null / 去空白 / 去重后转大写；空集合返回 {@code null}（让 Mapper 跳过该谓词）。
     */
    private static List<String> nullableUpperCaseList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Set<String> uniq = new LinkedHashSet<>(raw.size());
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (!t.isEmpty()) {
                uniq.add(t.toUpperCase());
            }
        }
        return uniq.isEmpty() ? null : new ArrayList<>(uniq);
    }

    private static String nullableUpperCase(String s) {
        String t = trimToNull(s);
        return t == null ? null : t.toUpperCase();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Map<Long, String> resolveUsernames(Set<Long> operatorIds) {
        if (operatorIds == null || operatorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        UserLookupMapper mapper = userLookupProvider.getIfAvailable();
        if (mapper == null) {
            // 测试 / 精简启动场景：返回空 map，DTO 中 username 为 null 即可
            return Collections.emptyMap();
        }
        Map<Long, String> result = new HashMap<>(operatorIds.size());
        Set<Long> resolved = new HashSet<>(operatorIds.size());
        for (Long uid : operatorIds) {
            if (uid == null || resolved.contains(uid)) {
                continue;
            }
            String username = mapper.findUsernameById(uid);
            result.put(uid, username);
            resolved.add(uid);
        }
        return result;
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

    private static CodeIssueDTO toBasicDTO(CodeIssue r) {
        return CodeIssueDTO.basic(
                r.getId(),
                r.getTaskId(),
                r.getFilePath(),
                r.getLineNo(),
                r.getRuleCode(),
                r.getSource(),
                r.getSeverity(),
                r.getStatus(),
                r.getDescription(),
                r.getSuggestion(),
                r.getConfidence(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
