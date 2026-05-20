-- =====================================================================
-- V12__m10_admin.sql
-- Task: B1-D.1 (M10 系统管理：模型 / 扫描器 / 系统参数 DDL + 种子数据)
-- Covers: R21.1, R21.3, R21.4
--
-- 严格对齐 design.md §7.2 的 DDL：
--   - model_config         (R21.1 / R21.2 / R23.2: apiKey 加密存储)
--   - scanner_config       (R21.3: 扫描器命令模板)
--   - system_param         (R21.4 / R24.3: 系统参数热更新)
--
-- 通用约定:
--   - 主键 BIGSERIAL；时间统一 TIMESTAMPTZ DEFAULT NOW()
--   - updated_at 由 V1__init.sql 中的 touch_updated_at() 触发器自动维护
--   - 所有 INSERT 使用 ON CONFLICT DO NOTHING 保证幂等
--
-- 编码：UTF-8 (no BOM)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. model_config —— AI 模型配置（R21.1 / R21.2 / R23.2）
--    api_key_encrypted 由 TokenEncryptor (AES-GCM-256) 加密后存储；
--    DB 中不存任何 apiKey 明文，且响应/日志通过 ResponseMaskingAspect 掩码。
-- ---------------------------------------------------------------------
CREATE TABLE model_config (
    id                  BIGSERIAL    PRIMARY KEY,
    name                VARCHAR(64)  NOT NULL,
    base_url            VARCHAR(255) NOT NULL,
    api_key_encrypted   VARCHAR(1024) NOT NULL,
    timeout_seconds     INT          NOT NULL DEFAULT 60
                                       CHECK (timeout_seconds BETWEEN 10 AND 300),
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_model_name UNIQUE (name)
);

COMMENT ON TABLE  model_config                    IS 'AI 模型配置（M10 / R21.1）。apiKey 必须加密存储（R23.2）。';
COMMENT ON COLUMN model_config.api_key_encrypted  IS 'AES-GCM-256 base64 密文；禁止在响应/日志中输出明文（R21.2 / R23.3）。';
COMMENT ON COLUMN model_config.timeout_seconds    IS '单次 AI 调用超时（秒），范围 10..300，DB CHECK 强制。';

-- ---------------------------------------------------------------------
-- 2. scanner_config —— 静态扫描器配置（R21.3）
--    command 为模板字符串，可包含 {workdir} / {file} / {output} 占位，
--    由扫描适配器在执行前替换为实际路径。
-- ---------------------------------------------------------------------
CREATE TABLE scanner_config (
    id                 BIGSERIAL    PRIMARY KEY,
    name               VARCHAR(64)  NOT NULL,
    language           VARCHAR(32)  NOT NULL,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    command            VARCHAR(1024) NOT NULL,
    result_parser_type VARCHAR(32)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_scanner_name UNIQUE (name)
);

COMMENT ON TABLE  scanner_config                    IS '静态扫描器配置（M10 / R21.3）。';
COMMENT ON COLUMN scanner_config.language           IS '适用语言：java / javascript / typescript / python / any。';
COMMENT ON COLUMN scanner_config.command            IS 'Shell 命令模板，含 {workdir} / {file} / {output} 占位。';
COMMENT ON COLUMN scanner_config.result_parser_type IS '结果解析类型：CHECKSTYLE_XML / ESLINT_JSON / PYLINT_JSON / SEMGREP_JSON。';

-- ---------------------------------------------------------------------
-- 3. system_param —— 系统参数表（R21.4 / R24.3）
--    sensitive=true 的参数 param_value 列存储 AES-GCM 加密后的密文，
--    应用层在读取时通过 MaskUtils 掩码再返回。
-- ---------------------------------------------------------------------
CREATE TABLE system_param (
    id          BIGSERIAL    PRIMARY KEY,
    param_key   VARCHAR(128) NOT NULL,
    param_value VARCHAR(1024) NOT NULL,
    description VARCHAR(255),
    sensitive   BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_by  BIGINT REFERENCES "user"(id),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_system_param_key UNIQUE (param_key)
);

COMMENT ON TABLE  system_param             IS '系统参数表（M10 / R21.4）。sensitive 参数加密存储 + 掩码输出。';
COMMENT ON COLUMN system_param.param_value IS 'sensitive=true 时为 AES-GCM 密文；否则为明文配置值。';
COMMENT ON COLUMN system_param.sensitive   IS 'true 表示参数值需加密存储且响应时掩码（R23.3）。';

-- ---------------------------------------------------------------------
-- 4. updated_at 自动维护触发器
--    复用 V1__init.sql 中创建的通用函数 touch_updated_at()。
--    system_param 也声明触发器：当 UPDATE 时自动刷新 updated_at。
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_model_config_updated
    BEFORE UPDATE ON model_config
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_scanner_config_updated
    BEFORE UPDATE ON scanner_config
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_system_param_updated
    BEFORE UPDATE ON system_param
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- =====================================================================
-- 5. 种子数据（幂等：ON CONFLICT DO NOTHING）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 5.1 scanner_config —— 4 条预置扫描器
--     - checkstyle (Java)
--     - eslint     (JavaScript / TypeScript；language='javascript' 覆盖二者)
--     - pylint     (Python)
--     - semgrep    (any，跨语言)
--     command 中的 {file} / {output} 占位由 B3-D 静态扫描适配器替换。
-- ---------------------------------------------------------------------
INSERT INTO scanner_config (name, language, enabled, command, result_parser_type) VALUES
    ('checkstyle', 'java',       TRUE,
     'checkstyle -c /google_checks.xml -f xml -o {output} {file}',
     'CHECKSTYLE_XML'),
    ('eslint',     'javascript', TRUE,
     'eslint --no-eslintrc --format json --output-file {output} {file}',
     'ESLINT_JSON'),
    ('pylint',     'python',     TRUE,
     'pylint --output-format=json {file} > {output}',
     'PYLINT_JSON'),
    ('semgrep',    'any',        TRUE,
     'semgrep --config=auto --json --output {output} {file}',
     'SEMGREP_JSON')
ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------------
-- 5.2 system_param —— 默认值
--     review.worker.concurrency      默认 4   (R21.4 范围 1..32)
--     ai.review.timeout.seconds       默认 60  (R21.4 范围 10..300)
--     diff.maxLinesPerFile            默认 5000 (R21.4 范围 100..50000)
--     gate.score.formula.weight.default 默认 1.0
--
--   tokenEncryptionKey 不在此处种子化：design §13.2 明确"密文形态由启动参数
--   --app.master-key 解锁，不持久化于库"。因此应用层从环境变量
--   TOKEN_ENCRYPTION_KEY 装载，DB 不存。
-- ---------------------------------------------------------------------
INSERT INTO system_param (param_key, param_value, description, sensitive) VALUES
    ('review.worker.concurrency',         '4',
     '单 JVM 内 Redis Stream 评审任务消费者线程数；范围 1..32。',
     FALSE),
    ('ai.review.timeout.seconds',         '60',
     '单次 AI 评审调用超时秒数；范围 10..300，超时即触发降级。',
     FALSE),
    ('diff.maxLinesPerFile',              '5000',
     '单文件 diff 最大行数；超过该阈值的文件标记 oversized=true 并跳过 AI 评审。范围 100..50000。',
     FALSE),
    ('gate.score.formula.weight.default', '1.0',
     '门禁评分公式默认权重，规则未单独配置时使用。',
     FALSE)
ON CONFLICT (param_key) DO NOTHING;

-- end V12__m10_admin.sql
