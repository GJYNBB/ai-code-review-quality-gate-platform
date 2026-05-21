package com.acrqg.platform.code_issue.domain;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import java.util.Set;

/**
 * {@link CodeIssue} 状态机（design.md §6.4 / §17 / R17.1）。
 *
 * <p>合法迁移集合（{@link #ALLOWED_ISSUE_EDGES}）严格对齐 R17.1：
 * <pre>
 *   NEW              → CONFIRMED, FALSE_POSITIVE
 *   CONFIRMED        → PENDING_VERIFY, CLOSED
 *   PENDING_VERIFY   → CLOSED, REOPENED
 *   CLOSED           → REOPENED
 *   REOPENED         → CONFIRMED, FALSE_POSITIVE
 * </pre>
 *
 * <p>本类与 {@link com.acrqg.platform.task.domain.StateMachine}（评审任务状态机）
 * 在结构上对齐：把"边集合"作为 {@code public static final} 常量公开，便于 Property
 * 测试（B4-A.6）直接断言；并把 {@link #tryTransit(CodeIssueStatus, CodeIssueStatus)}
 * 作为 Service 层的统一校验入口——非法迁移抛
 * {@link BusinessException}({@link ErrorCode#VALIDATION_ERROR})（R17.2）。
 *
 * <p>评论非空校验（R17.3：FALSE_POSITIVE / CLOSED 需 comment ≥ 5）属于<b>正交关注点</b>，
 * 由 {@link com.acrqg.platform.code_issue.dto.IssueStatusChangeRequest#commentValid()}
 * 与 IssueService 共同负责，不在本状态机内合并——目的是让"状态迁移合法性"与
 * "字段级 Bean Validation"互相解耦，便于复用与单元测试。
 *
 * <p>Covers: R17.1, R17.2。
 */
public final class IssueStateMachine {

    /**
     * 一条合法的状态迁移边。
     *
     * <p>{@code (from, to)} 共同构成 {@link Set} 的 hash key；与
     * {@link com.acrqg.platform.task.domain.StateMachine.Edge} 命名对齐，
     * 便于跨状态机的一致使用。
     *
     * @param from 起始状态
     * @param to   目标状态
     */
    public record Edge(CodeIssueStatus from, CodeIssueStatus to) {
    }

    /**
     * 合法迁移边集合（R17.1）。
     *
     * <p>使用 {@link Set#of} 保证不可变；与 design §6.3 评审任务状态机
     * 的 {@code ALLOWED_TASK_EDGES} 风格一致。
     */
    public static final Set<Edge> ALLOWED_ISSUE_EDGES = Set.of(
            new Edge(CodeIssueStatus.NEW, CodeIssueStatus.CONFIRMED),
            new Edge(CodeIssueStatus.NEW, CodeIssueStatus.FALSE_POSITIVE),
            new Edge(CodeIssueStatus.CONFIRMED, CodeIssueStatus.PENDING_VERIFY),
            new Edge(CodeIssueStatus.CONFIRMED, CodeIssueStatus.CLOSED),
            new Edge(CodeIssueStatus.PENDING_VERIFY, CodeIssueStatus.CLOSED),
            new Edge(CodeIssueStatus.PENDING_VERIFY, CodeIssueStatus.REOPENED),
            new Edge(CodeIssueStatus.CLOSED, CodeIssueStatus.REOPENED),
            new Edge(CodeIssueStatus.REOPENED, CodeIssueStatus.CONFIRMED),
            new Edge(CodeIssueStatus.REOPENED, CodeIssueStatus.FALSE_POSITIVE)
    );

    private IssueStateMachine() {
        // utility class
    }

    /**
     * 仅检查 {@code (from, to)} 是否在合法迁移集内，不抛异常。
     *
     * <p>用于属性测试（B4-A.6）与上层"先看再写"场景。
     *
     * @return 合法返回 {@code true}；其余返回 {@code false}（包括 {@code from == null}
     *         或 {@code to == null}）
     */
    public static boolean isAllowed(CodeIssueStatus from, CodeIssueStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED_ISSUE_EDGES.contains(new Edge(from, to));
    }

    /**
     * 校验状态迁移合法性。
     *
     * <p>非法时抛 {@link BusinessException} ({@link ErrorCode#VALIDATION_ERROR})，
     * 文案格式 {@code illegal issue transition: FROM->TO}。
     *
     * <p>Covers: R17.1, R17.2。
     *
     * @param from 起始状态，非 {@code null}
     * @param to   目标状态，非 {@code null}
     * @throws BusinessException 当 {@code from}/{@code to} 为 {@code null} 或迁移非法时
     */
    public static void tryTransit(CodeIssueStatus from, CodeIssueStatus to) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "illegal issue transition: " + from + "->" + to);
        }
        if (!ALLOWED_ISSUE_EDGES.contains(new Edge(from, to))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "illegal issue transition: " + from + "->" + to);
        }
    }

    /**
     * 返回不可变的合法迁移边集合（供 Property 测试或诊断使用）。
     *
     * <p>等价于 {@link #ALLOWED_ISSUE_EDGES}，保留方法形式以提升调用方语义清晰度
     * （与 design §19 Property 2 中"边集合查询"的接口约定对齐）。
     */
    public static Set<Edge> allowedTransitions() {
        return ALLOWED_ISSUE_EDGES;
    }
}
