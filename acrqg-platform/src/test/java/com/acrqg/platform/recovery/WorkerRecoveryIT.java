package com.acrqg.platform.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.acrqg.platform.support.PostgresRedisTestBase;
import com.acrqg.platform.task.repository.ReviewTaskMapper;
import com.acrqg.platform.task.worker.TaskRecoveryRunner;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Worker 启动 / 中断 / 恢复 IT（B6-A.10，design.md §16.3 策略 A）。
 *
 * <p>测试流程：
 * <ol>
 *   <li>直接通过 {@link ReviewTaskMapper}（service 层简化）插入 5 个任务，并把
 *       状态置为 {@code STATIC_SCANNING}（模拟 worker 在阶段中崩溃）；</li>
 *   <li>显式调用 {@link TaskRecoveryRunner#run} 触发启动期断点恢复；</li>
 *   <li>断言：5 个任务全部转为 {@code EXECUTION_FAILED}；
 *       每个任务的 {@code task_log} 至少包含一条 message 含
 *       {@code "task interrupted by worker restart"} 的 WARN 记录；
 *       不得有任务跳到 {@code PASSED}。</li>
 * </ol>
 *
 * <p>本测试在 {@code worker} profile 下运行，确保 {@link TaskRecoveryRunner} 被装配
 * 到 ApplicationContext。但本测试不依赖 Redis Stream 消费者实际工作——它只验证
 * Runner 的"扫描 + 转终态 + 写流水"三步逻辑。
 *
 * <p>Covers: R24.4。
 */
@SpringBootTest
@ActiveProfiles({"test", "worker"})
@TestInstance(Lifecycle.PER_CLASS)
class WorkerRecoveryIT extends PostgresRedisTestBase {

    @Autowired DataSource dataSource;
    @Autowired ReviewTaskMapper reviewTaskMapper;
    @Autowired TaskRecoveryRunner taskRecoveryRunner;

    long adminUserId;
    long projectId;

    @BeforeAll
    void setupBase() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM \"user\" WHERE username = 'admin'", Long.class);
        this.adminUserId = adminId == null ? 0L : adminId;

        Long pid = jdbc.queryForObject(
                "SELECT id FROM project WHERE name = 'proj-it-recovery'", Long.class);
        if (pid == null) {
            jdbc.update("INSERT INTO project(name, default_branch, language, created_by) "
                    + "VALUES ('proj-it-recovery','main','Java', ?)", this.adminUserId);
            pid = jdbc.queryForObject(
                    "SELECT id FROM project WHERE name = 'proj-it-recovery'", Long.class);
        }
        this.projectId = pid == null ? 0L : pid;
    }

    @Test
    void recoveryRunner_marksStuckTasksFailedWithLogs() throws Exception {
        // 1) 提交 5 个任务，直接置 STATIC_SCANNING 状态
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long[] taskIds = new long[5];
        long batchKey = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            String taskNo = "RT-RECOV-" + batchKey + "-" + i;
            String sha = String.format("%040x", batchKey + i);
            jdbc.update("""
                    INSERT INTO review_task
                       (task_no, project_id, pr_id, source_branch, target_branch, commit_sha,
                        status, trigger_type, attempt, started_at, created_by,
                        created_at, updated_at)
                    VALUES (?, ?, ?, 'feat/x', 'main', ?,
                            'STATIC_SCANNING', 'MANUAL', 1, NOW() - INTERVAL '30 seconds', ?,
                            NOW() - INTERVAL '1 minute', NOW())
                    """, taskNo, projectId, "pr-" + i, sha, adminUserId);
            Long tid = jdbc.queryForObject(
                    "SELECT id FROM review_task WHERE task_no = ?", Long.class, taskNo);
            taskIds[i] = tid == null ? 0L : tid;
        }

        // 2) 调用恢复
        taskRecoveryRunner.run(new DefaultApplicationArguments());

        // 3) 断言
        for (long taskId : taskIds) {
            String status = jdbc.queryForObject(
                    "SELECT status FROM review_task WHERE id = ?", String.class, taskId);
            assertThat(status).as("task %s should be EXECUTION_FAILED", taskId)
                    .isEqualTo("EXECUTION_FAILED");

            Integer warnCount = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM task_log WHERE task_id = ? "
                            + "AND level = 'WARN' AND message ILIKE '%interrupted by worker restart%'",
                    Integer.class, taskId);
            assertThat(warnCount).as("task %s should have a WARN log entry", taskId)
                    .isNotNull().isPositive();
        }

        // 不得有任务转 PASSED
        for (long taskId : taskIds) {
            Integer passedCount = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM review_task WHERE id = ? AND status = 'PASSED'",
                    Integer.class, taskId);
            assertThat(passedCount == null ? 0 : passedCount)
                    .as("task %s must not be PASSED", taskId)
                    .isZero();
        }

        // findStuckTasks 现在应不再返回这些任务
        List<?> stillStuck = reviewTaskMapper.findStuckTasks();
        for (long taskId : taskIds) {
            assertThat(stillStuck.stream()
                    .anyMatch(t -> taskMatches(t, taskId)))
                    .as("task %s no longer in stuck list", taskId)
                    .isFalse();
        }
    }

    /** 反射兼容判断 ReviewTask.id == taskId（避免引入 cast 依赖）。 */
    private boolean taskMatches(Object t, long taskId) {
        try {
            Long id = (Long) t.getClass().getMethod("getId").invoke(t);
            return id != null && id == taskId;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}
