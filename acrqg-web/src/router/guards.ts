import type { Router } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { hasAnyRole } from '@/utils/permission'

const APP_TITLE = '智评 — AI 代码评审与质量门禁平台'

/**
 * 注册全局路由守卫。
 *
 * 关联需求：
 * - R1.4 / R23.1：未登录访问受保护路由跳转到 /login
 * - R2.1~R2.5：基于角色（meta.requiredRoles）做访问控制，越权跳转到 /forbidden
 */
export function registerRouterGuards(router: Router): void {
    router.beforeEach((to) => {
        const auth = useAuthStore()
        auth.hydrate()

        // 公开路由直接放行
        if (to.meta?.public) return true

        // 未登录 → /login
        if (!auth.isAuthenticated) {
            return {
                name: 'login',
                query: { redirect: to.fullPath },
            }
        }

        // 角色校验
        const required = to.meta?.requiredRoles ?? []
        if (required.length > 0 && !hasAnyRole(auth.roles, required)) {
            return { name: 'forbidden' }
        }

        return true
    })

    router.afterEach((to) => {
        if (typeof document === 'undefined') return
        const title = (to.meta?.title as string) || ''
        document.title = title ? `${title} · ${APP_TITLE}` : APP_TITLE
    })
}
