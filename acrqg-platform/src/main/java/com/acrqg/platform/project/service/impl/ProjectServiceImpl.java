package com.acrqg.platform.project.service.impl;

import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.infra.permission.PermissionEvaluator;
import com.acrqg.platform.infra.permission.ProjectRole;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.acrqg.platform.project.domain.Project;
import com.acrqg.platform.project.domain.ProjectMember;
import com.acrqg.platform.project.dto.AddMemberRequest;
import com.acrqg.platform.project.dto.ProjectCreateRequest;
import com.acrqg.platform.project.dto.ProjectDTO;
import com.acrqg.platform.project.dto.ProjectMemberDTO;
import com.acrqg.platform.project.dto.ProjectQuery;
import com.acrqg.platform.project.dto.ProjectUpdateRequest;
import com.acrqg.platform.project.repository.ProjectListRow;
import com.acrqg.platform.project.repository.ProjectMapper;
import com.acrqg.platform.project.repository.ProjectMemberMapper;
import com.acrqg.platform.project.repository.ProjectMemberRow;
import com.acrqg.platform.project.repository.UserLookupMapper;
import com.acrqg.platform.project.service.ProjectService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ProjectService} 默认实现。
 *
 * <h3>关键策略</h3>
 *
 * <ol>
 *   <li><b>name 唯一</b>：依赖数据库 {@code uk_project_name} 唯一约束 +
 *       {@link DuplicateKeyException} 捕获，把冲突映射为
 *       {@link ErrorCode#PROJECT_NAME_EXISTS}（R4.2）。这种"先写后捕获"的写法
 *       优于"先 SELECT 再 INSERT"，无需双 SQL 也不需要悲观锁，且对并发更友好。</li>
 *   <li><b>事务边界</b>：{@link #create} / {@link #update} / {@link #addMember}
 *       / {@link #removeMember} 都是 {@code @Transactional}：
 *       <ul>
 *         <li>create 同事务内插 project + project_member（创建者自动成为
 *             {@code PROJECT_ADMIN}），保证一致性（R6.1）；</li>
 *         <li>{@link AuditEvent} 通过 {@link ApplicationEventPublisher} 在事务<b>提交后</b>
 *             才能被异步监听器消费（这是 Spring 的默认行为：异步 + 事务隔离），
 *             避免回滚后仍写出审计；</li>
 *       </ul>
 *   </li>
 *   <li><b>Caller 信息</b>：从 {@link CurrentUserHolder} 取当前操作者；
 *       {@link #update} 通过 {@link PermissionEvaluator#hasProjectRole} 校验
 *       PROJECT_ADMIN（R4.4）。该校验是 Service 级的<b>显式自检</b>（与
 *       Controller 的 {@code @RequirePermission} 互为兜底，便于将来在 CI/CD
 *       账户等没有项目角色的路径上即使绕开 Controller 也无法越权）。</li>
 *   <li><b>memberCount</b>：{@link ProjectMapper#pageList} / {@link ProjectMapper#selectWithMemberCount}
 *       通过子查询一次性返回，避免 N+1。</li>
 * </ol>
 *
 * <p>Covers: R4.1, R4.2, R4.3, R4.4, R4.5, R6.1, R6.2, R6.3, R6.4。
 */
@Service
public class ProjectServiceImpl implements ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceImpl.class);

    /** 审计资源类型字面量。 */
    private static final String RESOURCE_PROJECT = "PROJECT";
    private static final String RESOURCE_PROJECT_MEMBER = "PROJECT_MEMBER";

    /** 审计 action 字面量（与 ErrorCode / requirements 中文档保持大写蛇形）。 */
    private static final String ACTION_PROJECT_CREATED = "PROJECT_CREATED";
    private static final String ACTION_PROJECT_UPDATED = "PROJECT_UPDATED";
    private static final String ACTION_PROJECT_MEMBER_ADDED = "PROJECT_MEMBER_ADDED";
    private static final String ACTION_PROJECT_MEMBER_REMOVED = "PROJECT_MEMBER_REMOVED";

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserLookupMapper userLookupMapper;
    private final PermissionEvaluator permissionEvaluator;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectServiceImpl(ProjectMapper projectMapper,
                              ProjectMemberMapper projectMemberMapper,
                              UserLookupMapper userLookupMapper,
                              PermissionEvaluator permissionEvaluator,
                              ApplicationEventPublisher eventPublisher) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.userLookupMapper = userLookupMapper;
        this.permissionEvaluator = permissionEvaluator;
        this.eventPublisher = eventPublisher;
    }

    // ---------------------------------------------------------------------
    // create / page / get / update
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    public ProjectDTO create(ProjectCreateRequest request) {
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setDefaultBranch(request.defaultBranch());
        project.setLanguage(request.language());
        project.setCreatedBy(caller.id());

        try {
            projectMapper.insert(project);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.PROJECT_NAME_EXISTS,
                    ErrorCode.PROJECT_NAME_EXISTS.getMessage(), ex);
        }

        // 创建者自动成为 PROJECT_ADMIN（R6.1）
        ProjectMember adminMember = new ProjectMember();
        adminMember.setProjectId(project.getId());
        adminMember.setUserId(caller.id());
        adminMember.setProjectRole(ProjectRole.PROJECT_ADMIN.name());
        projectMemberMapper.insert(adminMember);

        publishAudit(caller, ACTION_PROJECT_CREATED, RESOURCE_PROJECT,
                String.valueOf(project.getId()),
                detailOf(
                        "projectId", project.getId(),
                        "name", project.getName(),
                        "language", project.getLanguage(),
                        "defaultBranch", project.getDefaultBranch()));

        ProjectListRow row = projectMapper.selectWithMemberCount(project.getId());
        return toDTO(row != null ? row : toListRow(project, 1));
    }

    @Override
    public PageResult<ProjectDTO> page(ProjectQuery query) {
        ProjectQuery q = query == null ? new ProjectQuery(null, 1, 20) : query;
        int page = q.safePage();
        int pageSize = q.safePageSize();
        int offset = (page - 1) * pageSize;
        String keyword = trimToNull(q.keyword());

        long total = projectMapper.countByKeyword(keyword);
        List<ProjectDTO> items;
        if (total == 0) {
            items = Collections.emptyList();
        } else {
            List<ProjectListRow> rows = projectMapper.pageList(keyword, pageSize, offset);
            items = new ArrayList<>(rows.size());
            for (ProjectListRow row : rows) {
                items.add(toDTO(row));
            }
        }
        return PageResult.of(items, page, pageSize, total);
    }

    @Override
    public ProjectDTO get(Long projectId) {
        ProjectListRow row = projectMapper.selectWithMemberCount(projectId);
        if (row == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目不存在");
        }
        return toDTO(row);
    }

    @Override
    @Transactional
    public ProjectDTO update(Long projectId, ProjectUpdateRequest request) {
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        Project existing = projectMapper.selectById(projectId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目不存在");
        }

        // 显式校验：调用者必须是该项目的 PROJECT_ADMIN（R4.4）；
        // 与 Controller 的 @RequirePermission 互为兜底。
        if (!permissionEvaluator.hasProjectRole(caller.id(), projectId,
                new ProjectRole[]{ProjectRole.PROJECT_ADMIN})) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    ErrorCode.PERMISSION_DENIED.getMessage());
        }

        boolean changed = false;
        Map<String, Object> diff = new LinkedHashMap<>();
        if (request.description() != null && !request.description().equals(existing.getDescription())) {
            diff.put("description", existing.getDescription() + " -> " + request.description());
            existing.setDescription(request.description());
            changed = true;
        }
        if (request.defaultBranch() != null && !request.defaultBranch().equals(existing.getDefaultBranch())) {
            diff.put("defaultBranch", existing.getDefaultBranch() + " -> " + request.defaultBranch());
            existing.setDefaultBranch(request.defaultBranch());
            changed = true;
        }
        if (request.language() != null && !request.language().equals(existing.getLanguage())) {
            diff.put("language", existing.getLanguage() + " -> " + request.language());
            existing.setLanguage(request.language());
            changed = true;
        }

        if (changed) {
            projectMapper.updateById(existing);
            publishAudit(caller, ACTION_PROJECT_UPDATED, RESOURCE_PROJECT,
                    String.valueOf(projectId),
                    detailOf("projectId", projectId, "diff", diff));
        }

        ProjectListRow row = projectMapper.selectWithMemberCount(projectId);
        return toDTO(row);
    }

    // ---------------------------------------------------------------------
    // members
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    public void addMember(Long projectId, AddMemberRequest request) {
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        // 项目必须存在
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目不存在");
        }

        Long userId = request.userId();
        ProjectRole role = request.role();

        // R6.2：userId 必须存在且 status=ENABLED
        String status = userLookupMapper.findStatusById(userId);
        if (status == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "用户不存在");
        }
        if (!"ENABLED".equals(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "用户未启用");
        }

        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setProjectRole(role.name());
        try {
            projectMemberMapper.insert(member);
        } catch (DuplicateKeyException ex) {
            // 唯一约束 uk_project_member 冲突：用户已是成员
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "用户已是该项目成员", ex);
        }

        publishAudit(caller, ACTION_PROJECT_MEMBER_ADDED, RESOURCE_PROJECT_MEMBER,
                projectId + ":" + userId,
                detailOf("projectId", projectId, "userId", userId, "role", role.name()));
    }

    @Override
    @Transactional
    public void removeMember(Long projectId, Long userId) {
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        int affected = projectMemberMapper.deleteByProjectAndUser(projectId, userId);
        if (affected > 0) {
            // 立即清理 PermissionEvaluator 缓存（R6.4），避免被移除的成员仍能在 60s 内继续访问
            permissionEvaluator.evictMember(projectId, userId);

            publishAudit(caller, ACTION_PROJECT_MEMBER_REMOVED, RESOURCE_PROJECT_MEMBER,
                    projectId + ":" + userId,
                    detailOf("projectId", projectId, "userId", userId));
        } else {
            log.debug("removeMember noop: projectId={} userId={} (not a member)", projectId, userId);
        }
    }

    @Override
    public List<ProjectMemberDTO> listMembers(Long projectId) {
        // 项目必须存在
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目不存在");
        }
        List<ProjectMemberRow> rows = projectMemberMapper.listMembers(projectId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProjectMemberDTO> result = new ArrayList<>(rows.size());
        for (ProjectMemberRow row : rows) {
            result.add(new ProjectMemberDTO(
                    row.getUserId(),
                    row.getUsername(),
                    row.getProjectRole(),
                    row.getJoinedAt()));
        }
        return result;
    }

    @Override
    public boolean isMember(Long projectId, Long userId) {
        if (projectId == null || userId == null) {
            return false;
        }
        return projectMemberMapper.isMember(projectId, userId) > 0;
    }

    @Override
    public Optional<ProjectRole> roleOf(Long projectId, Long userId) {
        if (projectId == null || userId == null) {
            return Optional.empty();
        }
        String code = projectMemberMapper.roleOf(projectId, userId);
        if (code == null || code.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ProjectRole.valueOf(code));
        } catch (IllegalArgumentException ex) {
            // DB CHECK 约束保证不会到达这里；防御性日志一行
            log.warn("unexpected project_role code in DB: projectId={} userId={} code={}",
                    projectId, userId, code);
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private void publishAudit(AuthenticatedUser caller, String action, String resourceType,
                              String resourceId, Map<String, Object> detail) {
        AuditEvent event = AuditEvent.of(
                caller.id(),
                caller.username(),
                action,
                resourceType,
                resourceId,
                null,
                detail);
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

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static ProjectDTO toDTO(ProjectListRow row) {
        return new ProjectDTO(
                row.getId(),
                row.getName(),
                row.getDescription(),
                row.getDefaultBranch(),
                row.getLanguage(),
                row.getCreatedBy(),
                row.getMemberCount(),
                row.getCreatedAt());
    }

    /** 把刚 INSERT 的 Project 退化为 ProjectListRow（仅当二次查询返回 null 时兜底）。 */
    private static ProjectListRow toListRow(Project p, int memberCount) {
        ProjectListRow row = new ProjectListRow();
        row.setId(p.getId());
        row.setName(p.getName());
        row.setDescription(p.getDescription());
        row.setDefaultBranch(p.getDefaultBranch());
        row.setLanguage(p.getLanguage());
        row.setCreatedBy(p.getCreatedBy());
        row.setCreatedAt(p.getCreatedAt());
        row.setUpdatedAt(p.getUpdatedAt());
        row.setMemberCount(memberCount);
        return row;
    }
}
