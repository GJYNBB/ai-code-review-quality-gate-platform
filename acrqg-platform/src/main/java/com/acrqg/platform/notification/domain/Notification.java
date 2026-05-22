package com.acrqg.platform.notification.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 站内通知领域对象（DO），对应 V50__m09_notification.sql 中的 {@code notification} 表。
 *
 * <p>表结构来自 design.md §7.2，与任务交付清单要求的 {@code link} / {@code related_type}
 * / {@code related_id} / {@code read_at} 字段一并落库。
 *
 * <p>Covers: R19.1, R19.2, R19.3, R19.4。
 */
@TableName(value = "notification", autoResultMap = true)
public class Notification {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    /** {@link NotificationType#name()}。 */
    @TableField("type")
    private String type;

    @TableField("title")
    private String title;

    @TableField("body")
    private String body;

    @TableField("link")
    private String link;

    /**
     * 已读标记。Java 字段名沿用 SQL 列名 {@code read}；通过 boolean
     * getter/setter（{@link #isRead()} / {@link #setRead(boolean)}）暴露。
     */
    @TableField("read")
    private boolean read;

    @TableField("related_type")
    private String relatedType;

    @TableField("related_id")
    private Long relatedId;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("read_at")
    private OffsetDateTime readAt;

    public Notification() {
        // for MyBatis-Plus
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public String getRelatedType() {
        return relatedType;
    }

    public void setRelatedType(String relatedType) {
        this.relatedType = relatedType;
    }

    public Long getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(Long relatedId) {
        this.relatedId = relatedId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(OffsetDateTime readAt) {
        this.readAt = readAt;
    }
}
