package com.acrqg.platform.notification.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 通知分页查询入参（R19.3）。
 *
 * <p>所有过滤字段为 {@code null} 时表示不过滤；{@code page} 从 1 起，
 * {@code pageSize} 限制 1..100 与平台分页约定一致。
 *
 * <p>Covers: R19.3。
 *
 * @param type     通知类型精确过滤；与 {@link com.acrqg.platform.notification.domain.NotificationType}
 *                 取值一致
 * @param read     已读标记过滤：{@code true}=仅已读 / {@code false}=仅未读 /
 *                 {@code null}=不过滤
 * @param page     页码，{@code >= 1}
 * @param pageSize 每页条数，{@code 1..100}
 */
public record NotificationQuery(
        String type,
        Boolean read,
        @Min(1) int page,
        @Min(1) @Max(100) int pageSize
) {
}
