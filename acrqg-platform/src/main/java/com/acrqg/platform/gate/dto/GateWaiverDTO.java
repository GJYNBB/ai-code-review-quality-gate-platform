package com.acrqg.platform.gate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 门禁豁免申请对外视图（design.md §8.4）。
 *
 * <p>由 {@code GateWaiverService.apply / approve / get / pendingByProject}
 * 返回，通过 {@code ApiResponse<GateWaiverDTO>} 包装。
 *
 * <p>Covers: R15.1, R15.3, R15.5。
 *
 * @param id              主键
 * @param taskId          关联任务主键
 * @param projectId       任务所属项目主键（便于前端无需二次查询任务）
 * @param reason          申请原因
 * @param status          申请状态字符串（PENDING / APPROVED / REJECTED）
 * @param applicantId     申请人主键
 * @param approverId      审批人主键，PENDING 时为 {@code null}
 * @param approvedAt      审批时间，PENDING 时为 {@code null}
 * @param approvalComment 审批意见，可空
 * @param createdAt       创建时间
 * @param updatedAt       最近更新时间
 */
@Schema(description = "门禁豁免申请视图（M07 / R15）")
public record GateWaiverDTO(
        Long id,
        Long taskId,
        Long projectId,
        String reason,
        String status,
        Long applicantId,
        Long approverId,
        OffsetDateTime approvedAt,
        String approvalComment,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
