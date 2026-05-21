package com.acrqg.platform.dashboard.service;

import com.acrqg.platform.dashboard.dto.DashboardQuery;
import com.acrqg.platform.dashboard.dto.QualityTrendDTO;
import com.acrqg.platform.dashboard.dto.RiskFileDTO;
import java.util.List;

/**
 * 项目质量看板服务（M08 / R18）。
 *
 * <p>提供两类聚合查询：
 * <ul>
 *   <li>{@link #trend} —— 按日聚合 {@code review_task}，返回任务数、通过率、
 *       平均评分、平均耗时的时间序列；</li>
 *   <li>{@link #topRiskFiles} —— 按 {@code file_path} 聚合 {@code code_issue}，
 *       按加权得分降序返回 TopN。</li>
 * </ul>
 *
 * <p>权限：实现侧由 {@code DashboardController} 通过 {@code @RequirePermission(projectMember=true)}
 * 在 Controller 边界拦截非项目成员（R18.4）。Service 自身假设调用方已通过权限校验。
 *
 * <p>Covers: R18.1, R18.2, R18.3, R18.4, R18.5。
 */
public interface DashboardService {

    /**
     * 查询项目质量趋势。
     *
     * <p>实现需保证：
     * <ol>
     *   <li>{@code query.startDate / endDate} 跨度若超过 365 天，抛
     *       {@code BusinessException(VALIDATION_ERROR, "time range cannot exceed 365 days")}（R18.2）；</li>
     *   <li>缺失日期补 0 而非省略，保证时间序列与日历一一对应；</li>
     *   <li>{@code passRate = passCount / taskCount}，{@code taskCount=0} 时为 {@code null}；</li>
     *   <li>所有时区按 UTC 计算；</li>
     *   <li>结果按日期升序。</li>
     * </ol>
     *
     * @param projectId 项目主键
     * @param query     查询参数（已通过 Bean Validation）
     * @return 包含逐日数据点 + 区间汇总的 {@link QualityTrendDTO}
     */
    QualityTrendDTO trend(Long projectId, DashboardQuery query);

    /**
     * 高风险文件 TopN（R18.3）。
     *
     * <p>实现需保证：
     * <ol>
     *   <li>{@code limit} 落在 {@code [1,100]}，超出由 Service 层兜底约束；</li>
     *   <li>排除 {@code FALSE_POSITIVE} 状态的问题；</li>
     *   <li>权重：CRITICAL=10, HIGH=5, MEDIUM=2, LOW=1, INFO=0.5；</li>
     *   <li>排序：{@code weighted_score DESC, issue_count DESC, file_path ASC}（稳定）。</li>
     * </ol>
     *
     * @param projectId 项目主键
     * @param limit     返回上限（默认 10）
     * @return 高风险文件列表，按加权得分降序
     */
    List<RiskFileDTO> topRiskFiles(Long projectId, int limit);
}
