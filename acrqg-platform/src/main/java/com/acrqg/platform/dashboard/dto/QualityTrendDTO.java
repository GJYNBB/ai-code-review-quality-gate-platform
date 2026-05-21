package com.acrqg.platform.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 项目质量趋势完整响应（design.md §6.5 / §8.4，R18.1 / R18.4）。
 *
 * <p>由 {@code DashboardServiceImpl#trend} 返回，包含：
 * <ol>
 *   <li>查询元信息：{@link #projectId} / {@link #startDate} / {@link #endDate}；</li>
 *   <li>逐日时间序列 {@link #points}：长度 = {@code endDate - startDate + 1}，
 *       缺失日期由 Service 层补 0 而非数据库层 {@code generate_series}（保留 SQL 简洁、
 *       缩短锁时间）。{@code points[i].date} 单调递增；</li>
 *   <li>区间汇总 {@link #totals}：覆盖 {@code totalTasks} / {@code overallPassRate}
 *       / {@code overallAvgScore}，便于前端在卡片上直接呈现，不必重新聚合。</li>
 * </ol>
 *
 * <p>Covers: R18.1, R18.4。
 *
 * @param projectId 项目主键
 * @param startDate 起始日期（含）
 * @param endDate   截止日期（含）
 * @param points    每日数据点（按 date 升序）
 * @param totals    区间汇总
 */
@Schema(description = "项目质量趋势响应（R18.1）")
public record QualityTrendDTO(

        @Schema(description = "项目主键", example = "42")
        Long projectId,

        @Schema(description = "起始日期（含）", example = "2024-12-01")
        LocalDate startDate,

        @Schema(description = "截止日期（含）", example = "2024-12-31")
        LocalDate endDate,

        @Schema(description = "每日数据点（按 date 升序，缺失日期补 0）")
        List<TrendPointDTO> points,

        @Schema(description = "区间汇总")
        Totals totals
) {

    /**
     * 区间汇总（用于看板顶部 KPI 卡片）。
     *
     * @param totalTasks      区间内任务总数
     * @param overallPassRate {@code 区间内 passCount / taskCount}，{@code taskCount=0} 时为 {@code null}
     * @param overallAvgScore 区间内 score 的算术平均，无评分时为 {@code null}
     */
    @Schema(description = "区间汇总")
    public record Totals(

            @Schema(description = "区间内任务总数", example = "245")
            long totalTasks,

            @Schema(description = "区间通过率 [0,1]，无任务时为 null", example = "0.7800")
            BigDecimal overallPassRate,

            @Schema(description = "区间平均评分 [0,100]，无评分时为 null", example = "85.6700")
            BigDecimal overallAvgScore
    ) {
    }
}
