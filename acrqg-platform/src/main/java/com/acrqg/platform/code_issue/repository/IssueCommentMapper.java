package com.acrqg.platform.code_issue.repository;

import com.acrqg.platform.code_issue.domain.IssueComment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 问题评论 Mapper。
 *
 * <p>由 B4-A {@code IssueService} 在添加评论时写入；本 Mapper 提供按问题
 * id 倒序列出评论的查询。
 *
 * <p>Covers: R16.3。
 */
public interface IssueCommentMapper extends BaseMapper<IssueComment> {

    /**
     * 列出指定问题的评论，按 created_at 倒序。
     *
     * @param codeIssueId 问题主键
     * @return 评论列表（可能为空）
     */
    @Select("""
            SELECT id, code_issue_id, content, operator_id, created_at
              FROM issue_comment
             WHERE code_issue_id = #{codeIssueId}
             ORDER BY created_at DESC, id DESC
            """)
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "code_issue_id", property = "codeIssueId"),
            @Result(column = "content", property = "content"),
            @Result(column = "operator_id", property = "operatorId"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<IssueComment> listByIssue(@Param("codeIssueId") Long codeIssueId);
}
