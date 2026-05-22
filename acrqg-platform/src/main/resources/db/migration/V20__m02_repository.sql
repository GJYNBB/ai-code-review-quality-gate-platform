-- =====================================================================
-- V20__m02_repository.sql
-- Task: B2-A.1 (代码仓库绑定表 DDL + uk_repository_binding_project + updated_at 触发器)
-- Covers: R5.1, R5.2, R5.3, R5.4, R5.5, R5.6
--
-- 说明：
--   - 严格对齐 design.md §7.2 / 6.2 中的 repository_binding 表 DDL；
--   - access_token / webhook_secret 以 AES-GCM 加密后的 base64 密文落库（R5.3 / R23.2），
--     长度上限按"密文 base64 + 派生开销"留 1024 字节；
--   - provider 通过 CHECK 限制为 GITHUB / GITLAB / GITEE；status 限制 ACTIVE / INACTIVE；
--   - uk_repository_binding_project 强制"一项目一绑定"（R5.6），同时也支撑 RepositoryService.bind
--     的 INSERT ... ON CONFLICT (project_id) DO UPDATE 幂等写入路径；
--   - updated_at 由通用触发器函数 touch_updated_at()（V1__init.sql）维护，
--     与 project / "user" 等表保持一致的更新策略。
--
-- 编码：UTF-8 (no BOM)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 仓库绑定表 repository_binding
-- ---------------------------------------------------------------------
CREATE TABLE repository_binding (
    id                       BIGSERIAL    PRIMARY KEY,
    project_id               BIGINT       NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    provider                 VARCHAR(16)  NOT NULL CHECK (provider IN ('GITHUB','GITLAB','GITEE')),
    repo_url                 VARCHAR(512) NOT NULL,
    access_token_encrypted   VARCHAR(1024) NOT NULL,                          -- AES-GCM 密文 base64（R5.3）
    webhook_secret_encrypted VARCHAR(1024) NOT NULL,                          -- AES-GCM 密文 base64（R5.3）
    webhook_url              VARCHAR(512) NOT NULL,                           -- 平台回调入口（R5.5）
    status                   VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                                          CHECK (status IN ('ACTIVE','INACTIVE')),
    last_checked_at          TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_repository_binding_project UNIQUE (project_id)              -- R5.6 一项目一绑定
);

COMMENT ON TABLE  repository_binding                        IS '仓库绑定表（M02 / R5）。一项目一绑定，accessToken / webhookSecret 加密存储。';
COMMENT ON COLUMN repository_binding.project_id             IS '项目主键，删除项目时级联删除绑定。';
COMMENT ON COLUMN repository_binding.provider               IS '代码平台：GITHUB / GITLAB / GITEE。';
COMMENT ON COLUMN repository_binding.repo_url               IS '原始仓库 URL，原文落库便于运维排查。';
COMMENT ON COLUMN repository_binding.access_token_encrypted IS 'AES-GCM(IV+ciphertext) 的 base64 密文（R5.3 / R23.2）。';
COMMENT ON COLUMN repository_binding.webhook_secret_encrypted IS 'Webhook 签名密钥 AES-GCM 密文（R5.3 / R7.1）。';
COMMENT ON COLUMN repository_binding.webhook_url            IS '平台对外的 webhook 接收 URL（R5.5），由 RepositoryService.bind 计算注入。';
COMMENT ON COLUMN repository_binding.status                 IS 'ACTIVE / INACTIVE，未启用时 webhook 仍可签名但不入队评审。';
COMMENT ON COLUMN repository_binding.last_checked_at        IS '最近一次 ProviderClient.ping 成功时间，运维可见。';

-- ---------------------------------------------------------------------
-- 2. updated_at 自动维护触发器
--    复用 V1__init.sql 中的通用函数 touch_updated_at()。
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_repository_binding_updated
    BEFORE UPDATE ON repository_binding
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- end V20__m02_repository.sql
