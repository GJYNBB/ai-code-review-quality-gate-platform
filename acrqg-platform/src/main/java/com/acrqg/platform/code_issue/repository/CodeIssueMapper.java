package com.acrqg.platform.code_issue.repository;

import com.acrqg.platform.code_issue.domain.CodeIssue;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 代码问题 Mapper（最小契约 / B3-E worktree 起点 stub）。
 *
 * <p><b>合并约定</b>：本接口在 B3-E worktree 起点是最小契约——只声明
 * {@link #insertBatch(List)}（B3-E AI 评审写入需要）以及继承 {@link BaseMapper}
 * 的标准 CRUD（B3-D 静态扫描写入需要）。当 B3-D 的完整版本合并进 develop 时，
 * 由集成 PR 解决冲突，B3-D 版本胜出，但 {@link #insertBatch} 与方法签名 / 表名
 * 必须保持兼容。
 *
 * <p>{@code task_log.detail} 等其它 JSONB 字段的 type handler 暂不需要。
 *
 * <p>Covers: R11.2, R12.3, R16.2。
 */
public interface CodeIssueMapper extends BaseMapper<CodeIssue> {

    /**
     * 批量插入代码问题。
     *
     * <p>使用 {@code INSERT INTO ... SELECT ... FROM unnest(...)} 在 PostgreSQL 上
     * 性能最优，但本最小 stub 采用 MyBatis 动态 SQL（更易迁移到 H2 测试环境）。
     * 受影响行数 = list.size()；当列表为空时直接返回 0，不发起 SQL。
     *
     * @param list 待插入列表
     * @return 受影响行数
     */
    @Insert("""
            <script>
              INSERT INTO code_issue (
                  task_id, file_path, line_no, rule_code, source, severity,
                  status, description, suggestion, confidence,
                  created_at, updated_at
              ) VALUES
              <foreach collection='list' item='it' separator=','>
                (
                  #{it.taskId}, #{it.filePath}, #{it.lineNo}, #{it.ruleCode},
                  #{it.source}, #{it.severity},
                  COALESCE(#{it.status}, 'NEW'),
                  #{it.description}, #{it.suggestion}, #{it.confidence},
                  COALESCE(#{it.createdAt}, NOW()), COALESCE(#{it.updatedAt}, NOW())
                )
              </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<CodeIssue> list);
}
