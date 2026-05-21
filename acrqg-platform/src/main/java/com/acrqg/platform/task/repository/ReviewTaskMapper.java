package com.acrqg.platform.task.repository;

import com.acrqg.platform.task.domain.ReviewTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

/**
 * 评审任务 Mapper。
 *
 * <p>继承 {@link BaseMapper} 享受标准 CRUD；额外提供以下自定义查询：
 * <ul>
 *   <li>{@link #findActiveByTriple} —— 按三元组 {@code (projectId, prId, commitSha)}
 *       查找当前活跃任务（非 EXECUTION_FAILED）。{@code prId} 允许 {@code null}（直接 push 触发场景）。
 *       支撑 R7.4 / R8.3 幂等去重。</li>
 *   <li>{@link #updateStatusOnlyIfFrom} —— 原子 CAS 更新状态：
 *       仅当当前状态等于 {@code from} 时才把状态改为 {@code to}，
 *       同时刷新 {@code updated_at}。返回受影响行数（0 表示并发抢占或非法迁移）。</li>
 *   <li>{@link #selectByTaskNo} —— 按业务编号 {@code task_no} 查询。</li>
 *   <li>{@link #findStuckTasks} —— 中间执行态扫描，供
 *       {@link com.acrqg.platform.task.worker.TaskRecoveryRunner} 启动期断点恢复使用。</li>
 *   <li>{@link #pageProjects} / {@link #countProjects} —— 按项目集合 + 可选过滤
 *       条件分页列出任务，支持基于成员关系的访问控制（Service 层组装 projectIds 后传入）。</li>
 *   <li>{@link #setStartedAtIfNull} / {@link #setFinishedAt} —— 计时字段维护，
 *       与状态机解耦（避免每次 transit 都附带时间字段更新）。</li>
 * </ul>
 *
 * <p>所有 SQL 通过 {@code #{}} 占位符绑定参数；CAS 更新依赖 PostgreSQL 的
 * MVCC + WHERE 子句的最近提交可见性，无需额外锁。
 *
 * <p>Covers: R7.4, R8.3, R9.1, R9.3, R9.7, R24.4。
 */
public interface ReviewTaskMapper extends BaseMapper<ReviewTask> {

    /**
     * 按三元组查找活跃任务。
     *
     * <p>"活跃"定义：{@code status != 'EXECUTION_FAILED'}。这与 design.md §6.3 的
     * 幂等语义一致——执行失败后的同一三元组允许 retry / 重新创建（R9.4）。
     * 同三元组下若由于并发或业务问题存在多条非失败任务（理论上被
     * {@code uk_review_task_triple} 约束阻止），按 {@code id ASC} 返回第一条。
     *
     * @param projectId 项目主键，非空
     * @param prId      PR/MR 编号，可空
     * @param commitSha commit SHA，非空
     * @return 命中时返回任务；否则返回 {@code null}
     */
    @SelectProvider(type = ReviewTaskSqlProvider.class, method = "findActiveByTripleSql")
    ReviewTask findActiveByTriple(@Param("projectId") Long projectId,
                                  @Param("prId") String prId,
                                  @Param("commitSha") String commitSha);

    /**
     * 原子 CAS 更新状态。
     *
     * <p>SQL：{@code UPDATE review_task SET status=#{to}, updated_at=NOW()
     * WHERE id=#{id} AND status=#{from}}。
     * 返回受影响行数；0 表示
     * <ul>
     *   <li>当前状态已不等于 {@code from}（被其他线程抢占），或</li>
     *   <li>记录不存在。</li>
     * </ul>
     *
     * <p>Service 层应基于此实现幂等的状态迁移（结合 {@code StateMachine.tryTransit}
     * 在 CAS 前做合法性校验）。
     *
     * @param id   任务主键
     * @param from 期望的当前状态
     * @param to   目标状态
     * @return 受影响行数（0 / 1）
     */
    @Update("UPDATE review_task SET status = #{to}, updated_at = NOW() "
            + "WHERE id = #{id} AND status = #{from}")
    int updateStatusOnlyIfFrom(@Param("id") Long id,
                               @Param("from") String from,
                               @Param("to") String to);

    /**
     * 按业务编号 {@code task_no} 查询。
     *
     * @param taskNo 业务编号
     * @return 命中返回任务；否则 {@code null}
     */
    @Select("SELECT * FROM review_task WHERE task_no = #{taskNo}")
    ReviewTask selectByTaskNo(@Param("taskNo") String taskNo);

    /**
     * 扫描所有处于中间执行态的任务（供启动期断点恢复使用）。
     *
     * <p>中间态：FETCHING_DIFF / STATIC_SCANNING / AI_REVIEWING / GATE_EVALUATING。
     */
    @Select("""
            SELECT * FROM review_task
             WHERE status IN ('FETCHING_DIFF','STATIC_SCANNING','AI_REVIEWING','GATE_EVALUATING')
             ORDER BY id ASC
            """)
    List<ReviewTask> findStuckTasks();

    /**
     * 按项目集合分页列出任务（{@code created_at DESC, id DESC}）。
     *
     * <p>当 {@code projectIds} 为空集合时，调用方应直接返回空页而不是触发 SQL（本方法
     * 在空集合时使用 {@code 1=0} 兜底，但不建议依赖该兜底）。
     *
     * @param projectIds  允许访问的项目主键集合
     * @param status      状态精确过滤（{@code null}/空 不过滤）
     * @param triggerType 触发来源精确过滤（{@code null}/空 不过滤）
     * @param projectId   单项目过滤（{@code null} 表示不过滤；非 null 时必须在 projectIds 内）
     * @param limit       每页条数
     * @param offset      偏移
     * @return 当前页任务列表
     */
    @SelectProvider(type = ReviewTaskSqlProvider.class, method = "pageProjectsSql")
    List<ReviewTask> pageProjects(@Param("projectIds") Collection<Long> projectIds,
                                  @Param("status") String status,
                                  @Param("triggerType") String triggerType,
                                  @Param("projectId") Long projectId,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    /** 与 {@link #pageProjects} 同条件下的总条数。 */
    @SelectProvider(type = ReviewTaskSqlProvider.class, method = "countProjectsSql")
    long countProjects(@Param("projectIds") Collection<Long> projectIds,
                       @Param("status") String status,
                       @Param("triggerType") String triggerType,
                       @Param("projectId") Long projectId);

    /**
     * 把 {@code started_at} 仅在为 NULL 时置为当前时间（首次进入执行态时调用）。
     *
     * @return 受影响行数（0 表示本任务已经记录过开始时间）
     */
    @Update("UPDATE review_task SET started_at = NOW(), updated_at = NOW() "
            + "WHERE id = #{id} AND started_at IS NULL")
    int setStartedAtIfNull(@Param("id") Long id);

    /**
     * 设置 {@code finished_at} 与可选的 {@code score} / {@code ai_risk_score} /
     * {@code ai_available}（终态时调用）。
     *
     * <p>所有可选字段在为 {@code null} 时不覆盖现有值，便于多个阶段分别回填。
     */
    @Update("""
            UPDATE review_task
               SET finished_at  = NOW(),
                   updated_at   = NOW(),
                   score        = COALESCE(#{score}, score),
                   ai_risk_score= COALESCE(#{aiRiskScore}, ai_risk_score),
                   ai_available = COALESCE(#{aiAvailable}, ai_available)
             WHERE id = #{id}
            """)
    int setFinishedAt(@Param("id") Long id,
                      @Param("score") Integer score,
                      @Param("aiRiskScore") Integer aiRiskScore,
                      @Param("aiAvailable") Boolean aiAvailable);

    /** 重试时把 attempt + 1 并重置 finished_at / score 等（仅当当前为终态时由 Service 调用）。 */
    @Update("UPDATE review_task SET attempt = attempt + 1, finished_at = NULL, "
            + "started_at = NULL, score = NULL, updated_at = NOW() WHERE id = #{id}")
    int incrementAttempt(@Param("id") Long id);

    /**
     * 写入 AI 阶段的中间结果（{@code ai_risk_score} / {@code ai_available}）。
     *
     * <p>不修改 {@code finished_at} 与 {@code status}（中间态调用）。所有可选字段
     * 在为 {@code null} 时不覆盖现有值，便于 AI 阶段在不同降级路径下分别回填：
     * <ul>
     *   <li>仅设 {@code aiAvailable=false}（敏感过滤失败 / 5xx 超时降级）；</li>
     *   <li>同时设 {@code aiAvailable=true} + {@code aiRiskScore}（成功路径）。</li>
     * </ul>
     *
     * <p>由 B3-E AiReviewService 调用。
     *
     * @return 受影响行数（0 表示任务不存在）
     */
    @Update("""
            UPDATE review_task
               SET ai_risk_score = COALESCE(#{aiRiskScore}, ai_risk_score),
                   ai_available  = COALESCE(#{aiAvailable},  ai_available),
                   updated_at    = NOW()
             WHERE id = #{id}
            """)
    int updateAiResult(@Param("id") Long id,
                       @Param("aiRiskScore") Integer aiRiskScore,
                       @Param("aiAvailable") Boolean aiAvailable);
}
