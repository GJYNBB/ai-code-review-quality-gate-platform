package com.acrqg.platform.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 高风险文件单条记录（design.md §6.5 / §8.4，R18.3）。
 *
 * <p>由 {@code DashboardServiceImpl#topRiskFiles} 按 {@code file_path} 聚合
 * {@code code_issue} 表得到，并按 {@link #weightedScore 加权得分} 降序取前 N 条。
 *
 * <p><b>权重定义</b>（与 SQL 保持一致；调整时需同步修改 {@code DashboardMapper}）：
 * <ul>
 *   <li>CRITICAL = 10</li>
 *   <li>HIGH     = 5</li>
 *   <li>MEDIUM   = 2</li>
 *   <li>LOW      = 1</li>
 *   <li>INFO     = 0.5</li>
 * </ul>
 *
 * <p>{@link #weightedScore} 使用 {@link BigDecimal} 以保留 INFO 的 0.5 精度，
 * 避免在 SUM 后被截断为整数；前端按字符串渲染即可。
 *
 * <p>状态过滤：仅排除 {@code FALSE_POSITIVE}（已确认误报），其它状态（NEW /
 * CONFIRMED / PENDING_VERIFY / CLOSED / REOPENED）均计入加权分数；
 * CLOSED 也计入是因为"高风险文件"需要反映"历史负担"，与 R18.3 表述一致。
 *
 * <p>Covers: R18.3。
 *
 * @param filePath      文件路径
 * @param issueCount    问题总数（不含 FALSE_POSITIVE）
 * @param weightedScore 加权得分（CRITICAL=10, HIGH=5, MEDIUM=2, LOW=1, INFO=0.5）
 * @param criticalCount CRITICAL 级别问题数
 * @param highCount     HIGH 级别问题数
 */
@Schema(description = "高风险文件 TopN 单条记录（R18.3）")
public record RiskFileDTO(

        @Schema(description = "文件路径", example = "src/main/java/com/example/Foo.java")
        String filePath,

        @Schema(description = "问题总数（不含 FALSE_POSITIVE）", example = "9")
        long issueCount,

        @Schema(description = "加权得分（CRITICAL=10, HIGH=5, MEDIUM=2, LOW=1, INFO=0.5）",
                example = "32.5")
        BigDecimal weightedScore,

        @Schema(description = "CRITICAL 级别问题数", example = "1")
        long criticalCount,

        @Schema(description = "HIGH 级别问题数", example = "3")
        long highCount
) {
}
