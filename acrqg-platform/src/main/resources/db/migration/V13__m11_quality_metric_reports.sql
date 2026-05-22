-- =====================================================================
-- V13__m11_quality_metric_reports.sql
--
-- M11 跟进项：为 test_coverage / duplicate_rate 两个 MetricCollector 启用
-- 真实数据源所新增的 system_param 配置项。新增 4 条，全部 sensitive=false。
--
-- 涉及模块: B3-F MetricCollector (gate.collector.{TestCoverage,DuplicateRate}Collector)
-- 关联需求: R14.1（指标采集真实化）、R21.4（系统参数热更新）
--
-- 字段语义：
--   gate.test_coverage.report.dir    JaCoCo CSV 报告基目录。Worker / CI 在
--                                    GATE_EVALUATING 阶段前把 jacoco.csv 写到
--                                    {dir}/task-{taskId}/jacoco.csv。文件不存在
--                                    时 collector 退化为 placeholder + WARN。
--   gate.test_coverage.report.placeholder
--                                    数据源缺失时的兜底值（百分比，0..100）。
--                                    保留 75 与历史占位一致，便于平滑过渡。
--   gate.duplicate_rate.report.dir   PMD-CPD XML 报告基目录。Worker / CI 写到
--                                    {dir}/task-{taskId}/cpd.xml。
--   gate.duplicate_rate.report.placeholder
--                                    数据源缺失时的兜底值（百分比，0..100）。
--                                    保留 0 与历史占位一致。
--
-- 兼容性：不存在 jacoco.csv / cpd.xml 时返回 placeholder 即可；管理员可通过
--   /api/v1/admin/system-params 修改 .dir / .placeholder。
-- =====================================================================

INSERT INTO system_param (param_key, param_value, description, sensitive) VALUES
    ('gate.test_coverage.report.dir',          'reports/coverage',
     'JaCoCo CSV 报告基目录；Worker 在 GATE_EVALUATING 前把 jacoco.csv 写到 {dir}/task-{taskId}/jacoco.csv。',
     FALSE),
    ('gate.test_coverage.report.placeholder',  '75',
     'test_coverage 数据源缺失时的兜底百分比 [0..100]；与历史占位一致，便于演示场景模板规则通过。',
     FALSE),
    ('gate.duplicate_rate.report.dir',         'reports/cpd',
     'PMD-CPD XML 报告基目录；Worker / CI 写到 {dir}/task-{taskId}/cpd.xml。',
     FALSE),
    ('gate.duplicate_rate.report.placeholder', '0',
     'duplicate_rate 数据源缺失时的兜底百分比 [0..100]；与历史占位一致。',
     FALSE)
ON CONFLICT (param_key) DO NOTHING;

-- end V13__m11_quality_metric_reports.sql
