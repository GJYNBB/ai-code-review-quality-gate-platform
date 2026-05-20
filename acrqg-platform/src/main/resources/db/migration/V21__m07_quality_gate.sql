-- =====================================================================
-- V21__m07_quality_gate.sql
-- Task: B2-B.1 (M07 质量门禁配置：quality_gate / gate_rule DDL)
-- Covers: R13.1, R13.2, R13.4, R13.5
--
-- 说明：
--   - 严格对齐 design.md §7.2 的 DDL：quality_gate / gate_rule 两张表。
--   - 同一项目同一时刻仅允许一个 enabled=true 版本（R13.4）：通过
--     部分唯一索引 uk_quality_gate_one_enabled 强制；保存时 Service 层先把
--     旧版本 enabled=false，再插入新版本 enabled=true（事务内）。
--   - quality_gate 不需要 updated_at / 触发器：版本一旦创建，业务流不再
--     原地修改其元数据；启用切换通过 UPDATE 列 enabled 即可。
--   - gate_rule 同样不需要触发器：规则随 quality_gate 整体创建 / 删除。
--   - 不在此处种子模板（设计中明确：模板由 Service 层 getDefaultTemplate()
--     返回内存对象；DB 中 project_id NOT NULL 也无法插入跨项目的种子）。
--
-- 序号区段：B2 占用 V20~V29（与 V20 仓库绑定并行；本任务取 V21）。
--
-- 编码：UTF-8 (no BOM)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 质量门禁主表 quality_gate
--    一个项目可有多个版本（version），仅一个 enabled=true。
--    project 删除时级联删除门禁版本及其规则（gate_rule 同时级联）。
-- ---------------------------------------------------------------------
CREATE TABLE quality_gate (
    id         BIGSERIAL    PRIMARY KEY,
    project_id BIGINT       NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name       VARCHAR(128) NOT NULL,
    version    INT          NOT NULL,                       -- R13.4 历史版本
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by BIGINT       NOT NULL REFERENCES "user"(id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_gate_project_version UNIQUE (project_id, version)
);
CREATE INDEX idx_quality_gate_project ON quality_gate(project_id);

-- 同一项目同一时刻只能一个 enabled=true（R13.4）：使用 partial unique index
CREATE UNIQUE INDEX uk_quality_gate_one_enabled
    ON quality_gate(project_id) WHERE enabled = TRUE;

COMMENT ON TABLE  quality_gate            IS '质量门禁版本主表（M07 / R13）。同项目仅一条 enabled=TRUE（uk_quality_gate_one_enabled）。';
COMMENT ON COLUMN quality_gate.version    IS '版本号，与 project_id 联合唯一（uk_gate_project_version）。新版本递增 max(version)+1。';
COMMENT ON COLUMN quality_gate.enabled    IS '是否当前启用版本；同 project 仅允许一条 TRUE（部分唯一索引保证）。';
COMMENT ON COLUMN quality_gate.created_by IS '创建者用户 id；用于审计追溯（R22.1）。';

-- ---------------------------------------------------------------------
-- 2. 门禁规则表 gate_rule
--    每个 quality_gate 版本对应若干条规则；CHECK 约束限制取值集合。
--    metric / operator / severity 取值集合与 design.md §7.2 严格一致。
-- ---------------------------------------------------------------------
CREATE TABLE gate_rule (
    id         BIGSERIAL    PRIMARY KEY,
    gate_id    BIGINT       NOT NULL REFERENCES quality_gate(id) ON DELETE CASCADE,
    metric     VARCHAR(64)  NOT NULL CHECK (metric IN
                ('critical_issue_count','security_issue_count','test_coverage',
                 'duplicate_rate','ai_risk_score','new_issue_count')),
    operator   VARCHAR(4)   NOT NULL CHECK (operator IN ('<=','>=','<','>','==','!=')),
    threshold  VARCHAR(32)  NOT NULL,
    severity   VARCHAR(8)   NOT NULL CHECK (severity IN ('BLOCKER','WARN')),
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_gate_rule_gate ON gate_rule(gate_id);

COMMENT ON TABLE  gate_rule            IS '门禁规则表（M07 / R13.2）。属于某 quality_gate 版本，跟随版本生命周期。';
COMMENT ON COLUMN gate_rule.metric     IS '指标名：critical_issue_count / security_issue_count / test_coverage / duplicate_rate / ai_risk_score / new_issue_count。';
COMMENT ON COLUMN gate_rule.operator   IS '比较运算符：<= / >= / < / > / == / !=。';
COMMENT ON COLUMN gate_rule.threshold  IS '阈值（字符串存储以兼容整数 / 浮点 / 百分比格式）；判定时由 OperatorEvaluator 转 BigDecimal。';
COMMENT ON COLUMN gate_rule.severity   IS '失败级别：BLOCKER 触发整体 FAILED；WARN 仅记录。';
COMMENT ON COLUMN gate_rule.enabled    IS '单条规则停用标志（R13.6）；停用后判定时跳过该规则。';
COMMENT ON COLUMN gate_rule.sort_order IS '展示顺序，前端按 sort_order 升序排列。';

-- end V21__m07_quality_gate.sql
