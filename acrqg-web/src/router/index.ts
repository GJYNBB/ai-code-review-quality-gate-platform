import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import DefaultLayout from '@/layouts/DefaultLayout.vue'
import BlankLayout from '@/layouts/BlankLayout.vue'
import LoginPage from '@/pages/login/LoginPage.vue'
import ForbiddenPage from '@/pages/forbidden/ForbiddenPage.vue'
import NotFoundPage from '@/pages/notfound/NotFoundPage.vue'
import { registerRouterGuards } from '@/router/guards'
import type { Role } from '@/types/api'

/**
 * 路由 meta 类型扩展。
 * - public: 是否为公开页（不需要登录）
 * - requiredRoles: 至少拥有其中一个全局角色才允许访问；空数组表示任意已登录可访问
 * - title: 页面标题
 */
declare module 'vue-router' {
    interface RouteMeta {
        public?: boolean
        requiredRoles?: Role[]
        title?: string
        placeholderDescription?: string
    }
}

// 占位组件：复用 PlaceholderPage 直到对应页面在 B5 接入。
const Placeholder = () => import('@/pages/PlaceholderPage.vue')

// 路由表与 design.md §5.2 对齐：15 条业务路由 + 公开路由 + 异常路由
export const routes: RouteRecordRaw[] = [
    // ---------------- 公开路由 ----------------
    {
        path: '/login',
        component: BlankLayout,
        meta: { public: true },
        children: [
            {
                path: '',
                name: 'login',
                component: LoginPage,
                meta: { public: true, title: '登录' },
            },
        ],
    },
    {
        path: '/forbidden',
        component: BlankLayout,
        meta: { public: true },
        children: [
            {
                path: '',
                name: 'forbidden',
                component: ForbiddenPage,
                meta: { public: true, title: '无权访问' },
            },
        ],
    },

    // ---------------- 业务路由（DefaultLayout 包裹）----------------
    {
        path: '/',
        component: DefaultLayout,
        redirect: '/dashboard',
        children: [
            {
                path: 'dashboard',
                name: 'dashboard',
                component: Placeholder,
                meta: {
                    requiredRoles: [],
                    title: '工作台',
                    placeholderDescription: 'UI-002 工作台首页将在 B5-A 落地',
                },
            },
            {
                path: 'projects',
                name: 'project-list',
                component: Placeholder,
                meta: {
                    requiredRoles: [],
                    title: '项目列表',
                    placeholderDescription: 'UI-003 项目列表将在 B5-A 落地',
                },
            },
            {
                path: 'projects/:projectId',
                name: 'project-detail',
                component: Placeholder,
                meta: {
                    requiredRoles: [],
                    title: '项目详情',
                    placeholderDescription: 'UI-004 项目详情将在 B5-A 落地',
                },
            },
            {
                path: 'projects/:projectId/repository',
                name: 'project-repository',
                component: Placeholder,
                meta: {
                    requiredRoles: ['PROJECT_ADMIN', 'SYSTEM_ADMIN'],
                    title: '仓库绑定',
                    placeholderDescription: 'UI-005 仓库绑定将在 B5-A 落地',
                },
            },
            {
                path: 'projects/:projectId/members',
                name: 'project-members',
                component: Placeholder,
                meta: {
                    requiredRoles: ['PROJECT_ADMIN', 'SYSTEM_ADMIN'],
                    title: '成员管理',
                    placeholderDescription: 'UI-004 成员管理将在 B5-A 落地',
                },
            },
            {
                path: 'projects/:projectId/quality-gate',
                name: 'project-quality-gate',
                component: Placeholder,
                meta: {
                    requiredRoles: ['PROJECT_ADMIN', 'SYSTEM_ADMIN'],
                    title: '质量门禁',
                    placeholderDescription: 'UI-009 质量门禁将在 B5-A 落地',
                },
            },
            {
                path: 'review-tasks',
                name: 'review-task-list',
                component: Placeholder,
                meta: {
                    requiredRoles: [],
                    title: '评审任务',
                    placeholderDescription: 'UI-006 评审任务列表将在 B5-A 落地',
                },
            },
            {
                path: 'review-tasks/:taskId/report',
                name: 'review-task-report',
                component: Placeholder,
                meta: {
                    requiredRoles: [],
                    title: '评审报告',
                    placeholderDescription: 'UI-007 评审报告将在 B5-A 落地',
                },
            },
            {
                path: 'issues/:issueId',
                name: 'issue-detail',
                component: Placeholder,
                meta: {
                    requiredRoles: [],
                    title: '问题详情',
                    placeholderDescription: 'UI-008 问题详情抽屉将在 B5-A 落地',
                },
            },
            {
                path: 'notifications',
                name: 'notification-list',
                component: Placeholder,
                meta: {
                    requiredRoles: [],
                    title: '通知中心',
                    placeholderDescription: '通知中心列表将在 B5-A 落地',
                },
            },
            {
                path: 'admin/users',
                name: 'admin-users',
                component: Placeholder,
                meta: {
                    requiredRoles: ['SYSTEM_ADMIN'],
                    title: '用户管理',
                    placeholderDescription: 'UI-010 用户管理将在 B5-A 落地',
                },
            },
            {
                path: 'admin/model-configs',
                name: 'admin-model-configs',
                component: Placeholder,
                meta: {
                    requiredRoles: ['SYSTEM_ADMIN'],
                    title: '模型配置',
                    placeholderDescription: 'UI-010 模型配置将在 B5-A 落地',
                },
            },
            {
                path: 'admin/scanners',
                name: 'admin-scanners',
                component: Placeholder,
                meta: {
                    requiredRoles: ['SYSTEM_ADMIN'],
                    title: '扫描器配置',
                    placeholderDescription: 'UI-010 扫描器配置将在 B5-A 落地',
                },
            },
            {
                path: 'admin/audit-logs',
                name: 'admin-audit-logs',
                component: Placeholder,
                meta: {
                    requiredRoles: ['SYSTEM_ADMIN'],
                    title: '审计日志',
                    placeholderDescription: 'UI-010 审计日志将在 B5-A 落地',
                },
            },
        ],
    },

    // ---------------- 兜底 ----------------
    {
        path: '/:pathMatch(.*)*',
        name: 'not-found',
        component: NotFoundPage,
        meta: { public: true, title: '页面不存在' },
    },
]

const router = createRouter({
    history: createWebHistory(),
    routes,
    scrollBehavior(_to, _from, savedPosition) {
        return savedPosition ?? { top: 0 }
    },
})

registerRouterGuards(router)

export default router
