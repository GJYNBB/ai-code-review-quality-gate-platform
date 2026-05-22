package com.acrqg.platform.ai.exception;

import java.util.Collections;
import java.util.List;

/**
 * AI 响应 JSON Schema 校验失败。
 *
 * <p>由 {@link com.acrqg.platform.ai.schema.AiReviewSchemaValidator} 抛出；
 * {@code AiReviewService} 捕获后写 {@code task_log(level=WARN)} 并丢弃响应
 * （R12.4 不影响 SAST 结果），不阻塞任务继续推进。
 *
 * <p>{@link #violations} 携带具体校验错误列表，便于在 task_log.detail 中精确
 * 定位上游 AI 服务的"非法响应"细节，辅助运维排障。
 *
 * <p>Covers: R12.3, R12.4。
 */
public class SchemaValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 校验违规明细（不可修改列表）。 */
    private final List<String> violations;

    public SchemaValidationException(String message, List<String> violations) {
        super(message);
        this.violations = violations == null
                ? Collections.emptyList()
                : List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
