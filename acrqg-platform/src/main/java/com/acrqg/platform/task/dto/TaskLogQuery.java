package com.acrqg.platform.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 任务流水分页查询参数。
 *
 * <p>对应 {@code GET /api/v1/review-tasks/{id}/logs?stage=&level=&page=&pageSize=}。
 *
 * <p>排序固定为 {@code created_at DESC, id DESC}（与
 * {@code idx_task_log_task_stage_time} 索引方向一致）。
 *
 * <p>Covers: R9.7, R16.5。
 *
 * @param stage    阶段名（精确匹配，可空）
 * @param level    级别（INFO / WARN / ERROR，可空）
 * @param page     页码
 * @param pageSize 每页条数
 */
@Schema(description = "任务流水分页查询参数")
public record TaskLogQuery(

        @Schema(description = "阶段名（精确匹配）", example = "AI_REVIEWING")
        String stage,

        @Schema(description = "级别", example = "ERROR")
        String level,

        @Schema(description = "页码", example = "1", defaultValue = "1")
        @Min(value = 1, message = "page 必须 >= 1")
        int page,

        @Schema(description = "每页条数 [1,100]", example = "50", defaultValue = "50")
        @Min(value = 1, message = "pageSize 必须 >= 1")
        @Max(value = 100, message = "pageSize 不能超过 100")
        int pageSize
) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 50;
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
