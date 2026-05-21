package com.acrqg.platform.notification.service;

import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.notification.domain.NotificationType;
import com.acrqg.platform.notification.dto.NotificationDTO;
import com.acrqg.platform.notification.dto.NotificationQuery;
import com.acrqg.platform.notification.dto.UnreadCountDTO;
import java.util.Collection;

/**
 * 站内通知服务（M09 / R19）。
 *
 * <p>对齐 design.md §6.6 与 §8.4。承担：
 * <ul>
 *   <li>发布：{@link #notify}（单收件人）/ {@link #notifyAll}（多收件人）；
 *       内部内置 60s 去重保护，避免同一业务事件短时重复轰炸。</li>
 *   <li>查询：{@link #page} 分页 / {@link #unreadCount} 红点计数。</li>
 *   <li>已读：{@link #markAsRead} 单条 / {@link #markAllRead} 一键全部。</li>
 * </ul>
 *
 * <p>所有写库操作完成后，本服务通过 Redis pub/sub 通道
 * {@code notification:user:{userId}:new} 发布一条空消息（消息体仅携带通知 id 字符串），
 * 供未来 SSE / WebSocket 推送客户端实时刷新红点（B5 实施）。
 *
 * <p>Covers: R19.1, R19.2, R19.3, R19.4。
 */
public interface NotificationService {

    /**
     * 创建并投递一条通知。
     *
     * <p>去重保护：同一 {@code (userId, type, relatedType, relatedId)} 在 60 秒内
     * 已存在<b>未读</b>记录时跳过本次插入并直接返回，避免重复事件刷屏。
     * 当 {@code relatedType} 或 {@code relatedId} 任一为 null 时，仅按
     * {@code (userId, type)} 与时间窗匹配是不充分的，因此回退为始终插入。
     *
     * @param userId      收件人，必填；不存在的用户由 DB FK 兜底（异常由调用方处理）
     * @param type        通知类型，必填
     * @param title       标题，1..200 字符
     * @param body        正文，必填
     * @param link        前端跳转路径，可空
     * @param relatedType 业务关联资源类型；与 {@code relatedId} 同时给出才会启用去重
     * @param relatedId   业务关联资源主键
     */
    void notify(Long userId,
                NotificationType type,
                String title,
                String body,
                String link,
                String relatedType,
                Long relatedId);

    /**
     * 向多名收件人广播同一条通知。
     *
     * <p>去重与单收件人逻辑一致；任一收件人写库 / 发布失败均会被记录但不会影响
     * 其他收件人的投递。空集合或 null 集合视为 no-op。
     *
     * @see #notify(Long, NotificationType, String, String, String, String, Long)
     */
    void notifyAll(Collection<Long> userIds,
                   NotificationType type,
                   String title,
                   String body,
                   String link,
                   String relatedType,
                   Long relatedId);

    /**
     * 当前用户的通知分页查询（R19.3）。
     *
     * <p>{@code query.read()} / {@code query.type()} 为 null/空时不过滤；
     * {@code page} 从 1 起，{@code pageSize} 由调用方限制 1..100（Bean Validation
     * 已兜底）。
     *
     * @param userId 当前用户，service 内不再校验登录态（控制器层负责）
     * @param query  查询条件，非空
     * @return 通知分页结果
     */
    PageResult<NotificationDTO> page(Long userId, NotificationQuery query);

    /**
     * 当前用户未读通知计数（R19.3 红点轮询）。
     */
    UnreadCountDTO unreadCount(Long userId);

    /**
     * 标记单条通知已读（R19.4）。
     *
     * <p>校验：通知存在且归属当前用户；不满足时抛
     * {@code BusinessException(VALIDATION_ERROR)}。已读状态下重复调用是幂等的，
     * 不会再次写库，也不抛异常。
     *
     * @param userId         当前用户
     * @param notificationId 通知主键
     */
    void markAsRead(Long userId, Long notificationId);

    /**
     * 一键全部已读（R19.4 拓展）。
     *
     * <p>幂等：当前用户没有未读时直接返回。
     */
    void markAllRead(Long userId);
}
