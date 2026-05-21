package com.acrqg.platform.task.worker;

import com.acrqg.platform.admin.domain.SystemParam;
import com.acrqg.platform.admin.repository.SystemParamMapper;
import com.acrqg.platform.diff.dto.DiffParseResult;
import com.acrqg.platform.diff.service.DiffParser;
import com.acrqg.platform.task.domain.ReviewTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * FETCHING_DIFF 阶段实现（B3-C.5）。
 *
 * <p>调用 {@link DiffParser#parse(Long)} 完成 diff 拉取 + 解析 + 落库；成功后
 * 返回 {@link ReviewTaskStatus#STATIC_SCANNING}。
 *
 * <p>失败语义：DiffParser 在网络 / HTTP 异常时抛
 * {@link com.acrqg.platform.repository.client.DiffFetchException}，
 * 阶段不主动捕获——交由 {@link TaskOrchestrator} 统一转为 EXECUTION_FAILED 并
 * 写 ERROR 级 task_log（DiffParserImpl 内部已写过一条更详细的 ERROR）。
 *
 * <p>{@link #timeoutSeconds()}：默认 120 s，可由 system_param 中的
 * {@code diff.fetch.timeout.seconds} 覆盖（启动期读取一次；越界 / 缺失退化为默认）。
 *
 * <p>仅在 {@code worker} profile 下注册 bean：api 进程无 stage 实现，
 * Orchestrator 调度时会立即走 EXECUTION_FAILED 兜底（防止 api 进程被错误地
 * 用于消费 Stream）。
 *
 * <p>Covers: R9.1, R10。
 */
@Component
@Profile("worker")
public class FetchingDiffStage implements TaskStage {

    private static final Logger log = LoggerFactory.getLogger(FetchingDiffStage.class);

    /** system_param 中存储 fetch timeout 的 key。 */
    static final String PARAM_FETCH_TIMEOUT = "diff.fetch.timeout.seconds";

    /** 默认超时秒数（与 design.md §6.4 / B3-C.5 一致）。 */
    static final long DEFAULT_TIMEOUT_SECONDS = 120L;

    private final DiffParser diffParser;
    private final SystemParamMapper systemParamMapper;

    public FetchingDiffStage(DiffParser diffParser,
                             SystemParamMapper systemParamMapper) {
        this.diffParser = diffParser;
        this.systemParamMapper = systemParamMapper;
    }

    @Override
    public ReviewTaskStatus stage() {
        return ReviewTaskStatus.FETCHING_DIFF;
    }

    @Override
    public ReviewTaskStatus next(StageContext ctx) {
        long taskId = ctx.taskId();
        DiffParseResult result = diffParser.parse(taskId);
        if (log.isDebugEnabled()) {
            log.debug("FetchingDiffStage: taskId={} files={} added={} deleted={}",
                    taskId,
                    result.changedFileCount(),
                    result.totalAddedLines(),
                    result.totalDeletedLines());
        }
        return ReviewTaskStatus.STATIC_SCANNING;
    }

    @Override
    public long timeoutSeconds() {
        return readTimeoutSecondsOrDefault();
    }

    private long readTimeoutSecondsOrDefault() {
        try {
            SystemParam sp = systemParamMapper.selectByKey(PARAM_FETCH_TIMEOUT);
            if (sp == null || sp.getParamValue() == null) {
                return DEFAULT_TIMEOUT_SECONDS;
            }
            long v = Long.parseLong(sp.getParamValue().trim());
            if (v <= 0) {
                return DEFAULT_TIMEOUT_SECONDS;
            }
            return v;
        } catch (RuntimeException ex) {
            log.warn("readTimeoutSecondsOrDefault fallback: {}", ex.toString());
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }
}
