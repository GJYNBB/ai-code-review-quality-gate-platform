-- =====================================================================
-- V10__m01_audit_seed.sql
-- Task: B1-B.4 (审计日志种子数据)
-- Covers: R22.3 (audit_log 字段必含 operatorId/username/action/resource/ip/detail)
--
-- 说明：
--   - audit_log 表本身已在 V1__init.sql 中创建（B0-A.10），同时附带
--     reject_audit_modify() 触发器强制 append-only（R22.4）。
--   - 本迁移仅插入"平台初始化"种子记录，作为 audit_log 的第一条记录，
--     用于后续测试 / 看板 / 审计检索的基线。
--   - 该记录的 operator_id 为 NULL（系统级动作），operator_username='SYSTEM'，
--     action='PLATFORM_INIT'，resource_type='SYSTEM'，resource_id 为 NULL，
--     detail 为 JSONB 形如 {"version": "0.1.0-bootstrap"}。
--   - 幂等：使用 NOT EXISTS 子查询保证重复执行不会写入重复种子（Flyway 校验
--     基于版本号通常不会重复执行 V10，但本地开发期 baseline-on-migrate 模式
--     可能会跳过部分迁移再回填，幂等更安全）。
--
-- 编码：UTF-8
-- =====================================================================

INSERT INTO audit_log (
    operator_id,
    operator_username,
    action,
    resource_type,
    resource_id,
    ip,
    detail
)
SELECT
    NULL,                                    -- operator_id：系统级
    'SYSTEM',                                -- operator_username
    'PLATFORM_INIT',                         -- action：平台初始化
    'SYSTEM',                                -- resource_type
    NULL,                                    -- resource_id
    NULL,                                    -- ip：内部触发，无客户端
    '{"version": "0.1.0-bootstrap"}'::jsonb  -- detail：版本快照
WHERE NOT EXISTS (
    SELECT 1 FROM audit_log
     WHERE action            = 'PLATFORM_INIT'
       AND resource_type     = 'SYSTEM'
       AND operator_username = 'SYSTEM'
);

-- end V10__m01_audit_seed.sql
