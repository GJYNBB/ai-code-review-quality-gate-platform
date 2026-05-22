package com.acrqg.platform.notification.service.impl;

import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.notification.domain.Notification;
import com.acrqg.platform.notification.domain.NotificationType;
import com.acrqg.platform.notification.dto.NotificationDTO;
import com.acrqg.platform.notification.dto.NotificationQuery;
import com.acrqg.platform.notification.dto.UnreadCountDTO;
import com.acrqg.platform.notification.repository.NotificationMapper;
import com.acrqg.platform.notification.service.NotificationService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link NotificationService} 默认实现（B4-D.3）。
 *
 * <p>实现要点：
 * <ul>
 *   <li><b>去重保护</b>：{@link #notify} 在写库前查 60 秒窗口内同一
 *       (userId, type, relatedType, relatedId) 的未读通知数；{@code >= 1} 时跳过插入。
 *       仅当 {@code relatedType} 与 {@code relatedId} 同时非空时启用去重，
 *       避免在缺少业务关联键时把"两条本应区分的通知"误吞为一条。</li>
 *   <li><b>Redis pub/sub 通道</b>：写库成功后通过
 *       {@link StringRedisTemplate#convertAndSend} 在
 *       {@code notification:user:{userId}:new} 通道发布通知主键。订阅方（B5 SSE
 *       / WebSocket）按 userId 路由消息到该用户的浏览器。
 *       发布失败会被吞掉只记 WARN，不影响数据库已落库的通知。</li>
 *   <li><b>批量并发</b>：{@link #notifyAll} 串行调用 {@link #notify}，
 *       让每条通知独立去重并独立处理失败；批量规模通常较小（项目管理员 +
 *       任务发起人，&lt;10 人），无需引入并发执行器。如未来确实需要可在此处替换
 *       为线程池实现。</li>
 *   <li><b>事务边界</b>：单条 {@link #notify} 是 {@code @Transactional} 的，
 *       Redis 发布在事务方法内调用——方法返回后事务才真正提交，但 Redis
 *       消息在事务提交前已发出。这里仍接受该轻微 race：消费方读不到 DB
 *       记录时退化为查 unreadCount 即可（典型 SSE 重连流程）。{@link #markAsRead}
 *       与 {@link #markAllRead} 为单条 UPDATE，无需显式 @Transactional。</li>
 * </ul>
 *
 * <p>Covers: R19.1, R19.2, R19.3, R19.4。
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    /** Redis pub/sub 通道前缀；订阅方拼接完整通道：{@code notification:user:{userId}:new}。 */
    static final String CHANNEL_PREFIX = "notification:user:";

    /** Redis pub/sub 通道后缀。 */
    static final String CHANNEL_SUFFIX = ":new";

    /** 去重时间窗：60 秒。 */
    static final Duration DEDUP_WINDOW = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationMapper notificationMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public NotificationServiceImpl(NotificationMapper notificationMapper,
                                   StringRedisTemplate stringRedisTemplate) {
        this.notificationMapper = notificationMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void notify(Long userId,
                       NotificationType type,
                       String title,
                       String body,
                       String link,
                       String relatedType,
                       Long relatedId) {
        validateNotifyArgs(userId, type, title, body);

        // 1) 去重保护：仅当 relatedType / relatedId 都给出时启用
        String typeName = type.name();
        if (relatedType != null && !relatedType.isBlank() && relatedId != null) {
            OffsetDateTime since = OffsetDateTime.now().minus(DEDUP_WINDOW);
            long dup = notificationMapper.countRecentDuplicates(userId, typeName, relatedType, relatedId, since);
            if (dup > 0) {
                if (log.isDebugEnabled()) {
                    log.debug("notification dedup skipped: userId={} type={} relatedType={} relatedId={} dup={}",
                            userId, typeName, relatedType, relatedId, dup);
                }
                return;
            }
        }

        // 2) 写库
        Notification entity = new Notification();
        entity.setUserId(userId);
        entity.setType(typeName);
        entity.setTitle(title);
        entity.setBody(body);
        entity.setLink(link);
        entity.setRead(false);
        entity.setRelatedType(relatedType);
        entity.setRelatedId(relatedId);
        notificationMapper.insert(entity);

        // 3) Redis pub/sub
        publishNewNotification(userId, entity.getId());
    }

    @Override
    public void notifyAll(Collection<Long> userIds,
                          NotificationType type,
                          String title,
                          String body,
                          String link,
                          String relatedType,
                          Long relatedId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        // LinkedHashSet 去重的同时保留稳定顺序，便于日志对照
        Set<Long> distinct = new LinkedHashSet<>(userIds.size());
        for (Long uid : userIds) {
            if (uid != null) {
                distinct.add(uid);
            }
        }
        for (Long uid : distinct) {
            try {
                notify(uid, type, title, body, link, relatedType, relatedId);
            } catch (RuntimeException ex) {
                // 单收件人失败不影响其他人；记录后继续
                log.warn("notifyAll: deliver failed userId={} type={} err={}", uid, type, ex.toString());
            }
        }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Override
    public PageResult<NotificationDTO> page(Long userId, NotificationQuery query) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "no current user");
        }
        if (query == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "query 不能为空");
        }
        int page = Math.max(query.page(), 1);
        int pageSize = query.pageSize() <= 0 ? 20 : Math.min(query.pageSize(), 100);
        int offset = (page - 1) * pageSize;

        String type = (query.type() == null || query.type().isBlank()) ? null : query.type().trim();
        Boolean read = query.read();

        long total = notificationMapper.countByUser(userId, type, read);
        List<NotificationDTO> items;
        if (total == 0L) {
            items = Collections.emptyList();
        } else {
            List<Notification> rows = notificationMapper.pageByUser(userId, type, read, pageSize, offset);
            items = new ArrayList<>(rows.size());
            for (Notification row : rows) {
                items.add(toDTO(row));
            }
        }
        return PageResult.of(items, page, pageSize, total);
    }

    @Override
    public UnreadCountDTO unreadCount(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "no current user");
        }
        long count = notificationMapper.countUnreadByUser(userId);
        return new UnreadCountDTO(count);
    }

    // ------------------------------------------------------------------
    // 已读
    // ------------------------------------------------------------------

    @Override
    public void markAsRead(Long userId, Long notificationId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "no current user");
        }
        if (notificationId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "notificationId 不能为空");
        }
        Notification existing = notificationMapper.findByIdAndUser(notificationId, userId);
        if (existing == null) {
            // 不存在 / 不属于该用户 —— 任务交付清单要求抛 VALIDATION_ERROR
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "通知不存在或不属于当前用户");
        }
        if (existing.isRead()) {
            // 已读重复调用幂等
            return;
        }
        notificationMapper.markAsRead(notificationId, userId, OffsetDateTime.now());
    }

    @Override
    public void markAllRead(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "no current user");
        }
        notificationMapper.markAllRead(userId, OffsetDateTime.now());
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private static void validateNotifyArgs(Long userId, NotificationType type, String title, String body) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "userId 不能为空");
        }
        if (type == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "type 不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "title 不能为空");
        }
        if (title.length() > 200) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "title 长度不能超过 200");
        }
        if (body == null || body.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "body 不能为空");
        }
    }

    private void publishNewNotification(Long userId, Long notificationId) {
        String channel = CHANNEL_PREFIX + userId + CHANNEL_SUFFIX;
        try {
            stringRedisTemplate.convertAndSend(channel, String.valueOf(notificationId));
        } catch (RuntimeException ex) {
            // Redis 故障不影响已落库的通知
            log.warn("publish notification failed: channel={} notificationId={} err={}",
                    channel, notificationId, ex.toString());
        }
    }

    private static NotificationDTO toDTO(Notification row) {
        return new NotificationDTO(
                row.getId(),
                row.getUserId(),
                row.getType(),
                row.getTitle(),
                row.getBody(),
                row.getLink(),
                row.isRead(),
                row.getRelatedType(),
                row.getRelatedId(),
                row.getCreatedAt(),
                row.getReadAt()
        );
    }
}
