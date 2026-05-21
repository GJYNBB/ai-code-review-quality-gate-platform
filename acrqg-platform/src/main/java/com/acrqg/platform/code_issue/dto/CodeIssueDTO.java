package com.acrqg.platform.code_issue.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 代码问题对外视图（design.md §8.4）。
 *
 * <p>由 B4-A {@code IssueService.page} / {@code get} 返回；同时是 B3-F
 * 报告聚合的输入项。
 *
 * <p>Covers: R16.2, R16.3。
 *
 * @param id          主键
 * @param taskId      关联评审任务
 * @param filePath    文件路径
 * @param lineNo      行号；可为 {@code null}
 * @param ruleCode    规则编码
 * @param source      来源（SAST / AI / MANUAL）
 * @param severity    严重等级
 * @param status      状态
 * @param description 描述
 * @param suggestion  修复建议；可为 {@code null}
 * @param confidence  AI 置信度 [0,1]；SAST 通常为 {@code null}
 * @param createdAt   创建时间
 * @param updatedAt   最近更新时间
 */
public record CodeIssueDTO(
        Long id,
        Long taskId,
        String filePath,
        Integer lineNo,
        String ruleCode,
        String source,
        String severity,
        String status,
        String description,
        String suggestion,
        BigDecimal confidence,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
