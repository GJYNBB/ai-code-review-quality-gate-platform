package com.acrqg.platform.common.exception;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.api.FieldError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>对应 design.md §8.3 与 §16.1 错误码表。负责将各类异常映射为统一的
 * {@link ApiResponse} 失败响应与正确的 HTTP 状态码。
 *
 * <p>处理矩阵：
 * <ul>
 *   <li>{@link BusinessException}：按 {@link ErrorCode#getHttpStatus()} 返回，
 *       消息使用异常自带的 {@code message}。</li>
 *   <li>{@link MethodArgumentNotValidException}：{@code @Valid @RequestBody} 校验失败，
 *       400 + {@link ErrorCode#VALIDATION_ERROR} + {@code details}。</li>
 *   <li>{@link ConstraintViolationException}：路径变量 / 查询参数级别的
 *       {@code @Validated} 校验失败，400 + {@link ErrorCode#VALIDATION_ERROR} + {@code details}。</li>
 *   <li>{@link MissingServletRequestParameterException}：必填查询参数缺失，
 *       400 + {@link ErrorCode#VALIDATION_ERROR}。</li>
 *   <li>{@link HttpMessageNotReadableException}：请求体不可解析（JSON 格式错误等），
 *       400 + {@link ErrorCode#VALIDATION_ERROR}。</li>
 *   <li>{@link AccessDeniedException}：Spring Security 抛出的授权失败，
 *       403 + {@link ErrorCode#PERMISSION_DENIED}。</li>
 *   <li>{@link Exception}（兜底）：500 + {@link ErrorCode#INTERNAL_ERROR}；
 *       记录完整堆栈到 ERROR 级别日志，但 <strong>不</strong> 在响应体中暴露堆栈
 *       （R23.3 "敏感字段不在响应中泄漏"扩展约束）。</li>
 * </ul>
 *
 * <p>Covers: R1.4, R2.1, R3.3, R23.1, R23.3 (响应体中不泄漏内部异常细节)。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。HTTP 状态来自 {@link ErrorCode#getHttpStatus()}。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        // 业务异常通常是预期内的；记录 WARN 即可，避免被告警系统按 ERROR 误报。
        log.warn("BusinessException: code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getCode().getHttpStatus())
                .body(ApiResponse.failure(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    /**
     * 处理 {@code @Valid @RequestBody} 校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleArgumentNotValid(MethodArgumentNotValidException ex) {
        List<FieldError> details = new ArrayList<>();
        for (org.springframework.validation.FieldError fe : ex.getBindingResult().getFieldErrors()) {
            String reason = fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage();
            details.add(new FieldError(fe.getField(), reason));
        }
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), details));
    }

    /**
     * 处理 {@code @Validated} 在路径变量 / 查询参数 / 方法参数上的约束违反。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldError> details = new ArrayList<>();
        Iterator<ConstraintViolation<?>> it = ex.getConstraintViolations().iterator();
        while (it.hasNext()) {
            ConstraintViolation<?> cv = it.next();
            // propertyPath 类似 "createUser.arg0.name"；取其末段作为 field 名以减少噪音
            String path = cv.getPropertyPath() == null ? "" : cv.getPropertyPath().toString();
            int dotIdx = path.lastIndexOf('.');
            String field = dotIdx >= 0 ? path.substring(dotIdx + 1) : path;
            details.add(new FieldError(field, cv.getMessage()));
        }
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), details));
    }

    /**
     * 处理必填查询参数缺失。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        List<FieldError> details = List.of(new FieldError(ex.getParameterName(), "缺少必填参数"));
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR,
                        "缺少必填参数: " + ex.getParameterName(), details));
    }

    /**
     * 处理请求体不可读（JSON 解析失败、字段类型不匹配等）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("HttpMessageNotReadable: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR, "请求体格式不正确", null));
    }

    /**
     * 处理 Spring Security 抛出的授权失败。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        // 授权失败属可预期；记录 WARN，便于安全审计（不至于被 ERROR 噪声淹没）
        log.warn("AccessDenied: {}", ex.getMessage());
        String message = ex.getMessage() == null ? ErrorCode.PERMISSION_DENIED.getMessage() : ex.getMessage();
        return ResponseEntity
                .status(ErrorCode.PERMISSION_DENIED.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.PERMISSION_DENIED, message, null));
    }

    /**
     * 兜底处理其他未捕获异常。
     *
     * <p>记录完整堆栈到日志（ERROR 级别），但响应体不暴露任何内部细节，
     * 仅返回 {@link ErrorCode#INTERNAL_ERROR} 默认文案，符合 R23.3 的脱敏约束。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), null));
    }
}
