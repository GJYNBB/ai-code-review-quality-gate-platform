-- =====================================================================
-- V50__m09_notification.sql
-- Task: B4-D.1 (M09 站内通知：notification 表 DDL + 索引)
-- Covers: R19.1, R19.2, R19.3, R19.4, R19.5
--
-- 说明：
--   - 严格对齐 design.md §7.2 的 notification DDL 与 §6.6 / §8.4 NotificationDTO
--     字段；并在此基础上把任务交付清单要求的 link / related_type / related_id /
--     read_at 字段一并落库，避免 B5 SSE 与回写联动时再做 schema 演进。
--   - type 字段使用 VARCHAR(32) 而非 PostgreSQL ENUM：design.md §7.2 整张表均使用
--     字符串 + 应用层枚举，便于灰度新增类型（TASK_FINISHED / GATE_FAILED /
--     ISSUE_ASSIGNED / WAIVER_REQUEST / WAIVER_APPROVED / WAIVER_REJECTED ...）。
--     此处 CHECK 约束放宽为字符串非空，由应用层 NotificationType 枚举做最终校验。
--   - 索引设计：
--     * idx_notification_user_unread —— 部分索引，仅覆盖 read=false 的行；
--       与 R19.3 "未读列表"、UI-002 头部红点轮询场景对齐，能极大压缩扫描行数。
--     * idx_notification_user_created —— 全量按时间倒序索引；服务于"全部通知"
--       分页查询（read=NULL 时）。
--   - read_at：非空当且仅当 read=true。约束由应用层保证（避免 PG 复杂 CHECK），
--     migration 仅暴露字段。
--   - 90 天保留策略（R19.5）：通过应用层 @Scheduled + 系统参数 archive_age_days
--     处理，本表不做分区；created_at 索引足以支撑 LIMIT/DELETE 批处理。
--
-- 编码：UTF-8 (no BOM)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 站内通知表 notification
-- ---------------------------------------------------------------------
CREATE TABLE notification (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    type         VARCHAR(32)  NOT NULL CHECK (length(type) > 0),
    title        VARCHAR(200) NOT NULL,
    body         TEXT         NOT NULL,
    link         VARCHAR(500),
    read         BOOLEAN      NOT NULL DEFAULT FALSE,
    related_type VARCHAR(32),
    related_id   BIGINT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    read_at      TIMESTAMPTZ
);

COMMENT ON TABLE  notification              IS '站内通知（M09 / R19）。任务终态、豁免审批、问题指派均落一行。';
COMMENT ON COLUMN notification.user_id      IS '收件人用户主键；用户被删除时级联清理本人通知。';
COMMENT ON COLUMN notification.type         IS '通知类型：TASK_FINISHED / GATE_FAILED / ISSUE_ASSIGNED / WAIVER_REQUEST / WAIVER_APPROVED / WAIVER_REJECTED 等，应用层枚举控制。';
COMMENT ON COLUMN notification.title        IS '通知标题，前端列表与红点 popover 展示。';
COMMENT ON COLUMN notification.body         IS '通知正文，支持纯文本或受信任的 Markdown，前端渲染时按需转义。';
COMMENT ON COLUMN notification.link         IS '前端跳转路径（可选），例如 /review-tasks/{id}、/issues/{id}。';
COMMENT ON COLUMN notification.read         IS '已读标记；R19.4 PATCH /notifications/{id}/read 时置 TRUE。';
COMMENT ON COLUMN notification.related_type IS '业务关联资源类型：review_task / code_issue / gate_waiver。';
COMMENT ON COLUMN notification.related_id   IS '业务关联资源主键，配合 related_type 用于跳转 / 去重。';
COMMENT ON COLUMN notification.read_at      IS '首次置为已读的时间；read=false 时为 NULL。';

-- ---------------------------------------------------------------------
-- 2. 索引
-- ---------------------------------------------------------------------
-- 未读快查（R19.3 红点 + 未读列表）。仅索引 read=false，写放大与索引体积均较低。
CREATE INDEX idx_notification_user_unread
    ON notification (user_id, read, created_at DESC)
    WHERE read = FALSE;

-- 全量按时间倒序，服务"全部通知"分页与历史检索。
CREATE INDEX idx_notification_user_created
    ON notification (user_id, created_at DESC);

-- end V50__m09_notification.sql
