-- =====================================================================
-- V31__m04_diff_file.sql
-- Task: B3-C.1 (代码差异文件表 diff_file + uk_diff_file_task_path + idx_diff_file_task)
-- Covers: R10.2, R10.3
--
-- 说明：
--   - 严格对齐 design.md §6.4 / §7.2 的 diff_file 表，并按本任务范围扩展：
--       * change_type 在 design 的 ADDED/MODIFIED/DELETED 基础上新增 RENAMED
--         （GitHub/GitLab/Gitee 三平台均支持的状态，B3-C.3 在 ProviderClient
--         实现中映射）；
--       * 拆分原 design 的 diff_payload JSONB 为两列：
--           hunks  JSONB —— 解析后的 hunk 结构（供 ReportService.diffView 渲染，
--                            含 oldStart/newStart/lines）；
--           patch  TEXT  —— 原始 unified diff 文本，供属性测试 P7 与运维归档；
--         两列同源，分列存储能让常见报告查询无需把大字符串拽进内存。
--       * 增加 old_path VARCHAR(1024) —— 仅 RENAMED 场景写入，便于报告页对照。
--       * file_path / old_path 列宽 1024（design.md §7.2 的 512 不足以容纳
--         monorepo 深层路径；与 B3-D 扫描器输入一致）。
--   - 索引：
--       uk_diff_file_task_path ：(task_id, file_path) 唯一，DiffParser 重试
--         时通过 DELETE FROM diff_file WHERE task_id=? 清理后重写，唯一约束兜底；
--       idx_diff_file_task     ：报告聚合常用过滤列。
--   - 外键 ON DELETE CASCADE：review_task 删除时级联清理 diff_file，
--     与 task_log 保持一致策略。
--
-- 编码：UTF-8 (no BOM)
-- =====================================================================

CREATE TABLE diff_file (
    id                   BIGSERIAL    PRIMARY KEY,
    task_id              BIGINT       NOT NULL REFERENCES review_task(id) ON DELETE CASCADE,
    file_path            VARCHAR(1024) NOT NULL,
    change_type          VARCHAR(16)   NOT NULL
                         CHECK (change_type IN ('ADDED','MODIFIED','DELETED','RENAMED')),
    old_path             VARCHAR(1024),
    added_lines          INT          NOT NULL DEFAULT 0 CHECK (added_lines   >= 0),
    deleted_lines        INT          NOT NULL DEFAULT 0 CHECK (deleted_lines >= 0),
    total_changed_lines  INT          NOT NULL DEFAULT 0 CHECK (total_changed_lines >= 0),
    oversized            BOOLEAN      NOT NULL DEFAULT FALSE,
    hunks                JSONB,
    patch                TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_diff_file_task_path UNIQUE (task_id, file_path)
);

CREATE INDEX idx_diff_file_task ON diff_file(task_id);

COMMENT ON TABLE  diff_file                     IS '评审任务变更文件（M04 / R10.2 / R10.3）。任务删除时级联清理。';
COMMENT ON COLUMN diff_file.task_id             IS '关联评审任务主键。';
COMMENT ON COLUMN diff_file.file_path           IS '变更后文件路径（RENAMED 时为新路径）。';
COMMENT ON COLUMN diff_file.change_type         IS 'ADDED / MODIFIED / DELETED / RENAMED。';
COMMENT ON COLUMN diff_file.old_path            IS 'RENAMED 场景下的变更前路径，其余场景为 NULL。';
COMMENT ON COLUMN diff_file.added_lines         IS '新增行数（不含 +++ 头行）。';
COMMENT ON COLUMN diff_file.deleted_lines       IS '删除行数（不含 --- 头行）。';
COMMENT ON COLUMN diff_file.total_changed_lines IS 'added_lines + deleted_lines（冗余存储以加速聚合查询）。';
COMMENT ON COLUMN diff_file.oversized           IS 'TRUE 表示超过 diff.maxLinesPerFile（默认 5000），AI 阶段跳过该文件（R10.5）。';
COMMENT ON COLUMN diff_file.hunks               IS 'JSONB 形式的 hunk 列表 [{oldStart,oldLines,newStart,newLines,lines:[{type,content,oldLineNo,newLineNo}]}]。';
COMMENT ON COLUMN diff_file.patch               IS '原始 unified diff 文本（archival 用途）。';

-- end V31__m04_diff_file.sql
