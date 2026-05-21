package com.acrqg.platform.diff.service;

import com.acrqg.platform.diff.dto.DiffParseResult;
import com.acrqg.platform.repository.dto.DiffPayload;

/**
 * Diff 解析服务（design.md §6.4 / R10）。
 *
 * <p>{@link #parse} 串联完整链路：
 * <ol>
 *   <li>读取 {@code review_task} 与对应 {@code repository_binding}；</li>
 *   <li>通过 {@code RepositoryService.decryptAccessToken} 取明文 token；</li>
 *   <li>调用 {@code ProviderClient.fetchDiff} 拉取 diff；</li>
 *   <li>解析 unified diff，写入 {@code diff_file}；</li>
 *   <li>返回任务级 {@link DiffParseResult}。</li>
 * </ol>
 *
 * <p>{@link #parseFromPayload} 拆分纯解析逻辑，便于属性测试 P7（design §19）
 * 与单元测试，无需 mock ProviderClient。
 *
 * <p>Covers: R10.1, R10.2, R10.3, R10.4, R10.5。
 */
public interface DiffParser {

    /**
     * 完整执行 diff 拉取 + 解析 + 落库。
     *
     * <p>失败语义：网络 / HTTP 异常 → 写一条 ERROR task_log，抛
     * {@link com.acrqg.platform.repository.client.DiffFetchException}
     * 由 {@link com.acrqg.platform.task.worker.TaskOrchestrator} 接管转
     * EXECUTION_FAILED（R10.4 / R9.2）。
     *
     * <p>重试场景（{@code review_task.attempt > 1}）下，先
     * {@code DELETE FROM diff_file WHERE task_id=?} 清理旧记录，再批量插入新记录，
     * 避免 {@code uk_diff_file_task_path} 唯一约束冲突。
     *
     * @param taskId 任务主键
     * @return 解析结果
     */
    DiffParseResult parse(Long taskId);

    /**
     * 仅解析载荷，不访问网络也不写库。
     *
     * <p>用途：
     * <ul>
     *   <li>属性测试 P7：自定义生成 {@link DiffPayload} 后断言行数一致性；</li>
     *   <li>单元测试：mock ProviderClient 后验证解析逻辑。</li>
     * </ul>
     *
     * <p>方法纯函数：相同输入恒等输出，无副作用。
     *
     * @param payload 已经拉取好的载荷
     * @return 解析结果
     */
    DiffParseResult parseFromPayload(DiffPayload payload);
}
