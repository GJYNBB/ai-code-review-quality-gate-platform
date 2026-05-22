-- =====================================================================
-- V33__m07_gate_result.sql
-- Task: B3-F.1 (M07 质量门禁判定结果：gate_result DDL)
-- Covers: R14.3, R14.4, R14.5, R14.8
--
-- 说明：
--   - 严格对齐 design.md §7.2 + B3-F 任务交付清单：gate_result 一张表，
--     一对一映射 review_task（uk_gate_result_task），承载门禁判定结果。
--   - status 枚举包含 PENDING/PASSED/FAILED/WAIVED：B3-F GateRuleEngine
--     仅写入 PASSED / FAILED；PENDING 预留给"门禁判定中"未持久化的过渡态；
--     WAIVED 由 B4-E 豁免审批写入（R15.3 / R14.6）。在本迁移就把 4 种状态
--     一并放进 CHECK 约束，避免 B4-E 时再做 schema 演进。
--   - score / ai_risk_score 允许 NULL：当门禁配置为空（无任何启用规则）时
--     工程上仍写一条 PASSED 记录但 score=100；ai_risk_score 在 ai_available
--     =false 时为 NULL（design §12.5 + R12.5）。
--   - ai_available 默认 TRUE：AI 阶段降级时会把 review_task.ai_available 置
--     FALSE，本表也会同步落 FALSE，便于报告页直接展示。
--   - summary JSONB：design §7.2 注释 "failedRules / passedRules / aiAvailable"，
--     B3-F 实际写入 GateResultSummary 的序列化结果，包含 failedRules / passedRules
--     / metricValues 三段。
--   - 序号区段：B3 占用 V30~V39（V30~V32 已用于 review_task / diff_file /
--     code_issue；V33 留给 gate_result）。
--   - updated_at：复用 V1__init.sql 中的通用函数 touch_updated_at()。
--
-- 编码：UTF-8 (no BOM)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 门禁判定结果表 gate_result
--    一对一映射 review_task（uk_gate_result_task），任务删除时级联清理。
-- ---------------------------------------------------------------------
CREATE TABLE gate_result (
    id            BIGSERIAL    PRIMARY KEY,
    task_id       BIGINT       NOT NULL REFERENCES review_task(id) ON DELETE CASCADE,
    status        VARCHAR(16)  NOT NULL
                  CHECK (status IN ('PENDING','PASSED','FAILED','WAIVED')),
    score         INT          CHECK (score IS NULL OR (score BETWEEN 0 AND 100)),
    ai_risk_score INT          CHECK (ai_risk_score IS NULL OR (ai_risk_score BETWEEN 0 AND 100)),
    ai_available  BOOLEAN      NOT NULL DEFAULT TRUE,
    summary       JSONB        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_gate_result_task UNIQUE (task_id)
);

CREATE INDEX idx_gate_result_status ON gate_result(status);

COMMENT ON TABLE  gate_result               IS '质量门禁判定结果（M07 / R14）。一任务一行，由 GateRuleEngine.evaluate 写入；B4-E 豁免时翻为 WAIVED。';
COMMENT ON COLUMN gate_result.task_id       IS '关联评审任务主键，任务删除时级联清理（uk_gate_result_task 强制 1:1）。';
COMMENT ON COLUMN gate_result.status        IS '判定结果：PENDING(过渡态) / PASSED(R14.4) / FAILED(R14.3) / WAIVED(R15.3)。';
COMMENT ON COLUMN gate_result.score         IS '质量评分 [0,100]，由 GateRuleEngine 按 system_param 配置的公式计算（R14.5）。';
COMMENT ON COLUMN gate_result.ai_risk_score IS 'AI 风险分快照（与 review_task.ai_risk_score 同源）；ai_available=false 时通常为 NULL。';
COMMENT ON COLUMN gate_result.ai_available  IS 'AI 服务在本次评估时的可用性（R12.5）；FALSE 表示 AI 阶段降级。';
COMMENT ON COLUMN gate_result.summary       IS 'JSON 明细：failedRules / passedRules / metricValues 三段（R14.8）。';

-- ---------------------------------------------------------------------
-- 2. updated_at 自动维护触发器
--    复用 V1__init.sql 中的通用函数 touch_updated_at()。
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_gate_result_updated
    BEFORE UPDATE ON gate_result
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- end V33__m07_gate_result.sql
