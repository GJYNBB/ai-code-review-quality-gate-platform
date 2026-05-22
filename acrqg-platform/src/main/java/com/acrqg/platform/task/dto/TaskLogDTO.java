package com.acrqg.platform.task.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 任务执行流水对外视图。
 *
 * <p>对应 {@code GET /api/v1/review-tasks/{id}/logs}。{@code detail} 已经在写入时
 * 完成敏感字段掩码，前端可直接展示。
 *
 * <p>Covers: R9.7, R16.5, R23.3。
 *
 * @param id        主键
 * @param taskId    所属任务主键
 * @param stage     阶段名
 * @param level     级别（INFO / WARN / ERROR）
 * @param message   消息文本
 * @param detail    JSON 明细（已掩码），可能为 {@code null}
 * @param createdAt 创建时间
 */
public record TaskLogDTO(
        Long id,
        Long taskId,
        String stage,
        String level,
        String message,
        Map<String, Object> detail,
        OffsetDateTime createdAt
) {
}
