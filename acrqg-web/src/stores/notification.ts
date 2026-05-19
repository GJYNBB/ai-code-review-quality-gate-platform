import { defineStore } from 'pinia'
import type { NotificationDTO } from '@/types/api'

/**
 * Notification Store（占位）
 *
 * 关联需求：R19 通知中心。
 * B0-B 仅声明 state，具体轮询/拉取动作在 B4-D 落地。
 */
interface NotificationState {
    unreadCount: number
    items: NotificationDTO[]
}

export const useNotificationStore = defineStore('notification', {
    state: (): NotificationState => ({
        unreadCount: 0,
        items: [],
    }),

    actions: {
        setUnreadCount(count: number) {
            this.unreadCount = Math.max(0, count)
        },
        setItems(items: NotificationDTO[]) {
            this.items = items
        },
        markRead(id: number) {
            const item = this.items.find((it) => it.id === id)
            if (item && !item.readFlag) {
                item.readFlag = true
                this.unreadCount = Math.max(0, this.unreadCount - 1)
            }
        },
        reset() {
            this.unreadCount = 0
            this.items = []
        },
    },
})
