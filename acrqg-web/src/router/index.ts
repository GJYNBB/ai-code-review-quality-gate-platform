import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import DefaultLayout from '@/layouts/DefaultLayout.vue'
import BlankLayout from '@/layouts/BlankLayout.vue'
import LoginPage from '@/pages/login/LoginPage.vue'
import ForbiddenPage from '@/pages/forbidden/ForbiddenPage.vue'
import NotFoundPage from '@/pages/notfound/NotFoundPage.vue'
import { registerRouterGuards } from '@/router/guards'
import type { Role } from '@/types/api'

/**
 * 路由 meta 类型扩展（design §5.2 / §5.4）。
 *
 * - public         : 是否为公开页（true 表示无需登录即可访问）
 * - requiredRoles  : 至少拥有其中一个全局角色才允许访问；空数组表示任意已登录可访问；
 *                   省略不写表示与空数组等价
 * - title          : 页面标题（document.title 由 guards.afterEach 写入）
 *
 * 项目成员粒度的鉴权（路由表中标注 "项目成员"）由后端在接口层面校验；
 * 前端路由层只能把关全局角色，进入页面后由组件按返回结果决定可见性。
 */
declare module 'vue-router' {
    interface RouteMeta {
        public?: boolean
        requiredRoles?: Role[]
        title?: string
        placeholderDescription?: string
    }
}

// 占位组件：B5-A.8~13 尚未实现的页面继续保留占位
const Placeholder = () => import('@/pages/PlaceholderPage.vue')

// B5-A 已落地的页面（异步懒加载）
const DashboardPage = () => import('@/pages/dashboard/DashboardPage.vue')
const ProjectListPage = () => import('@/pages/project/ProjectListPage.vue')
const ProjectDetailPage = () => import('@/pages/project/ProjectDetailPage.vue')
const QualityGatePage = () => import('@/pages/project/QualityGatePage.vue')

/**
 * 路由表对齐 design.md §5.2（15 条业务路由 + 登录 / 403 / 404）：
 *
 *  /login                                          公开（BlankLayout）
 *  /dashboard                                      任意已登录
 *  /projects                                       任意已登录
 *  /projects/:projectId                            项目成员（后端校验）
 *  /projects/:projectId/repository                 PROJECT_ADMIN / SYSTEM_ADMIN
 *  /projects/:projectId/members                    PROJECT_ADMIN / SYSTEM_ADMIN
 *  /projects/:projectId/quality-gate               PROJECT_ADMIN / SYSTEM_ADMIN
 *  /review-tasks                                   任意已登录（按项目筛选）
 *  /review-tasks/:taskId/report                    项目成员（后端校验）
 *  /issues/:issueId                                项目成员（后端校验）
 *  /notifications                                  任意已登录
 *  /admin/users                                    SYSTEM_ADMIN
 *  /admin/model-configs                            SYSTEM_ADMIN
 *  /admin/scanners                                 SYSTEM_ADMIN
 *  /admin/system-params                            SYSTEM_ADMIN
 *  /admin/audit-logs                               SYSTEM_ADMIN
 *  /forbidden                                      公开（BlankLayout）
 *  /:pathMatch(.*)*                                404（公开）
 */
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
                component: DashboardPage,
                meta: { public: false, requiredRoles: [], title: '工作台' },
            },
            {
                path: 'projects',
                name: 'project-list',
                component: ProjectListPage,
                meta: { public: false, requiredRoles: [], title: '项目列表' },
            },
            {
                path: 'projects/:projectId',
                name: 'project-detail',
                component: ProjectDetailPage,
                meta: { public: false, requiredRoles: [], title: '项目详情' },
            },
            {
                path: 'projects/:projectId/repository',
                name: 'project-repository',
                component: Placeholder,
                meta: {
                    public: false,
                    requiredRoles: ['PROJECT_ADMIN', 'SYSTEM_ADMIN'],
                    title: '仓库绑定',
                    placeholderDescription: 'UI-005 仓库绑定（详情页内嵌 RepositoryBindingTab，B5-A.5）',
                },
            },
            {
                path: 'projects/:projectId/members',
                name: 'project-members',
                component: Placeholder,
                meta: {
                    public: false,
                    requiredRoles: ['PROJECT_ADMIN', 'SYSTEM_ADMIN'],
                    title: '成员管理',
                    placeholderDescription: 'UI-004 成员管理（详情页内嵌 MemberManageTab，B5-A.6）',
                },
            },
            {
                path: 'projects/:projectId/quality-gate',
                name: 'project-quality-gate',
                component: QualityGatePage,
                meta: {
                    public: false,
                    requiredRoles: ['PROJECT_ADMIN', 'SYSTEM_ADMIN'],
                    title: '质量门禁',
                },
            },
            {
                path: 'review-tasks',
                name: 'review-task-list',
                component: Placeholder,
                meta: {
                    public: false,
                    requiredRoles: [],
                    title: '评审任务',
                    placeholderDescription: 'UI-006 评审任务列表将在 B5-A.8 落地',
                },
            },
            {
                path: 'review-tasks/:taskId/report',
                name: 'review-task-report',
                component: Placeholder,
                meta: {
                    public: false,
                    requiredRoles: [],
                    title: '评审报告',
                    placeholderDescription: 'UI-007 评审报告将在 B5-A.9 落地',
                },
            },
            {
                path: 'issues/:issueId',
                name: 'issue-detail',
                component: Placeholder,
                meta: {
                    public: false,
                    requiredRoles: [],
                    title: '问题详情',
                    placeholderDescription: 'UI-008 问题详情抽屉将在 B5-A.10 落地',
                },
            },
            {
                path: 'notifications',
                name: 'notification-list',
                component: Placeholder,
                meta: {
                    public: false,
                    requiredRoles: [],
                    title: '通知中心',
                    placeholderDescription: '通知中心列表将在 B5-A.11 落地',
                },
            },
            {
                path: 'admin/users',
                name: 'admin-users',
                component: Placeholder,
                meta: {
                    public: false,
                    requiredRoles: ['SYSTEM_ADMIN'],
                    title: '用户管理',
                    placeholderDescription: 'UI-010 用户管理将在 B5-A.12 落地',
                },
            },
            {
                path: 'admin/model-configs',
                name: 'admin-model-configs',
                component: Placeholder,
                meta: {
                    public: false,
                    requiredRoles: ['SYSTEM_ADMIN'],
                    title: '模型配置',
                    placeholderDescription: 'UI-010 模型配置将在 B5-A.12 落地',
                },
            },
            {
                path: 'admin/scanners',
                name: 'admin-scanners',
                component: Placeholder,
                meta: {
                    public: false,
                    requiredRoles: ['SYSTEM_ADMIN'],
                    title: '扫描器配置',
                    placeholderDescription: 'UI-010 扫描器配置将在 B5-A.12 落地',
                },
            },
            {
                path: 'admin/system-params',
                name: 'admin-system-params',
                component: Placeholder,
                meta: {
                    public: false,
                    requiredRoles: ['SYSTEM_ADMIN'],
                    title: '系统参数',
                    placeholderDescription: 'UI-010 系统参数将在 B5-A.12 落地',
                },
            },
            {
                path: 'admin/audit-logs',
                name: 'admin-audit-logs',
                component: Placeholder,
                meta: {
                    public: false,
                    requiredRoles: ['SYSTEM_ADMIN'],
                    title: '审计日志',
                    placeholderDescription: 'UI-010 审计日志将在 B5-A.12 落地',
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
