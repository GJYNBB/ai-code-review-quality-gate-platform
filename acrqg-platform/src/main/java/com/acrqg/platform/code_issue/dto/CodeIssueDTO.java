package com.acrqg.platform.code_issue.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 代码问题对外视图（design.md §8.4）。
 *
 * <p>由 B4-A {@code IssueService.page} / {@code get} 返回；同时是 B3-F
 * 报告聚合的输入项。
 *
 * <p>{@code history} 与 {@code comments} 仅在 {@code IssueService.get(id)} 详情接口
 * 中填充；{@code page(...)} 列表接口为 {@code null}（默认）。两个工厂方法
 * {@link #basic} 与 {@link #withDetails} 分别对应这两种使用场景，避免调用方
 * 传 {@code null} 形成噪声。
 *
 * <p>Covers: R16.2, R16.3, R17.4。
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
 * @param history     状态变更历史；列表接口为 {@code null}，详情接口非 {@code null}
 * @param comments    评论列表；列表接口为 {@code null}，详情接口非 {@code null}
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
        OffsetDateTime updatedAt,
        List<IssueHistoryDTO> history,
        List<IssueCommentDTO> comments
) {

    /**
     * 列表接口构造（不带 history / comments）。
     *
     * <p>返回的 DTO 中 {@code history} 与 {@code comments} 字段为 {@code null}，
     * 可被前端识别为"未加载"。
     */
    public static CodeIssueDTO basic(Long id,
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
                                      OffsetDateTime updatedAt) {
        return new CodeIssueDTO(id, taskId, filePath, lineNo, ruleCode, source,
                severity, status, description, suggestion, confidence,
                createdAt, updatedAt, null, null);
    }

    /**
     * 详情接口构造（包含 history / comments）。
     *
     * <p>当 {@code history} / {@code comments} 为 {@code null} 时按空列表处理，
     * 让前端永远拿到列表语义。
     */
    public static CodeIssueDTO withDetails(Long id,
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
                                            OffsetDateTime updatedAt,
                                            List<IssueHistoryDTO> history,
                                            List<IssueCommentDTO> comments) {
        return new CodeIssueDTO(id, taskId, filePath, lineNo, ruleCode, source,
                severity, status, description, suggestion, confidence,
                createdAt, updatedAt,
                history == null ? List.of() : history,
                comments == null ? List.of() : comments);
    }
}
