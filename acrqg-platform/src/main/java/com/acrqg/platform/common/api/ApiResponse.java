package com.acrqg.platform.common.api;

import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * 平台统一响应包装。
 *
 * <p>所有 REST 控制器均通过该 record 返回 JSON。字段定义严格对齐
 * design.md §8.1 与 02 号 RESTful 接口设计文档：
 *
 * <pre>
 * {
 *   "code": 0,                         // 成功为整数 0；失败为字符串错误码
 *   "message": "success",
 *   "data": { ... } | null,            // 业务负载（成功时非空）
 *   "details": [                       // 失败时按字段返回的 FieldError 列表（成功时为 null）
 *      { "field": "name", "reason": "不能为空" }
 *   ],
 *   "requestId": "550e8400-..."        // 链路追踪 ID（取自 MDC.traceId）
 * }
 * </pre>
 *
 * <p>{@code code} 使用 {@link Object} 类型而非泛型常量，是为了在同一字段中既能承载
 * 成功的 {@link Integer} {@code 0} 又能承载失败的 {@link String} 错误码（与
 * {@link ErrorCode#getCode()} 的设计一致）。
 *
 * <p>{@code requestId} 由 {@link MDC#get(String)} 读取键 {@code "traceId"} 取得；
 * 当 {@code TraceIdFilter}（B0-A.8 实现）尚未介入或当前处于 Worker 入口外时，
 * MDC 可能为空，此时退化为生成一个新的 UUID 字符串作为兜底，保证响应字段始终非空。
 *
 * <p>Covers: R23.1 (统一响应), R23.3 (敏感字段不在响应中泄漏), R24.5 (按 traceId 串联链路)。
 *
 * @param <T>       业务负载类型
 * @param code      0（成功）或字符串错误码
 * @param message   响应文案
 * @param data      业务负载，失败时为 {@code null}
 * @param details   字段级错误明细，仅校验失败时非 {@code null}
 * @param requestId 链路追踪 ID，与日志中的 {@code traceId} 字段对齐
 */
public record ApiResponse<T>(
        Object code,
        String message,
        T data,
        List<FieldError> details,
        String requestId
) {

    /** MDC 中保存 traceId 的键名，与 {@code TraceIdFilter} / {@code logback-spring.xml} 对齐。 */
    private static final String MDC_TRACE_ID = "traceId";

    /**
     * 构造一个成功响应。
     *
     * <p>{@code code=0, message="success"}，{@code details} 为空。
     *
     * @param data 业务负载，可为 {@code null}
     * @param <T>  业务负载类型
     * @return 成功响应包装
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), "success", data, null, currentRequestId());
    }

    /**
     * 构造一个失败响应。
     *
     * <p>{@code code} 来自 {@link ErrorCode#getCode()}，{@code data=null}。
     *
     * @param code    错误码
     * @param message 错误文案；建议使用人类可读的中文短句
     * @param details 字段级错误明细；不存在时传 {@code null}
     * @param <T>     业务负载类型（失败时无意义，仅用于统一返回类型）
     * @return 失败响应包装
     */
    public static <T> ApiResponse<T> failure(ErrorCode code, String message, List<FieldError> details) {
        return new ApiResponse<>(code.getCode(), message, null, details, currentRequestId());
    }

    /**
     * 读取当前请求的 traceId；当 MDC 未设置时退化为生成新的 UUID。
     *
     * <p>该方法不会抛出异常；当 SLF4J 实现不支持 MDC（极少见）或 MDC 中无 traceId 时，
     * 直接返回 UUID 字符串以保证 {@link #requestId()} 字段始终可用。
     */
    private static String currentRequestId() {
        String traceId = MDC.get(MDC_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return traceId;
    }
}
