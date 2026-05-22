package com.acrqg.platform.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 审计日志 DTO，{@code GET /api/v1/admin/audit-logs} 列表项。
 *
 * <p>对齐 design.md §8.4：
 * <pre>
 * public record AuditLogDTO(Long id, Long operatorId, String operatorUsername,
 *     String action, String resourceType, String resourceId, String ip,
 *     Map&lt;String,Object&gt; detail, OffsetDateTime createdAt) {}
 * </pre>
 *
 * <p>{@code detail} 字段在 DTO 中表达为 {@link Map}，便于前端 JSON 直接消费；
 * 其值已经在写入数据库时由 {@link com.acrqg.platform.audit.service.AuditService}
 * 调用 {@link com.acrqg.platform.common.util.MaskUtils#maskJsonNode} 完成敏感字段
 * 掩码（R22.5 / R23.3）。即查询时不再做掩码（数据库中已是脱敏后的内容）。
 *
 * <p>Covers: R22.2, R22.3, R22.5。
 */
@Schema(description = "审计日志条目（R22.2 / R22.3）")
public record AuditLogDTO(

        @Schema(description = "主键") Long id,

        @Schema(description = "操作者用户主键；系统级操作可为 null") Long operatorId,

        @Schema(description = "操作者用户名快照") String operatorUsername,

        @Schema(description = "动作名，例如 LOGIN_SUCCESS / PROJECT_CREATED") String action,

        @Schema(description = "资源类型，例如 USER / PROJECT / SYSTEM") String resourceType,

        @Schema(description = "资源 ID（字符串形式）") String resourceId,

        @Schema(description = "客户端 IP（IPv4/IPv6）") String ip,

        @Schema(description = "操作明细，敏感字段已掩码为 ****")
        Map<String, Object> detail,

        @Schema(description = "创建时间")
        OffsetDateTime createdAt
) {
}
