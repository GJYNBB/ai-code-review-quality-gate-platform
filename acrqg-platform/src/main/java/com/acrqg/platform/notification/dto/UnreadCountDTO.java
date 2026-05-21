package com.acrqg.platform.notification.dto;

/**
 * 未读通知数视图。
 *
 * <p>由 {@code GET /api/v1/notifications/unread-count} 返回；前端头部红点
 * 30s 轮询使用（design.md §6.6 / UI-002 / R19.3）。
 *
 * @param count 当前用户未读通知总数（{@code >= 0}）
 */
public record UnreadCountDTO(long count) {
}
