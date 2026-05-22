package com.acrqg.platform.dashboard.controller;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.dashboard.dto.DashboardQuery;
import com.acrqg.platform.dashboard.dto.QualityTrendDTO;
import com.acrqg.platform.dashboard.dto.RiskFileDTO;
import com.acrqg.platform.dashboard.service.DashboardService;
import com.acrqg.platform.infra.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目质量看板控制器（B4-C.3 / R18）。
 *
 * <p>对齐 design.md §6.5 / §8.7 与 02 号 RESTful 接口设计文档：
 * <pre>
 * GET /api/v1/projects/{id}/dashboard/trend         @RequirePermission(projectMember=true)
 * GET /api/v1/projects/{id}/dashboard/risk-files    @RequirePermission(projectMember=true)
 * </pre>
 *
 * <p>权限：两个端点都通过 {@link RequirePermission} 强制要求当前用户为目标项目
 * 的成员（R18.4），非成员返回 {@code PERMISSION_DENIED}（403）。
 *
 * <p>校验：
 * <ul>
 *   <li>{@link DashboardQuery} 的 Bean Validation 由
 *       {@link Valid @Valid} + {@link ModelAttribute @ModelAttribute} 触发，
 *       绑定 URL query 参数 {@code startDate / endDate / branch}；</li>
 *   <li>{@code limit} 通过 {@link RequestParam} + {@link Min}/{@link Max} 限制
 *       在 {@code [1, 100]}，默认 {@code 10}；类级别 {@link Validated} 让方法级
 *       约束生效；</li>
 *   <li>跨度 &gt; 365 天的查询由 DashboardQuery 的 {@code @AssertTrue} 校验
 *       拦截，错误码 {@code VALIDATION_ERROR}（R18.2）。</li>
 * </ul>
 *
 * <p>Covers: R18.1, R18.2, R18.3, R18.4, R23.1。
 */
@RestController
@RequestMapping("/api/v1/projects/{id}/dashboard")
@Validated
@Tag(name = "Dashboard", description = "项目质量看板（M08 / R18）")
public class DashboardController {

    /** 高风险文件 TopN 默认 limit，与 service 层一致。 */
    public static final int DEFAULT_RISK_FILE_LIMIT = 10;

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "项目质量趋势",
            description = "按日聚合 review_task，返回 taskCount / passRate / avgScore / "
                    + "avgDurationSeconds 等时间序列。startDate 与 endDate 跨度不能超过 365 天，"
                    + "否则返回 VALIDATION_ERROR（R18.2）。仅项目成员可访问。")
    @GetMapping("/trend")
    @RequirePermission(projectMember = true, projectIdParam = "id")
    public ApiResponse<QualityTrendDTO> trend(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute DashboardQuery query) {
        return ApiResponse.success(dashboardService.trend(id, query));
    }

    @Operation(summary = "高风险文件 TopN",
            description = "按 file_path 聚合 code_issue，按加权得分降序取前 N 条；"
                    + "权重 CRITICAL=10, HIGH=5, MEDIUM=2, LOW=1, INFO=0.5。"
                    + "limit 默认 10，范围 [1,100]。仅项目成员可访问。")
    @GetMapping("/risk-files")
    @RequirePermission(projectMember = true, projectIdParam = "id")
    public ApiResponse<List<RiskFileDTO>> riskFiles(
            @PathVariable("id") Long id,
            @RequestParam(value = "limit", required = false, defaultValue = "10")
            @Min(value = 1, message = "limit 必须 >= 1")
            @Max(value = 100, message = "limit 不能超过 100")
            int limit) {
        return ApiResponse.success(dashboardService.topRiskFiles(id, limit));
    }
}
