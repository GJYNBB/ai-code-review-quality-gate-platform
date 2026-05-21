package com.acrqg.platform.task.worker;

import com.acrqg.platform.scanner.ScannerOrchestrator;
import com.acrqg.platform.task.domain.ReviewTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * STATIC_SCANNING 阶段实现（B3-D.6）。
 *
 * <p>调用 {@link ScannerOrchestrator#scan(Long)} 完成多扫描器编排。失败语义：
 * <ul>
 *   <li>单扫描器失败已在 {@link ScannerOrchestrator} 内捕获并写 WARN task_log（R11.4）；</li>
 *   <li>编排器整体抛出的 {@link RuntimeException}（如任务/项目缺失、批量插入失败）
 *       不被本阶段捕获——交由 {@link TaskOrchestrator} 统一转为 EXECUTION_FAILED。</li>
 * </ul>
 *
 * <p>无论扫描出多少 issue，都进入 {@link ReviewTaskStatus#AI_REVIEWING}（R9.1）。
 *
 * <p>仅在 {@code worker} profile 下注册 bean。
 *
 * <p>Covers: R9.1, R11。
 */
@Component
@Profile("worker")
public class StaticScanningStage implements TaskStage {

    private static final Logger log = LoggerFactory.getLogger(StaticScanningStage.class);

    private final ScannerOrchestrator scannerOrchestrator;

    public StaticScanningStage(ScannerOrchestrator scannerOrchestrator) {
        this.scannerOrchestrator = scannerOrchestrator;
    }

    @Override
    public ReviewTaskStatus stage() {
        return ReviewTaskStatus.STATIC_SCANNING;
    }

    @Override
    public ReviewTaskStatus next(StageContext ctx) {
        long taskId = ctx.taskId();
        int inserted = scannerOrchestrator.scan(taskId);
        if (log.isDebugEnabled()) {
            log.debug("StaticScanningStage: taskId={} attempt={} insertedIssues={}",
                    taskId, ctx.attempt(), inserted);
        }
        return ReviewTaskStatus.AI_REVIEWING;
    }
}
