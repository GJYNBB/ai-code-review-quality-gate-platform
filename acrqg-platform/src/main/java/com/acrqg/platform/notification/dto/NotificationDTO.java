package com.acrqg.platform.notification.dto;

import java.time.OffsetDateTime;

/**
 * 站内通知对外视图（design.md §6.6 / §8.4）。
 *
 * <p>由 {@code NotificationService.page} 与 {@code NotificationController} 返回。
 * 所有字段对齐 {@code notification} 表，{@code link} / {@code relatedType} /
 * {@code relatedId} / {@code readAt} 在不存在时为 {@code null}。
 *
 * <p>Covers: R19.3, R19.4。
 *
 * @param id          主键
 * @param userId      收件人
 * @param type        通知类型（{@link com.acrqg.platform.notification.domain.NotificationType#name()}）
 * @param title       标题
 * @param body        正文
 * @param link        前端跳转路径，可为 {@code null}
 * @param read        是否已读
 * @param relatedType 业务关联资源类型（review_task / code_issue / gate_waiver）
 * @param relatedId   业务关联资源主键
 * @param createdAt   创建时间
 * @param readAt      首次置为已读的时间，可为 {@code null}
 */
public record NotificationDTO(
        Long id,
        Long userId,
        String type,
        String title,
        String body,
        String link,
        boolean read,
        String relatedType,
        Long relatedId,
        OffsetDateTime createdAt,
        OffsetDateTime readAt
) {
}
