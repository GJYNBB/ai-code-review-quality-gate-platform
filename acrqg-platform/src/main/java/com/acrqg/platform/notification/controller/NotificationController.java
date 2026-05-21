package com.acrqg.platform.notification.controller;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.acrqg.platform.notification.dto.NotificationDTO;
import com.acrqg.platform.notification.dto.NotificationQuery;
import com.acrqg.platform.notification.dto.UnreadCountDTO;
import com.acrqg.platform.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知控制器（M09 / R19）。
 *
 * <p>对齐 design.md §6.6 / §8.7：
 * <pre>
 * GET    /api/v1/notifications                 已登录
 * GET    /api/v1/notifications/unread-count    已登录
 * PATCH  /api/v1/notifications/{id}/read       已登录
 * POST   /api/v1/notifications/read-all        已登录
 * </pre>
 *
 * <p>"已登录"由 {@code SecurityConfig} 的 {@code anyRequest().authenticated()}
 * 全局兜底；类级 {@link PreAuthorize @PreAuthorize("isAuthenticated()")} 双保险，
 * 同时为方法级 SpEL 表达式留口。
 *
 * <p>当前用户从 {@link CurrentUserHolder} 获取——它由
 * {@code JwtAuthFilter} 在请求进入时填充；service 内部所有过滤都按当前用户隔离。
 *
 * <p>Covers: R19.3, R19.4。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Notification", description = "站内通知（M09 / R19）")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "通知分页查询",
            description = "已登录用户可调用，按当前用户过滤；支持 type / read 精确过滤。")
    @GetMapping
    public ApiResponse<PageResult<NotificationDTO>> page(@Valid @ModelAttribute NotificationQuery query) {
        Long userId = currentUserId();
        return ApiResponse.success(notificationService.page(userId, query));
    }

    @Operation(summary = "未读通知数",
            description = "已登录用户可调用；前端头部红点 30s 轮询。")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountDTO> unreadCount() {
        Long userId = currentUserId();
        return ApiResponse.success(notificationService.unreadCount(userId));
    }

    @Operation(summary = "标记单条通知已读",
            description = "已登录用户可调用；通知不属于当前用户时返回 VALIDATION_ERROR。")
    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable("id") Long id) {
        Long userId = currentUserId();
        notificationService.markAsRead(userId, id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "一键全部已读",
            description = "已登录用户可调用；幂等。")
    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        Long userId = currentUserId();
        notificationService.markAllRead(userId);
        return ApiResponse.success(null);
    }

    private static Long currentUserId() {
        AuthenticatedUser user = CurrentUserHolder.requireCurrent();
        return user.id();
    }
}
