package com.acrqg.platform.common.api;

/**
 * 平台统一错误码枚举。
 *
 * <p>每个错误码包含三个属性：
 * <ul>
 *   <li>{@code code}：响应体 {@code code} 字段的取值。{@link #SUCCESS} 为 {@link Integer}
 *       字面量 {@code 0}（与 02 号 RESTful 接口设计文档保持一致）；其余为
 *       {@link String} 字面量（如 {@code "AUTH_INVALID_CREDENTIALS"}），便于前端按字符串
 *       匹配错误码。因此这里使用 {@link Object} 作为 {@code code} 字段类型。</li>
 *   <li>{@code message}：默认中文文案，可被 {@code BusinessException} 在抛出时覆盖。</li>
 *   <li>{@code httpStatus}：与 design.md §16.1 错误码表一一对应的 HTTP 状态码。</li>
 * </ul>
 *
 * <p>HTTP 状态映射严格对齐 design.md §16.1：
 * <pre>
 * SUCCESS                    -> 200
 * VALIDATION_ERROR           -> 400
 * GATE_RULE_INVALID          -> 400
 * AUTH_INVALID_CREDENTIALS   -> 401
 * AUTH_INVALID_TOKEN         -> 401
 * AUTH_ACCOUNT_DISABLED      -> 401
 * WEBHOOK_SIGNATURE_INVALID  -> 401
 * PERMISSION_DENIED          -> 403
 * TASK_NOT_FOUND             -> 404
 * PROJECT_NAME_EXISTS        -> 409
 * TASK_DUPLICATED            -> 409
 * TASK_NOT_RETRYABLE         -> 409
 * WAIVER_DUPLICATED          -> 409
 * REPOSITORY_UNREACHABLE     -> 422
 * INTERNAL_ERROR             -> 500
 * AI_SERVICE_UNAVAILABLE     -> 503
 * </pre>
 *
 * <p>Covers: R1.2, R1.3, R1.4, R3.2, R3.3, R4.2, R4.3, R5.2, R6.2, R7.2,
 * R8.2, R8.3, R9.4, R9.5, R12.5, R13.3, R14.7, R15.2, R15.6, R17, R18.2,
 * R18.4, R19.4, R20.3, R20.4, R21.4, R21.6, R23.1.
 */
public enum ErrorCode {

    /** 成功，HTTP 200。code 字段为整数 {@code 0}。 */
    SUCCESS(Integer.valueOf(0), "success", 200),

    /** R1.2 用户名或密码错误，HTTP 401。 */
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "用户名或密码错误", 401),

    /** R1.4 / R3.2 访问令牌无效或已过期，HTTP 401。 */
    AUTH_INVALID_TOKEN("AUTH_INVALID_TOKEN", "访问令牌无效或已过期", 401),

    /** R1.3 账号已被禁用，HTTP 401。 */
    AUTH_ACCOUNT_DISABLED("AUTH_ACCOUNT_DISABLED", "账号已被禁用", 401),

    /** R2.1 / R3.3 / R4.4 / R6 / R18.4 / R19.4 / R21.6 权限不足，HTTP 403。 */
    PERMISSION_DENIED("PERMISSION_DENIED", "权限不足", 403),

    /** R3 / R4.3 / R6.2 / R8.2 / R15.2 / R17 / R18.2 / R21.4 参数校验失败，HTTP 400。 */
    VALIDATION_ERROR("VALIDATION_ERROR", "参数校验失败", 400),

    /** R4.2 项目名称已存在，HTTP 409。 */
    PROJECT_NAME_EXISTS("PROJECT_NAME_EXISTS", "项目名称已存在", 409),

    /** R5.2 仓库不可访问，HTTP 422。 */
    REPOSITORY_UNREACHABLE("REPOSITORY_UNREACHABLE", "仓库不可访问", 422),

    /** R7.2 Webhook 签名校验失败，HTTP 401。 */
    WEBHOOK_SIGNATURE_INVALID("WEBHOOK_SIGNATURE_INVALID", "Webhook 签名校验失败", 401),

    /** R8.3 评审任务重复，HTTP 409。 */
    TASK_DUPLICATED("TASK_DUPLICATED", "评审任务重复", 409),

    /** R9.5 当前任务状态不可重试，HTTP 409。 */
    TASK_NOT_RETRYABLE("TASK_NOT_RETRYABLE", "当前任务状态不可重试", 409),

    /** 任务不存在，HTTP 404。 */
    TASK_NOT_FOUND("TASK_NOT_FOUND", "任务不存在", 404),

    /** R12.5 AI 服务不可用，HTTP 503。 */
    AI_SERVICE_UNAVAILABLE("AI_SERVICE_UNAVAILABLE", "AI 服务不可用", 503),

    /** R13.3 门禁规则配置非法，HTTP 400。 */
    GATE_RULE_INVALID("GATE_RULE_INVALID", "门禁规则配置非法", 400),

    /** R15.6 已存在有效的豁免申请，HTTP 409。 */
    WAIVER_DUPLICATED("WAIVER_DUPLICATED", "已存在有效的豁免申请", 409),

    /** 系统未捕获的内部异常兜底，HTTP 500。 */
    INTERNAL_ERROR("INTERNAL_ERROR", "系统繁忙，请稍后再试", 500);

    private final Object code;
    private final String message;
    private final int httpStatus;

    ErrorCode(Object code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    /**
     * 响应体 {@code code} 字段的取值。{@link #SUCCESS} 返回 {@link Integer} {@code 0}，
     * 其余错误码返回 {@link String}。
     */
    public Object getCode() {
        return code;
    }

    /** 默认错误文案（中文）。 */
    public String getMessage() {
        return message;
    }

    /** 与 design.md §16.1 对齐的 HTTP 状态码。 */
    public int getHttpStatus() {
        return httpStatus;
    }
}
