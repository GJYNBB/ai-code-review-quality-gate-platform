package com.acrqg.platform.project.service;

import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.infra.permission.ProjectRole;
import com.acrqg.platform.project.dto.AddMemberRequest;
import com.acrqg.platform.project.dto.ProjectCreateRequest;
import com.acrqg.platform.project.dto.ProjectDTO;
import com.acrqg.platform.project.dto.ProjectMemberDTO;
import com.acrqg.platform.project.dto.ProjectQuery;
import com.acrqg.platform.project.dto.ProjectUpdateRequest;
import java.util.List;
import java.util.Optional;

/**
 * 项目（M02）服务接口。
 *
 * <p>对齐 design.md §6.2：
 * <pre>
 * public interface ProjectService {
 *     ProjectDTO create(ProjectCreateRequest req);
 *     PageResult&lt;ProjectDTO&gt; page(ProjectQuery q);
 *     ProjectDTO get(Long projectId);
 *     ProjectDTO update(Long projectId, ProjectUpdateRequest req);
 *     void addMember(Long projectId, Long userId, ProjectRole role);
 *     void removeMember(Long projectId, Long userId);
 *     boolean isMember(Long projectId, Long userId);
 *     Optional&lt;ProjectRole&gt; roleOf(Long projectId, Long userId);
 * }
 * </pre>
 *
 * <p>异常约定：
 * <ul>
 *   <li>{@code create}：name 重复抛 {@code BusinessException(PROJECT_NAME_EXISTS)}（R4.2）；
 *       language 非法时由 Bean Validation 拦截，无需 Service 自检。</li>
 *   <li>{@code update}：项目不存在抛 {@code BusinessException(VALIDATION_ERROR, "项目不存在")}；
 *       调用者非 PROJECT_ADMIN 抛 {@code BusinessException(PERMISSION_DENIED)}（R4.4）。</li>
 *   <li>{@code addMember}：userId 不存在 / 非 ENABLED → {@code VALIDATION_ERROR}（R6.2）；
 *       唯一约束冲突 → {@code VALIDATION_ERROR("用户已是该项目成员")}。</li>
 *   <li>{@code removeMember}：仅删除 {@code project_member} 行，永远不删全局用户（R6.3）。</li>
 * </ul>
 *
 * <p>所有变更操作发布 {@code AuditEvent}（R4.5 / R6 关键操作审计）。
 *
 * <p>Covers: R4.1, R4.2, R4.3, R4.4, R4.5, R6.1, R6.2, R6.3, R6.4。
 */
public interface ProjectService {

    /** 创建项目并把创建者自动加入为 PROJECT_ADMIN（R4.1, R4.2, R6.1）。 */
    ProjectDTO create(ProjectCreateRequest request);

    /** 关键字模糊分页（name + description），返回 memberCount（R4.3）。 */
    PageResult<ProjectDTO> page(ProjectQuery query);

    /** 主键查询；返回 memberCount。项目不存在抛 {@code VALIDATION_ERROR}。 */
    ProjectDTO get(Long projectId);

    /** 更新项目（仅 PROJECT_ADMIN）。{@code null} 字段不覆盖（R4.3, R4.4）。 */
    ProjectDTO update(Long projectId, ProjectUpdateRequest request);

    /** 添加项目成员（R6.1, R6.2）。 */
    void addMember(Long projectId, AddMemberRequest request);

    /** 移除项目成员（R6.3）；不存在则忽略。 */
    void removeMember(Long projectId, Long userId);

    /** 列出项目成员（R6.1）。 */
    List<ProjectMemberDTO> listMembers(Long projectId);

    /** 是否为项目成员（供 PermissionEvaluator 使用）。 */
    boolean isMember(Long projectId, Long userId);

    /** 用户在项目内的角色（供 PermissionEvaluator 使用）。 */
    Optional<ProjectRole> roleOf(Long projectId, Long userId);
}
