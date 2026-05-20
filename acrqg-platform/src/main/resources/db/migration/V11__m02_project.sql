-- =====================================================================
-- V11__m02_project.sql
-- Task: B1-C.1 (项目与项目成员表 DDL + 索引 + updated_at 触发器)
-- Covers: R4.1, R4.2, R6.1, R6.4
--
-- 说明：
--   - 严格对齐 design.md §7.2 的 DDL：project / project_member 两张表。
--   - project.name 通过唯一约束 uk_project_name 保证组织内唯一（R4.2）。
--   - project_member (project_id, user_id) 通过唯一约束保证不重复加成员（R6.1）。
--   - project_role 通过 CHECK 约束限制取值（DEVELOPER / REVIEWER / PROJECT_ADMIN）。
--   - project.updated_at 由通用触发器函数 touch_updated_at()（V1__init.sql 中创建）
--     自动维护，保证应用层未填充 updated_at 时仍能正确刷新。
--
-- 编码：UTF-8 (no BOM)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 项目主表 project
-- ---------------------------------------------------------------------
CREATE TABLE project (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    default_branch  VARCHAR(128) NOT NULL DEFAULT 'main',
    language        VARCHAR(32)  NOT NULL,             -- Java / Python / JavaScript / TypeScript / Go ...
    created_by      BIGINT       NOT NULL REFERENCES "user"(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_project_name UNIQUE (name)            -- R4.2 组织内唯一
);
CREATE INDEX idx_project_created_by ON project(created_by);

COMMENT ON TABLE  project              IS '项目主表（M02）。R4.1 创建；R4.2 name 唯一。';
COMMENT ON COLUMN project.name         IS '项目名称，组织内唯一（uk_project_name）。';
COMMENT ON COLUMN project.language     IS '主要语言，应用层校验为 Java / Python / JavaScript / TypeScript / Go。';
COMMENT ON COLUMN project.created_by   IS '创建者用户 id；不允许 NULL，便于审计追溯。';

-- ---------------------------------------------------------------------
-- 2. 项目成员表 project_member
--    (project_id, user_id) 唯一（R6.1）；project_role CHECK 限制取值。
--    project 删除时级联删除成员；user 删除暂不级联（R6.3 仅删关联不删用户）。
-- ---------------------------------------------------------------------
CREATE TABLE project_member (
    id           BIGSERIAL    PRIMARY KEY,
    project_id   BIGINT       NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    user_id      BIGINT       NOT NULL REFERENCES "user"(id),
    project_role VARCHAR(32)  NOT NULL CHECK (project_role IN ('DEVELOPER','REVIEWER','PROJECT_ADMIN')),
    joined_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_project_member UNIQUE (project_id, user_id) -- R6.1
);
CREATE INDEX idx_project_member_user ON project_member(user_id);

COMMENT ON TABLE  project_member               IS '项目成员关联表（M02 / R6）。';
COMMENT ON COLUMN project_member.project_role  IS '项目内角色：DEVELOPER / REVIEWER / PROJECT_ADMIN。';

-- ---------------------------------------------------------------------
-- 3. project.updated_at 自动维护触发器
--    复用 V1__init.sql 中创建的通用函数 touch_updated_at()。
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_project_updated
    BEFORE UPDATE ON project
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- end V11__m02_project.sql
