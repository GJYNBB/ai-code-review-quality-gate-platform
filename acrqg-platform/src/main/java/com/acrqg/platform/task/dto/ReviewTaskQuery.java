package com.acrqg.platform.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 评审任务分页查询参数。
 *
 * <p>对应 {@code GET /api/v1/review-tasks?projectId=&status=&triggerType=&page=&pageSize=}。
 *
 * <ul>
 *   <li>{@code projectId}    —— 限定项目（可空）。Service 层会校验调用者
 *       是否为该项目成员，非成员返回 {@code PERMISSION_DENIED}。</li>
 *   <li>{@code status}       —— 任务状态精确过滤（可空）。</li>
 *   <li>{@code triggerType}  —— 触发来源精确过滤（可空）。</li>
 *   <li>{@code page} / {@code pageSize} —— 1..100；通过
 *       {@link #safePage()} / {@link #safePageSize()} 兜底。</li>
 * </ul>
 *
 * <p>Covers: R8, R16.5。
 *
 * @param projectId   项目主键（可空）
 * @param status      任务状态（可空）
 * @param triggerType 触发来源（可空）
 * @param page        页码（默认 1）
 * @param pageSize    每页条数（默认 20）
 */
@Schema(description = "评审任务分页查询参数")
public record ReviewTaskQuery(

        @Schema(description = "项目主键（可空，未传时按调用者参与的所有项目过滤）")
        Long projectId,

        @Schema(description = "任务状态精确过滤", example = "PASSED")
        String status,

        @Schema(description = "触发来源", example = "WEBHOOK")
        String triggerType,

        @Schema(description = "页码，从 1 起", example = "1", defaultValue = "1")
        @Min(value = 1, message = "page 必须 >= 1")
        int page,

        @Schema(description = "每页条数 [1,100]", example = "20", defaultValue = "20")
        @Min(value = 1, message = "pageSize 必须 >= 1")
        @Max(value = 100, message = "pageSize 不能超过 100")
        int pageSize
) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public int safePage() {
        return page <= 0 ? DEFAULT_PAGE : page;
    }

    public int safePageSize() {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
