package com.acrqg.platform.code_issue.dto;

import com.acrqg.platform.code_issue.domain.CodeIssueStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 问题状态变更请求（design.md §8.4 / R17）。
 *
 * <p>状态机由 B4-A {@code IssueService} 完整实现。本 DTO 仅承载入参。
 *
 * <p>R17.2：标记 FALSE_POSITIVE 或 CLOSED 时必须提供 {@code comment} 且长度 ≥ 5。
 *
 * <p>Covers: R17.1, R17.2。
 *
 * @param status  目标状态
 * @param comment 操作说明（FALSE_POSITIVE / CLOSED 时长度需 ≥ 5）
 */
public record IssueStatusChangeRequest(
        @NotNull CodeIssueStatus status,
        @Size(max = 1024) String comment
) {

    @AssertTrue(message = "标记误报或关闭时 comment 长度需 ≥ 5")
    public boolean commentValid() {
        if (status == CodeIssueStatus.FALSE_POSITIVE || status == CodeIssueStatus.CLOSED) {
            return comment != null && comment.trim().length() >= 5;
        }
        return true;
    }
}
