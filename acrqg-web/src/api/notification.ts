import { request } from '@/api/http'
import type {
    NotificationDTO,
    NotificationQuery,
    PageResult,
    UnreadCountDTO,
} from '@/types/api'

/** 站内通知 API（design §8.7）。 */

/** GET /notifications */
export function page(query: NotificationQuery): Promise<PageResult<NotificationDTO>> {
    return request<PageResult<NotificationDTO>>({
        method: 'GET',
        url: '/notifications',
        params: query,
    })
}

/** GET /notifications/unread-count */
export function unreadCount(): Promise<UnreadCountDTO> {
    return request<UnreadCountDTO>({
        method: 'GET',
        url: '/notifications/unread-count',
        // 30s 轮询场景：失败时不弹 ElMessage，避免噪声
        skipErrorMessage: true,
    })
}

/**
 * 标记单/多条通知为已读。
 * 后端当前仅暴露 PATCH /notifications/{id}/read 与 POST /notifications/read-all；
 * 一次性标记多个时按 id 循环逐条调用。
 */
export async function markRead(ids: number[]): Promise<void> {
    await Promise.all(
        ids.map((id) =>
            request<void>({
                method: 'PATCH',
                url: `/notifications/${id}/read`,
            }),
        ),
    )
}

/** POST /notifications/read-all */
export function markAllRead(): Promise<void> {
    return request<void>({
        method: 'POST',
        url: '/notifications/read-all',
    })
}
