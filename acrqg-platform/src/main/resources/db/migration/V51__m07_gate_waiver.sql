-- =====================================================================
-- V51__m07_gate_waiver.sql
-- Task: B4-E.1 (M07 门禁豁免审批：gate_waiver DDL)
-- Covers: R15.1, R15.2, R15.3, R15.5, R15.6, R20.6
--
-- 说明：
--   - design.md §7.2 / §6.7：门禁豁免（GateWaiver）一张表，承载 PROJECT_ADMIN
--     对 FAILED_GATE 任务的人工豁免审批流。
--   - 本批次 task 交付清单（B4-E.1）的字段集合：
--       id / task_id / reason / status(PENDING|APPROVED|REJECTED) /
--       applicant_id / approver_id / approved_at / approval_comment /
--       created_at / updated_at + trg_gate_waiver_updated 触发器。
--     与 design.md §7.2 中的 expire_at / EXPIRED 状态相比做了简化：B4-E
--     当前只支持"申请 → 审批 → 永久生效"三步，未引入定时过期，因此
--     status CHECK 约束仅含 PENDING / APPROVED / REJECTED。后续如需
--     EXPIRED 状态可单独写一条 ALTER TABLE 增量。
--   - 唯一约束 uk_gate_waiver_task_pending：防止同一任务重复申请。语义
--     是"同一 task 只能有一条 PENDING 行"；APPROVED / REJECTED 状态
--     不影响新申请（任务再次失败时还可申请新一轮）。这与 design.md
--     §7.2 的 uk_gate_waiver_active 略有差异（后者包含 APPROVED），
--     但与 task 交付清单的语义一致：审批通过后该 task 应转 PASSED，
--     不再可重复申请。
--   - 序号区段：B4 占用 V40~V59；本表是 M07 第二张迁移文件，定为 V51。
--   - 触发器：复用 V1__init.sql 的通用函数 touch_updated_at()。
--
-- 编码：UTF-8 (no BOM)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 门禁豁免申请表 gate_waiver
-- ---------------------------------------------------------------------
CREATE TABLE gate_waiver (
    id               BIGSERIAL    PRIMARY KEY,
    task_id          BIGINT       NOT NULL REFERENCES review_task(id) ON DELETE CASCADE,
    reason           TEXT         NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    applicant_id     BIGINT       NOT NULL REFERENCES "user"(id),
    approver_id      BIGINT       REFERENCES "user"(id),
    approved_at      TIMESTAMPTZ,
    approval_comment TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 唯一约束：同一任务只能有一条 PENDING 记录
-- 部分唯一索引（partial unique index），仅 status='PENDING' 行参与唯一性判定
CREATE UNIQUE INDEX uk_gate_waiver_task_pending
    ON gate_waiver(task_id) WHERE status = 'PENDING';

-- 检索索引
CREATE INDEX idx_gate_waiver_task   ON gate_waiver(task_id, created_at DESC);
CREATE INDEX idx_gate_waiver_status ON gate_waiver(status, created_at DESC);

COMMENT ON TABLE  gate_waiver                  IS '门禁豁免申请（M07 / R15）。一任务可有多条历史记录；同一时刻至多一条 PENDING（uk_gate_waiver_task_pending）。';
COMMENT ON COLUMN gate_waiver.task_id          IS '关联评审任务主键，任务删除时级联清理。';
COMMENT ON COLUMN gate_waiver.reason           IS '申请原因（R15.2，至少 10 字符；服务层强制）。';
COMMENT ON COLUMN gate_waiver.status           IS '申请状态：PENDING / APPROVED / REJECTED。';
COMMENT ON COLUMN gate_waiver.applicant_id     IS '申请人用户主键。';
COMMENT ON COLUMN gate_waiver.approver_id      IS '审批人用户主键；PENDING 时为 NULL。';
COMMENT ON COLUMN gate_waiver.approved_at      IS '审批时间；PENDING 时为 NULL。';
COMMENT ON COLUMN gate_waiver.approval_comment IS '审批意见（可空）。';

-- ---------------------------------------------------------------------
-- 2. updated_at 自动维护触发器
--    复用 V1__init.sql 中的通用函数 touch_updated_at()。
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_gate_waiver_updated
    BEFORE UPDATE ON gate_waiver
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- end V51__m07_gate_waiver.sql
