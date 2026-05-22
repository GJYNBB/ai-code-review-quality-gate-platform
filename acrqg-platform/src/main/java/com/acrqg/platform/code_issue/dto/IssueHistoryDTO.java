package com.acrqg.platform.code_issue.dto;

import java.time.OffsetDateTime;

/**
 * 问题状态历史视图（design.md §8.4 / R17）。
 *
 * <p>由 B4-A {@code IssueService.history} 返回。
 *
 * <p>Covers: R17。
 *
 * @param id            历史记录主键
 * @param codeIssueId   关联问题主键
 * @param fromStatus    迁移前状态
 * @param toStatus      迁移后状态
 * @param comment       说明文本，可为 {@code null}
 * @param operatorId    操作者主键，系统迁移时为 {@code null}
 * @param operatorName  操作者用户名（Service 层 join 回填）
 * @param changedAt     变更时间
 */
public record IssueHistoryDTO(
        Long id,
        Long codeIssueId,
        String fromStatus,
        String toStatus,
        String comment,
        Long operatorId,
        String operatorName,
        OffsetDateTime changedAt
) {
}
