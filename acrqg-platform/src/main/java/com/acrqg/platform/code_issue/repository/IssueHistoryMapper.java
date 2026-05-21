package com.acrqg.platform.code_issue.repository;

import com.acrqg.platform.code_issue.domain.IssueHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 问题状态历史 Mapper。
 *
 * <p>主要用例由 B4-A {@code IssueService} 在状态流转时追加；本 Mapper 提供按问题
 * id 倒序列出历史的查询。
 *
 * <p>Covers: R17。
 */
public interface IssueHistoryMapper extends BaseMapper<IssueHistory> {

    /**
     * 列出指定问题的状态历史，按 changed_at 倒序。
     *
     * @param codeIssueId 问题主键
     * @return 历史列表（可能为空）
     */
    @Select("""
            SELECT id, code_issue_id, from_status, to_status, comment,
                   operator_id, changed_at
              FROM issue_history
             WHERE code_issue_id = #{codeIssueId}
             ORDER BY changed_at DESC, id DESC
            """)
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "code_issue_id", property = "codeIssueId"),
            @Result(column = "from_status", property = "fromStatus"),
            @Result(column = "to_status", property = "toStatus"),
            @Result(column = "comment", property = "comment"),
            @Result(column = "operator_id", property = "operatorId"),
            @Result(column = "changed_at", property = "changedAt")
    })
    List<IssueHistory> listByIssue(@Param("codeIssueId") Long codeIssueId);
}
