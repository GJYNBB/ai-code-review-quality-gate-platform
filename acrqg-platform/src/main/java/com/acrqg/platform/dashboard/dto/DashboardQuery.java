package com.acrqg.platform.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 项目质量看板查询参数（design.md §6.5 / §8.4，R18.1 / R18.2）。
 *
 * <p>对应 {@code GET /api/v1/projects/{id}/dashboard/trend?startDate=&endDate=&branch=}。
 *
 * <ul>
 *   <li>{@code startDate} —— 起始日期（含），不能为空，且不能晚于今天（R18.1）；</li>
 *   <li>{@code endDate}   —— 截止日期（含），不能为空，且不能晚于今天（R18.1）；</li>
 *   <li>{@code branch}    —— 可选源分支过滤（精确匹配 review_task.source_branch）；</li>
 *   <li>{@link #isRangeWithinOneYear()} —— Bean Validation 自定义校验：
 *       {@code startDate} 与 {@code endDate} 跨度不能超过 365 天（R18.2）；
 *       超出时返回 {@code VALIDATION_ERROR}。</li>
 * </ul>
 *
 * <p>本 record 仅承载查询参数，所有时区/边界细节由 {@code DashboardServiceImpl}
 * 负责（按 UTC 计算；区间为 {@code [startDate, endDate]} 闭区间）。
 *
 * <p>Covers: R18.1, R18.2。
 *
 * @param startDate 起始日期（含）
 * @param endDate   截止日期（含）
 * @param branch    可选源分支过滤
 */
@Schema(description = "项目质量看板查询参数（R18.1 / R18.2）")
public record DashboardQuery(

        @Schema(description = "起始日期（含），格式 yyyy-MM-dd",
                example = "2024-12-01", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "startDate 不能为空")
        @PastOrPresent(message = "startDate 不能晚于今天")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,

        @Schema(description = "截止日期（含），格式 yyyy-MM-dd",
                example = "2024-12-31", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "endDate 不能为空")
        @PastOrPresent(message = "endDate 不能晚于今天")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate,

        @Schema(description = "可选源分支过滤", example = "feature/foo")
        String branch
) {

    /** 单次查询允许的最大跨度（含两端），单位：天。 */
    public static final long MAX_RANGE_DAYS = 365L;

    /**
     * R18.2：跨度校验。
     *
     * <p>当任一日期为空时返回 {@code true}，避免与 {@link NotNull} 校验重复报错；
     * 当 {@code startDate} 晚于 {@code endDate} 时同样视为校验失败（语义异常，
     * 与"超过 365 天"共享同一错误信息便于上层处理）。
     *
     * @return 跨度合法时 {@code true}
     */
    @AssertTrue(message = "time range cannot exceed 365 days")
    @Schema(hidden = true)
    public boolean isRangeWithinOneYear() {
        if (startDate == null || endDate == null) {
            return true;
        }
        if (endDate.isBefore(startDate)) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        return days <= MAX_RANGE_DAYS;
    }
}
