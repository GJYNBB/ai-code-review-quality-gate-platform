package com.acrqg.platform.common.exception;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.api.FieldError;
import java.util.List;

/**
 * 业务异常。
 *
 * <p>所有可预期的业务错误（如参数非法、资源不存在、权限不足等）应抛出该异常
 * 而非直接返回 {@code ApiResponse.failure(...)}，由 {@code GlobalExceptionHandler}
 * 统一映射为 HTTP 状态码与响应体；这样能够：
 * <ul>
 *   <li>避免控制器层手写 {@code ResponseEntity} 模板代码；</li>
 *   <li>在切面（事务、审计、日志）层统一捕获，事务可正确回滚；</li>
 *   <li>保证 {@code ApiResponse.requestId} 等字段始终从 MDC 注入。</li>
 * </ul>
 *
 * <p>承载的 {@link #details} 字段在 Bean Validation 之外的业务级字段错误（例如
 * {@code GATE_RULE_INVALID} 时定位到具体规则索引）使用，对应 design.md §13.3
 * "在 details 中指出违规规则索引"。
 *
 * <p>Covers: 全局错误码（R1~R22 引用），R13.3 details 字段诊断。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final List<FieldError> details;

    /** 使用错误码的默认文案。 */
    public BusinessException(ErrorCode code) {
        super(code.getMessage());
        this.code = code;
        this.details = null;
    }

    /** 使用错误码的默认 HTTP 状态，但覆盖响应文案。 */
    public BusinessException(ErrorCode code, String customMessage) {
        super(customMessage);
        this.code = code;
        this.details = null;
    }

    /** 携带字段级错误明细。 */
    public BusinessException(ErrorCode code, String customMessage, List<FieldError> details) {
        super(customMessage);
        this.code = code;
        this.details = details;
    }

    /** 包装下游异常（保留 cause 用于日志）。 */
    public BusinessException(ErrorCode code, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.code = code;
        this.details = null;
    }

    /** 工厂方法：使用错误码默认文案抛出。 */
    public static BusinessException of(ErrorCode code) {
        return new BusinessException(code);
    }

    /** 工厂方法：使用错误码并覆盖文案。 */
    public static BusinessException of(ErrorCode code, String customMessage) {
        return new BusinessException(code, customMessage);
    }

    public ErrorCode getCode() {
        return code;
    }

    /** 字段级错误明细，可能为 {@code null}。 */
    public List<FieldError> getDetails() {
        return details;
    }
}
