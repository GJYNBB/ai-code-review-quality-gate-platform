-- =====================================================================
-- V1__init.sql
-- Covers: R1.6, R2, R22.4, R23.3
-- Module map:
--   M01 用户与权限   -> "user", role, user_role
--   M01 审计         -> audit_log + reject_audit_modify() + 触发器
--   通用             -> touch_updated_at() + "user".updated_at 触发器
--
-- 设计依据：design.md §7.2（DDL）。
-- 时区说明：本迁移不设置 SET TIME ZONE，时区在应用层（application.yml）
--           的 spring.jackson / Hibernate / JDBC 参数中统一处理，避免与
--           Flyway 事务会话耦合。
-- 编码：UTF-8
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 用户表 "user"
--    USER 是 PostgreSQL 保留字，必须使用双引号包裹。
-- ---------------------------------------------------------------------
CREATE TABLE "user" (
    id              BIGSERIAL    PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL,
    email           VARCHAR(128) NOT NULL,
    password_hash   VARCHAR(120) NOT NULL,                                    -- BCrypt 60 字符；保留余量
    status          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED'
                                  CHECK (status IN ('ENABLED','DISABLED')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_email    UNIQUE (email)
);

COMMENT ON TABLE  "user"               IS '平台用户表（M01）。R1.6 密码必须以 BCrypt 哈希存储。';
COMMENT ON COLUMN "user".password_hash IS 'BCrypt 哈希值，禁止在任何接口响应或日志中输出（R1.6 / R23.3）。';
COMMENT ON COLUMN "user".status        IS '账号状态。DISABLED 时 R3.2 要求其已签发 token 在 5 分钟内失效。';

-- ---------------------------------------------------------------------
-- 2. 角色表 role
-- ---------------------------------------------------------------------
CREATE TABLE role (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL,                                        -- DEVELOPER / REVIEWER / PROJECT_ADMIN / SYSTEM_ADMIN / CI_CD
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(255),
    CONSTRAINT uk_role_code UNIQUE (code)
);

COMMENT ON TABLE  role      IS '全局角色字典（M01 / R2）。';
COMMENT ON COLUMN role.code IS '角色编码：DEVELOPER / REVIEWER / PROJECT_ADMIN / SYSTEM_ADMIN / CI_CD。';

-- ---------------------------------------------------------------------
-- 3. 用户-角色关联表 user_role
-- ---------------------------------------------------------------------
CREATE TABLE user_role (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES role(id),
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);
CREATE INDEX idx_user_role_user ON user_role(user_id);
CREATE INDEX idx_user_role_role ON user_role(role_id);

COMMENT ON TABLE user_role IS '用户与角色的多对多关联（M01 / R2.1）。';

-- ---------------------------------------------------------------------
-- 4. 审计日志表 audit_log
--    R22.3 字段；R22.4 仅追加（通过触发器禁止 UPDATE/DELETE）。
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id                BIGSERIAL    PRIMARY KEY,
    operator_id       BIGINT       REFERENCES "user"(id),
    operator_username VARCHAR(64),
    action            VARCHAR(64)  NOT NULL,
    resource_type     VARCHAR(64),
    resource_id       VARCHAR(64),
    ip                VARCHAR(45),
    detail            JSONB,                                                  -- 已掩码后的明细（R22.5 / R23.3）
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_operator_action_time ON audit_log(operator_id, action, created_at DESC);
CREATE INDEX idx_audit_action_time          ON audit_log(action, created_at DESC);

COMMENT ON TABLE  audit_log        IS '审计日志（M01 / R22）。仅追加（append-only），由触发器强制。';
COMMENT ON COLUMN audit_log.detail IS '操作明细 JSONB，已对密码、accessToken、apiKey、webhookSecret 等敏感字段掩码（R22.5）。';

-- ---------------------------------------------------------------------
-- 5. 审计日志只追加：拒绝 UPDATE / DELETE 的触发器
--    R22.4：审计记录一经写入即不可被修改或删除。
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION reject_audit_modify() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_no_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_audit_modify();

CREATE TRIGGER trg_audit_no_delete
    BEFORE DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_audit_modify();

-- ---------------------------------------------------------------------
-- 6. 自动维护 updated_at 的通用函数与 "user" 触发器
--    其他业务表的 updated_at 触发器在各自的迁移中创建。
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_user_updated
    BEFORE UPDATE ON "user"
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- =====================================================================
-- 7. 种子数据（幂等：使用 ON CONFLICT DO NOTHING）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 7.1 五条角色种子（R2 角色取值）
-- ---------------------------------------------------------------------
INSERT INTO role(code, name, description) VALUES
    ('DEVELOPER',     '开发者',
        '可创建/查看本人参与项目的评审任务，处理本人任务下的问题。'),
    ('REVIEWER',      '评审者',
        '可评审项目的任务与问题、审批门禁豁免。'),
    ('PROJECT_ADMIN', '项目管理员',
        '可创建项目、绑定仓库、管理项目成员、配置质量门禁。'),
    ('SYSTEM_ADMIN',  '系统管理员',
        '可管理全局用户、模型/扫描器/系统参数与审计日志。'),
    ('CI_CD',         'CI/CD',
        '允许调用 Webhook 与手动创建评审任务接口，禁止写入用户/项目配置/门禁规则。')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------
-- 7.2 初始 SYSTEM_ADMIN 用户：admin / admin@local
--     password_hash 为 BCrypt(cost=10) 离线生成，明文为 "Admin@123"。
--     仅用于初始化引导；生产环境必须在首次登录后立即修改并轮换。
--     R1.6：密码以 BCrypt 哈希存储，禁止任何接口响应或日志输出原文/哈希。
-- ---------------------------------------------------------------------
INSERT INTO "user"(username, email, password_hash, status) VALUES
    ('admin', 'admin@local',
     '$2a$10$wH2j2fMnJZDj7jVRNJqnku0nhHVEf9D2QdBFdQ1mcg1GZj7LgFmYS',
     'ENABLED')
ON CONFLICT (username) DO NOTHING;

-- ---------------------------------------------------------------------
-- 7.3 admin 用户绑定 SYSTEM_ADMIN 角色
--     使用子查询确保 id 解耦于具体序列值；幂等。
-- ---------------------------------------------------------------------
INSERT INTO user_role(user_id, role_id)
SELECT u.id, r.id
  FROM "user" u, role r
 WHERE u.username = 'admin'
   AND r.code     = 'SYSTEM_ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- end V1__init.sql
