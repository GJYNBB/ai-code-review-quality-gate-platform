package com.acrqg.platform.report.repository;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 报告页问题聚合 Mapper（B4-B.2）。
 *
 * <p>专为 {@link com.acrqg.platform.report.service.impl.ReportServiceImpl}
 * 提供"按 (severity, source) 二维聚合"的查询；不在
 * {@link com.acrqg.platform.code_issue.repository.CodeIssueMapper} 中扩展，
 * 是为了把"报告聚合"这一只读视图的实现封装在 report 模块内，避免
 * 跨模块的签名变更。
 *
 * <p>SQL：
 * <pre>
 * SELECT severity, source, COUNT(*) AS cnt
 *   FROM code_issue
 *  WHERE task_id = ?
 *    AND status &lt;&gt; 'FALSE_POSITIVE'
 *  GROUP BY severity, source
 *  ORDER BY severity, source
 * </pre>
 *
 * <p>FALSE_POSITIVE 在统计中被排除，与 {@code MetricCollector} 计算
 * {@code critical_issue_count} 的语义保持一致（R17.6）。
 *
 * <p>Covers: R16.1。
 */
@Mapper
public interface ReportIssueAggregationMapper {

    /**
     * 按 (severity, source) 聚合任务下的有效问题数。
     *
     * <p>返回的每个 Map 元素包含三个键：
     * <ul>
     *   <li>{@code severity}（{@link String}）；</li>
     *   <li>{@code source}（{@link String}）；</li>
     *   <li>{@code cnt}（{@link Long}）。</li>
     * </ul>
     *
     * <p>使用 {@code Map<String, Object>} 而非自定义 Row 类，避免在 mapper 与 DTO
     * 之间引入第三个仅做"查询投影"的中间类。
     *
     * @param taskId 任务主键
     * @return 聚合结果列表（按 severity、source 字典序）
     */
    @Select("""
            SELECT severity, source, COUNT(*) AS cnt
              FROM code_issue
             WHERE task_id = #{taskId}
               AND status <> 'FALSE_POSITIVE'
             GROUP BY severity, source
             ORDER BY severity, source
            """)
    List<Map<String, Object>> aggregateBySeverityAndSource(@Param("taskId") Long taskId);
}
