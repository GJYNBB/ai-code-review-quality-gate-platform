-- =====================================================================
-- V32__m05_code_issue.sql
-- Task: B3-D.4 (M05 静态扫描问题表 + 状态历史 + 评论)
-- Covers: R11.2, R12.3, R16.2, R17
--
-- 说明：
--   - 此迁移与 B4-A 的 issue 模块共享 code_issue / issue_history / issue_comment
--     三张表；在本 V32 中创建一次，B4-A 不再重复创建（仅追加 service / controller）。
--   - code_issue.source 取值 SAST（B3-D 静态扫描）/ AI（B3-E AI 评审）/ MANUAL（B4-A 用户新增）。
--   - code_issue.status 状态机由 B4-A IssueService 维护：
--       NEW → CONFIRMED / FALSE_POSITIVE / CLOSED
--       CONFIRMED → PENDING_VERIFY → CLOSED
--       FALSE_POSITIVE / CLOSED → REOPENED → CONFIRMED
--   - issue_history / issue_comment 通过 code_issue_id 外键级联，避免在删除任务后留下孤儿；
--     因 code_issue 已对 review_task ON DELETE CASCADE，删除任务时三张表会一并清理。
--   - 索引设计依据 design.md §7.3：
--       * idx_code_issue_task             ：报告页与编排器按 task 全量取回；
--       * idx_code_issue_severity_status  ：B4-A 列表筛选与 B3-F MetricCollector
--                                           （critical_issue_count 等聚合）的核心覆盖索引；
--       * idx_code_issue_file             ：报告页按文件分组、风险文件 TopN 计算（R18.3）。
--
--   - confidence NUMERIC(3,2)：B3-E AI 评审写入的置信度 [0.00, 1.00]；SAST 通常为 NULL。
--
-- 编码：UTF-8 (no BOM)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 代码问题表 code_issue
-- ---------------------------------------------------------------------
CREATE TABLE code_issue (
    id          BIGSERIAL    PRIMARY KEY,
    task_id     BIGINT       NOT NULL REFERENCES review_task(id) ON DELETE CASCADE,
    file_path   VARCHAR(1024) NOT NULL,
    line_no     INT          CHECK (line_no IS NULL OR line_no >= 0),
    rule_code   VARCHAR(255),
    source      VARCHAR(16)  NOT NULL CHECK (source IN ('SAST','AI','MANUAL')),
    severity    VARCHAR(16)  NOT NULL CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW','INFO')),
    status      VARCHAR(16)  NOT NULL DEFAULT 'NEW'
                CHECK (status IN ('NEW','CONFIRMED','FALSE_POSITIVE','PENDING_VERIFY','CLOSED','REOPENED')),
    description TEXT         NOT NULL,
    suggestion  TEXT,
    confidence  NUMERIC(3,2) CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_code_issue_task            ON code_issue(task_id);
CREATE INDEX idx_code_issue_severity_status ON code_issue(task_id, severity, status);
CREATE INDEX idx_code_issue_file            ON code_issue(task_id, file_path);

COMMENT ON TABLE  code_issue              IS '代码问题（M05 / M06 / R11.2）。SAST + AI + MANUAL 三类来源共享此表。';
COMMENT ON COLUMN code_issue.task_id      IS '关联评审任务主键，任务删除时级联清理。';
COMMENT ON COLUMN code_issue.file_path    IS '问题所在文件路径（与 diff_file.file_path 同源）。';
COMMENT ON COLUMN code_issue.line_no      IS '问题所在行号；可为空（如规则级、文件级问题）。';
COMMENT ON COLUMN code_issue.rule_code    IS '工具规则编码（如 checkstyle:LineLength / eslint:no-unused-vars / sk-rule-001）。';
COMMENT ON COLUMN code_issue.source       IS 'SAST=静态扫描；AI=AI 评审；MANUAL=人工补录。';
COMMENT ON COLUMN code_issue.severity     IS 'CRITICAL/HIGH/MEDIUM/LOW/INFO，由 SeverityMapper 归一化。';
COMMENT ON COLUMN code_issue.status       IS '问题状态机：NEW/CONFIRMED/FALSE_POSITIVE/PENDING_VERIFY/CLOSED/REOPENED。';
COMMENT ON COLUMN code_issue.description  IS '问题描述（工具消息或 AI 评审输出）。';
COMMENT ON COLUMN code_issue.suggestion   IS '修复建议；可为空。';
COMMENT ON COLUMN code_issue.confidence   IS 'AI 评审置信度 [0,1]；SAST 通常为 NULL。';

-- ---------------------------------------------------------------------
-- 2. 问题状态历史 issue_history
-- ---------------------------------------------------------------------
CREATE TABLE issue_history (
    id            BIGSERIAL    PRIMARY KEY,
    code_issue_id BIGINT       NOT NULL REFERENCES code_issue(id) ON DELETE CASCADE,
    from_status   VARCHAR(16)  NOT NULL,
    to_status     VARCHAR(16)  NOT NULL,
    comment       VARCHAR(1024),
    operator_id   BIGINT       REFERENCES "user"(id),
    changed_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_issue_history_issue ON issue_history(code_issue_id, changed_at DESC);

COMMENT ON TABLE  issue_history              IS '问题状态变更历史（M08 / R17）。';
COMMENT ON COLUMN issue_history.code_issue_id IS '关联问题主键。';
COMMENT ON COLUMN issue_history.from_status  IS '迁移前状态。';
COMMENT ON COLUMN issue_history.to_status    IS '迁移后状态。';
COMMENT ON COLUMN issue_history.comment      IS '迁移说明（FALSE_POSITIVE / CLOSED 时长度需 ≥ 5）。';
COMMENT ON COLUMN issue_history.operator_id  IS '操作者用户主键；系统迁移时为 NULL。';

-- ---------------------------------------------------------------------
-- 3. 问题评论 issue_comment
-- ---------------------------------------------------------------------
CREATE TABLE issue_comment (
    id            BIGSERIAL    PRIMARY KEY,
    code_issue_id BIGINT       NOT NULL REFERENCES code_issue(id) ON DELETE CASCADE,
    content       TEXT         NOT NULL,
    operator_id   BIGINT       NOT NULL REFERENCES "user"(id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_issue_comment_issue ON issue_comment(code_issue_id, created_at DESC);

COMMENT ON TABLE  issue_comment              IS '问题评论（M08 / R16.3）。';
COMMENT ON COLUMN issue_comment.code_issue_id IS '关联问题主键。';
COMMENT ON COLUMN issue_comment.content      IS '评论内容（Markdown 文本）。';
COMMENT ON COLUMN issue_comment.operator_id  IS '评论用户主键。';

-- ---------------------------------------------------------------------
-- 4. updated_at 自动维护触发器（仅 code_issue 有 updated_at 列）
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_code_issue_updated
    BEFORE UPDATE ON code_issue
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- end V32__m05_code_issue.sql
