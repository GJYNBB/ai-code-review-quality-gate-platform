package com.acrqg.platform.code_issue.dto;

import java.time.OffsetDateTime;

/**
 * 问题评论视图（design.md §8.4）。
 *
 * <p>由 B4-A {@code IssueService.addComment} / {@code listComments} 返回。
 *
 * <p>Covers: R16.3。
 *
 * @param id            评论主键
 * @param codeIssueId   关联问题主键
 * @param content       评论内容
 * @param operatorId    评论用户主键
 * @param operatorName  评论用户用户名（在 Service 层 join 后回填）
 * @param createdAt     创建时间
 */
public record IssueCommentDTO(
        Long id,
        Long codeIssueId,
        String content,
        Long operatorId,
        String operatorName,
        OffsetDateTime createdAt
) {
}
