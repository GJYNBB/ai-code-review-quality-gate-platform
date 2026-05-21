package com.acrqg.platform.webhook.dto;

/**
 * Webhook 处理结果 DTO（B3-B.3）。
 *
 * <p>{@link #ignored} 为 {@code true} 表示事件被有意忽略（PING / OTHER 类型，
 * 或者绑定状态异常但不应当 fail-fast）；此时 {@link #taskId} 与 {@link #status}
 * 通常为 {@code null}，{@link #reason} 给出原因。
 *
 * <p>{@link #ignored} 为 {@code false} 表示事件已被路由到任务创建链路，
 * {@link #taskId} 为创建（或幂等命中）的任务 id，{@link #status} 为该任务的
 * 当前状态字符串（{@code PENDING} / {@code FETCHING_DIFF} / ...）。
 *
 * <p>本 DTO 通过 {@code WebhookController} 直接以 JSON 返回给上游平台；
 * 字段顺序按重要程度排列，便于人肉 diagnostic。
 *
 * <p>Covers: R7.3, R7.4, R7.5, R7.6。
 *
 * @param ignored 是否忽略
 * @param taskId  关联的评审任务 id；忽略时为 {@code null}
 * @param status  关联评审任务的当前状态；忽略时为 {@code null}
 * @param reason  忽略原因或简短描述
 */
public record WebhookHandleResult(
        boolean ignored,
        Long taskId,
        String status,
        String reason) {

    /** 构造一个 ignored 结果。 */
    public static WebhookHandleResult ignored(String reason) {
        return new WebhookHandleResult(true, null, null, reason);
    }

    /** 构造一个已创建任务的结果。 */
    public static WebhookHandleResult ofCreated(Long taskId, String status) {
        return new WebhookHandleResult(false, taskId, status, "task created");
    }

    /** 构造一个幂等命中的结果（任务已存在）。 */
    public static WebhookHandleResult ofIdempotent(Long taskId, String status) {
        return new WebhookHandleResult(false, taskId, status, "idempotent hit");
    }
}
