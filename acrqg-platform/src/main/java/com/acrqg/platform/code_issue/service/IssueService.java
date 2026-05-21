package com.acrqg.platform.code_issue.service;

import com.acrqg.platform.code_issue.dto.CodeIssueDTO;
import com.acrqg.platform.code_issue.dto.IssueQuery;
import com.acrqg.platform.code_issue.dto.IssueStatusChangeRequest;
import com.acrqg.platform.common.api.PageResult;

/**
 * 问题生命周期服务（design.md §6.8 / R16.2-3, R17）。
 *
 * <p>职责：
 * <ul>
 *   <li>{@link #page} —— 按任务 + 多维过滤分页问题列表（R16.2）；</li>
 *   <li>{@link #get} —— 取问题详情，含状态历史 + 评论（R16.3, R17.4）；</li>
 *   <li>{@link #changeStatus} —— 状态流转（R17.1-R17.5），写 issue_history 并发布审计；</li>
 *   <li>{@link #addComment} —— 追加评论（R16.3）。</li>
 * </ul>
 *
 * <p>权限模型与 design.md §16.5 对齐：所有方法在内部基于
 * "issue → review_task.project_id" 实时校验当前用户是项目成员；非成员抛
 * {@code PERMISSION_DENIED}。{@code DEVELOPER} 角色仅能操作自己创建任务的 issue
 * （R17.5 拒绝转换而非鉴权错误）。
 *
 * <p>Covers: R16.2, R16.3, R17.1, R17.2, R17.3, R17.4, R17.5, R17.6。
 */
public interface IssueService {

    /**
     * 按任务分页查询问题列表（R16.2）。
     *
     * @param taskId 评审任务主键
     * @param query  过滤 + 分页参数；{@code null} 时按默认分页（page=1, pageSize=20）
     * @return 当前页问题列表（不含 history / comments）
     */
    PageResult<CodeIssueDTO> page(Long taskId, IssueQuery query);

    /**
     * 取问题详情（R16.3）。
     *
     * @param id 问题主键
     * @return 含 history / comments 的完整 DTO
     */
    CodeIssueDTO get(Long id);

    /**
     * 状态流转（R17.1-R17.5）。
     *
     * <p>事务内完成：
     * <ol>
     *   <li>校验项目成员关系（非成员抛 PERMISSION_DENIED）；</li>
     *   <li>校验 DEVELOPER 角色仅能操作自己创建任务的 issue（R17.5）；</li>
     *   <li>校验目标状态合法（{@code IssueStateMachine}，R17.1-R17.2）；</li>
     *   <li>校验 FALSE_POSITIVE / CLOSED 时 comment 长度 ≥ 5（R17.3）；</li>
     *   <li>UPDATE code_issue + INSERT issue_history（R17.4）；</li>
     *   <li>发布 {@code AuditEvent("ISSUE_STATUS_CHANGED")}。</li>
     * </ol>
     *
     * @param id  问题主键
     * @param req 状态变更请求
     */
    void changeStatus(Long id, IssueStatusChangeRequest req);

    /**
     * 追加评论（R16.3）。
     *
     * <p>{@code content} 必须 1..1000 字符；非空且 trim 后非空。
     *
     * @param id      问题主键
     * @param content 评论内容
     */
    void addComment(Long id, String content);
}
