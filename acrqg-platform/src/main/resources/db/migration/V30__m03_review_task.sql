-- =====================================================================
-- V30__m03_review_task.sql
-- Task: B3-A.1 (评审任务核心：review_task 与 task_log 表 + 状态/触发类型 CHECK +
--               幂等三元组唯一索引 + updated_at 触发器)
-- Covers: R7.4, R8.3, R9.1, R9.7
--
-- 说明：
--   - 严格对齐 design.md §6.3 / §7.2 / §7.4：
--     review_task 状态字典 8 种；trigger_type 4 种；
--     uk_review_task_no、uk_review_task_triple 用于 task_no 与 (project_id, pr_id, commit_sha)
--     幂等去重（R7.4 / R8.3）。
--   - 相比 design.md §7.2 补充：attempt（重试计数，R9.4）、ai_risk_score（AI 风险分，B3-E 写入）、
--     started_at / finished_at（任务计时，R9.7）。所有补充字段均非破坏性，不影响后续模块。
--   - task_log：append-only 流水（INFO/WARN/ERROR），detail 列使用 JSONB；
--     idx_task_log_task_stage_time 支撑 design.md §7.3 报告执行日志查询模式。
--   - updated_at：复用 V1__init.sql 中的通用函数 touch_updated_at()。
--
-- 编码：UTF-8 (no BOM)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 评审任务表 review_task
-- ---------------------------------------------------------------------
CREATE TABLE review_task (
    id            BIGSERIAL    PRIMARY KEY,
    task_no       VARCHAR(64)  NOT NULL,                                       -- 形如 RT2026051900000001
    project_id    BIGINT       NOT NULL REFERENCES project(id),
    pr_id         VARCHAR(64),
    source_branch VARCHAR(255),
    target_branch VARCHAR(255),
    commit_sha    VARCHAR(64)  NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN
                      ('PENDING','FETCHING_DIFF','STATIC_SCANNING','AI_REVIEWING',
                       'GATE_EVALUATING','PASSED','FAILED_GATE','EXECUTION_FAILED')),
    trigger_type  VARCHAR(16)  NOT NULL DEFAULT 'WEBHOOK'
                  CHECK (trigger_type IN ('WEBHOOK','MANUAL','CI_CD','RETRY')),
    score         INT          CHECK (score IS NULL OR (score BETWEEN 0 AND 100)),
    ai_risk_score INT          CHECK (ai_risk_score IS NULL OR (ai_risk_score BETWEEN 0 AND 100)),
    ai_available  BOOLEAN      NOT NULL DEFAULT TRUE,
    attempt       INT          NOT NULL DEFAULT 1 CHECK (attempt >= 1),
    created_by    BIGINT       REFERENCES "user"(id),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_review_task_no     UNIQUE (task_no),
    CONSTRAINT uk_review_task_triple UNIQUE (project_id, pr_id, commit_sha)    -- R7.4 / R8.3 幂等
);

CREATE INDEX idx_review_task_status      ON review_task(status);
CREATE INDEX idx_review_task_project_ts  ON review_task(project_id, created_at DESC);
CREATE INDEX idx_review_task_finished_at ON review_task(finished_at);

COMMENT ON TABLE  review_task                IS '评审任务（M03 / R7~R9）。状态机驱动，三元组幂等。';
COMMENT ON COLUMN review_task.task_no        IS '业务可读编号 RT{yyyyMMdd}{seq}（R8.4），全局唯一。';
COMMENT ON COLUMN review_task.project_id     IS '所属项目主键。';
COMMENT ON COLUMN review_task.pr_id          IS 'PR/MR 编号（外部代码平台），允许为空（直接 push 触发场景）。';
COMMENT ON COLUMN review_task.commit_sha     IS '触发评审的 commit；与 (project_id, pr_id) 共同构成幂等键。';
COMMENT ON COLUMN review_task.status         IS '状态机当前状态（design §6.3.1）。';
COMMENT ON COLUMN review_task.trigger_type   IS '触发来源：WEBHOOK / MANUAL / CI_CD / RETRY。';
COMMENT ON COLUMN review_task.score          IS '门禁评分 [0,100]，由 B3-F GateEngine 写入。';
COMMENT ON COLUMN review_task.ai_risk_score  IS 'AI 风险分 [0,100]，由 B3-E AI 阶段写入；AI 不可用时为 NULL。';
COMMENT ON COLUMN review_task.ai_available   IS 'AI 服务在本任务内是否可用；FALSE 表示走降级（R12.5）。';
COMMENT ON COLUMN review_task.attempt        IS '重试次数（首次为 1，每次 retry +1）。';
COMMENT ON COLUMN review_task.created_by     IS '触发用户主键；webhook 触发时为 NULL。';

-- ---------------------------------------------------------------------
-- 2. 任务执行流水表 task_log
-- ---------------------------------------------------------------------
CREATE TABLE task_log (
    id         BIGSERIAL    PRIMARY KEY,
    task_id    BIGINT       NOT NULL REFERENCES review_task(id) ON DELETE CASCADE,
    stage      VARCHAR(32)  NOT NULL,
    level      VARCHAR(8)   NOT NULL CHECK (level IN ('INFO','WARN','ERROR')),
    message    TEXT         NOT NULL,
    detail     JSONB,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_task_log_task_stage_time ON task_log(task_id, stage, created_at DESC);

COMMENT ON TABLE  task_log            IS '评审任务执行流水（M03 / R9.7）。append-only，按 task 级联删除。';
COMMENT ON COLUMN task_log.task_id    IS '关联评审任务主键，任务删除时级联清理流水。';
COMMENT ON COLUMN task_log.stage      IS '阶段名（FETCHING_DIFF / STATIC_SCANNING / AI_REVIEWING / GATE_EVALUATING / SYSTEM）。';
COMMENT ON COLUMN task_log.level      IS '级别：INFO / WARN / ERROR。';
COMMENT ON COLUMN task_log.detail     IS 'JSON 明细（已掩码），可承载 stack trace、外部响应摘要等。';

-- ---------------------------------------------------------------------
-- 3. updated_at 自动维护触发器
--    review_task 复用 V1__init.sql 中的通用函数 touch_updated_at()。
--    task_log 是 append-only 表，无 updated_at 列。
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_review_task_updated
    BEFORE UPDATE ON review_task
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- end V30__m03_review_task.sql
