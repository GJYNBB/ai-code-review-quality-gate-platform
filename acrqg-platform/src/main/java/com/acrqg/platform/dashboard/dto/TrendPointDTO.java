package com.acrqg.platform.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目质量趋势单日数据点（design.md §6.5 / §8.4，R18.1）。
 *
 * <p>由 {@code DashboardServiceImpl#trend} 按日聚合 {@code review_task} 得到：
 * <ul>
 *   <li>{@link #taskCount}          —— 当日任务总数（含非终态）；</li>
 *   <li>{@link #passCount}          —— 当日终态为 {@code PASSED} 的任务数；</li>
 *   <li>{@link #failCount}          —— 当日终态为 {@code FAILED_GATE} 的任务数；</li>
 *   <li>{@link #passRate}           —— {@code passCount / taskCount}，{@code taskCount=0}
 *       时为 {@code null}（避免除零，前端按"无数据"渲染）；</li>
 *   <li>{@link #avgScore}           —— 当日 {@code review_task.score} 的算术平均；
 *       无评分（如所有任务都未走完门禁阶段）时为 {@code null}；</li>
 *   <li>{@link #avgDurationSeconds} —— 当日 {@code finished_at - created_at} 的算术平均
 *       （秒）；任务未结束 / {@code finished_at} 为空时不计入分母。</li>
 * </ul>
 *
 * <p>所有 {@code BigDecimal} 字段保留 4 位小数，由 Service 端负责定标。
 *
 * <p>Covers: R18.1。
 *
 * @param date               日期（UTC）
 * @param taskCount          当日任务总数
 * @param passCount          当日 PASSED 任务数
 * @param failCount          当日 FAILED_GATE 任务数
 * @param passRate           通过率 [0,1]，taskCount=0 时为 null
 * @param avgScore           平均评分 [0,100]，无评分时为 null
 * @param avgDurationSeconds 平均耗时（秒），无完成任务时为 null
 */
@Schema(description = "项目质量趋势单日数据点（R18.1）")
public record TrendPointDTO(

        @Schema(description = "日期（UTC）", example = "2024-12-01")
        LocalDate date,

        @Schema(description = "当日任务总数", example = "12")
        long taskCount,

        @Schema(description = "当日 PASSED 任务数", example = "9")
        long passCount,

        @Schema(description = "当日 FAILED_GATE 任务数", example = "2")
        long failCount,

        @Schema(description = "通过率 [0,1]，taskCount=0 时为 null", example = "0.7500")
        BigDecimal passRate,

        @Schema(description = "平均评分 [0,100]，无评分时为 null", example = "87.5000")
        BigDecimal avgScore,

        @Schema(description = "平均耗时（秒），无完成任务时为 null", example = "183.2000")
        BigDecimal avgDurationSeconds
) {
}
