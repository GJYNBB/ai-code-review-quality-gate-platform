package com.acrqg.platform.notification.repository;

import com.acrqg.platform.notification.domain.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 站内通知 Mapper（B4-D.2）。
 *
 * <p>承担：
 * <ul>
 *   <li>{@link #insert(Notification)} —— 单条插入；主键回填到入参对象，便于 Service 层
 *       拿到 id 后继续 Redis 通道发布。</li>
 *   <li>{@link #pageByUser} / {@link #countByUser} —— 当前用户的通知分页 + 总数；
 *       支持 {@code type} 精确过滤与 {@code read} 已读 / 未读过滤；
 *       排序：{@code created_at DESC}，二级 {@code id DESC} 防止时间相同时翻页错乱。</li>
 *   <li>{@link #countUnreadByUser(Long)} —— 红点轮询用未读计数（R19.3）。</li>
 *   <li>{@link #markAsRead(Long, Long, OffsetDateTime)} —— 仅当通知归属指定 user 且
 *       原 read=false 时把 read 翻为 true 并写 read_at；返回受影响行数。
 *       0 行表示通知不存在 / 不归属该用户 / 已读，由 Service 层据此判定异常。</li>
 *   <li>{@link #markAllRead(Long, OffsetDateTime)} —— 一键全部已读（R19.4 拓展）。</li>
 *   <li>{@link #findByIdAndUser(Long, Long)} —— 主键 + 用户过滤的查找，用于
 *       归属校验，未命中返回 {@code null}。</li>
 *   <li>{@link #findRecentForDedup} —— 60s 去重查询：返回同一用户、同一
 *       (type, related_type, related_id) 在最近时间窗内的未读通知 id 列表。
 *       Service 层用于 {@code notify} 内的去重保护。</li>
 * </ul>
 *
 * <p>Covers: R19.1, R19.2, R19.3, R19.4。
 */
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 标准列 ↔ 字段映射，复用于多个查询，避免重复声明。 */
    String COLUMNS = """
            id, user_id, type, title, body, link, read,
            related_type, related_id, created_at, read_at
            """;

    /**
     * 按用户分页查询通知。
     *
     * <p>过滤：
     * <ul>
     *   <li>{@code type} 非空时按类型精确匹配；</li>
     *   <li>{@code read} 非 null 时按已读状态过滤；为 null 时不过滤。</li>
     * </ul>
     *
     * <p>排序：{@code created_at DESC, id DESC}。
     */
    @Select("""
            <script>
            SELECT id, user_id, type, title, body, link, read,
                   related_type, related_id, created_at, read_at
              FROM notification
             WHERE user_id = #{userId}
            <if test='type != null and type != ""'>
              AND type = #{type}
            </if>
            <if test='read != null'>
              AND read = #{read}
            </if>
             ORDER BY created_at DESC, id DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @Results(id = "notificationMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "type", property = "type"),
            @Result(column = "title", property = "title"),
            @Result(column = "body", property = "body"),
            @Result(column = "link", property = "link"),
            @Result(column = "read", property = "read"),
            @Result(column = "related_type", property = "relatedType"),
            @Result(column = "related_id", property = "relatedId"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "read_at", property = "readAt")
    })
    List<Notification> pageByUser(@Param("userId") Long userId,
                                  @Param("type") String type,
                                  @Param("read") Boolean read,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    /** 与 {@link #pageByUser} 同条件的总条数。 */
    @Select("""
            <script>
            SELECT COUNT(1) FROM notification
             WHERE user_id = #{userId}
            <if test='type != null and type != ""'>
              AND type = #{type}
            </if>
            <if test='read != null'>
              AND read = #{read}
            </if>
            </script>
            """)
    long countByUser(@Param("userId") Long userId,
                     @Param("type") String type,
                     @Param("read") Boolean read);

    /** 当前用户未读总数（红点轮询）。 */
    @Select("SELECT COUNT(1) FROM notification WHERE user_id = #{userId} AND read = FALSE")
    long countUnreadByUser(@Param("userId") Long userId);

    /**
     * 标记单条通知已读。
     *
     * <p>原子语义：仅当 (id, user_id) 匹配且原 {@code read=false} 时翻为 {@code true}
     * 并写 {@code read_at}。返回受影响行数：
     * <ul>
     *   <li>{@code 1} —— 标记成功。</li>
     *   <li>{@code 0} —— 通知不存在 / 不归属该用户 / 已读。</li>
     * </ul>
     *
     * <p>Service 层先用 {@link #findByIdAndUser(Long, Long)} 区分"不存在/不属于"
     * 与"已读"两种 0 行原因。
     */
    @Update("""
            UPDATE notification
               SET read = TRUE, read_at = #{readAt}
             WHERE id = #{id} AND user_id = #{userId} AND read = FALSE
            """)
    int markAsRead(@Param("id") Long id,
                   @Param("userId") Long userId,
                   @Param("readAt") OffsetDateTime readAt);

    /** 一键全部已读（仅未读 → 已读，幂等）。 */
    @Update("""
            UPDATE notification
               SET read = TRUE, read_at = #{readAt}
             WHERE user_id = #{userId} AND read = FALSE
            """)
    int markAllRead(@Param("userId") Long userId,
                    @Param("readAt") OffsetDateTime readAt);

    /**
     * 主键 + 用户过滤查找。
     *
     * <p>用于 {@link com.acrqg.platform.notification.service.NotificationService#markAsRead}
     * 的归属校验：不存在 / 不归属当前用户时返回 {@code null}，由 Service 层抛
     * {@code BusinessException(VALIDATION_ERROR)}。
     */
    @Select("""
            SELECT id, user_id, type, title, body, link, read,
                   related_type, related_id, created_at, read_at
              FROM notification
             WHERE id = #{id} AND user_id = #{userId}
            """)
    @Results(id = "notificationByIdMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "type", property = "type"),
            @Result(column = "title", property = "title"),
            @Result(column = "body", property = "body"),
            @Result(column = "link", property = "link"),
            @Result(column = "read", property = "read"),
            @Result(column = "related_type", property = "relatedType"),
            @Result(column = "related_id", property = "relatedId"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "read_at", property = "readAt")
    })
    Notification findByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 去重查询：返回同一用户在 {@code since} 之后、未读、且匹配
     * (type, related_type, related_id) 的通知数量。
     *
     * <p>由 Service 层 {@code notify} 调用：当返回 &gt;0 时跳过本次插入，避免
     * 短时重复事件（如 webhook 重投）打满收件箱。
     *
     * <p>{@code relatedType} / {@code relatedId} 任一为 null 时不参与匹配（即
     * 必须 type 同样命中且未读窗口内才视为重复，这种保守策略在缺少业务关联时
     * 仍允许写入，避免误吞通知）。
     *
     * @param userId      收件人
     * @param type        类型字符串
     * @param relatedType 业务关联资源类型
     * @param relatedId   业务关联资源主键
     * @param since       窗口起点（不含）
     */
    @Select("""
            <script>
            SELECT COUNT(1) FROM notification
             WHERE user_id = #{userId}
               AND type = #{type}
               AND read = FALSE
               AND created_at >= #{since}
            <if test='relatedType != null and relatedType != ""'>
              AND related_type = #{relatedType}
            </if>
            <if test='relatedId != null'>
              AND related_id = #{relatedId}
            </if>
            </script>
            """)
    long countRecentDuplicates(@Param("userId") Long userId,
                               @Param("type") String type,
                               @Param("relatedType") String relatedType,
                               @Param("relatedId") Long relatedId,
                               @Param("since") OffsetDateTime since);
}
