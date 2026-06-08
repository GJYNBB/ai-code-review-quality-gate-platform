<script setup lang="ts">
/**
 * DefaultLayout：业务页主布局。
 *
 * Aegis Console shell：深色技术侧边栏 + 顶部工具栏 + 内容画布。
 * 保留原有路由、角色显隐和菜单跳转行为，仅重写视觉与信息架构。
 */
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import {
  Bell,
  Cpu,
  DataAnalysis,
  Files,
  Grid,
  Lock,
  Monitor,
  Operation,
  Setting,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'

import AppHeader from '@/components/AppHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { hasAnyRole } from '@/utils/permission'
import type { Role } from '@/types/api'

interface MenuItem {
  index: string
  title: string
  description: string
  icon: Component
  requiredRoles?: Role[]
}

interface MenuGroup {
  title: string
  items: MenuItem[]
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { user } = storeToRefs(authStore)

const menuGroups: MenuGroup[] = [
  {
    title: 'Overview',
    items: [
      {
        index: '/dashboard',
        title: '工作台',
        description: '质量趋势与风险概览',
        icon: DataAnalysis,
      },
      { index: '/projects', title: '项目管理', description: '项目、成员与仓库配置', icon: Grid },
      { index: '/review-tasks', title: '评审任务', description: '异步评审流水线', icon: Files },
      { index: '/notifications', title: '通知中心', description: '待处理消息与提醒', icon: Bell },
    ],
  },
  {
    title: 'Administration',
    items: [
      {
        index: '/admin/users',
        title: '用户管理',
        description: '账号与全局角色',
        icon: User,
        requiredRoles: ['SYSTEM_ADMIN'],
      },
      {
        index: '/admin/model-configs',
        title: '模型配置',
        description: 'AI 网关与密钥',
        icon: Cpu,
        requiredRoles: ['SYSTEM_ADMIN'],
      },
      {
        index: '/admin/scanners',
        title: '扫描器配置',
        description: 'SAST 执行策略',
        icon: Operation,
        requiredRoles: ['SYSTEM_ADMIN'],
      },
      {
        index: '/admin/system-params',
        title: '系统参数',
        description: '运行时参数热更新',
        icon: Setting,
        requiredRoles: ['SYSTEM_ADMIN'],
      },
      {
        index: '/admin/audit-logs',
        title: '审计日志',
        description: '安全审计轨迹',
        icon: Lock,
        requiredRoles: ['SYSTEM_ADMIN'],
      },
    ],
  },
]

const visibleMenuGroups = computed<MenuGroup[]>(() => {
  const roles = user.value?.roles ?? []
  return menuGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => {
        if (!item.requiredRoles || item.requiredRoles.length === 0) return true
        return hasAnyRole(roles, item.requiredRoles)
      }),
    }))
    .filter((group) => group.items.length > 0)
})

const visibleMenuItems = computed(() => visibleMenuGroups.value.flatMap((group) => group.items))

const activeMenu = computed(() => {
  const path = route.path
  const sorted = [...visibleMenuItems.value].sort((a, b) => b.index.length - a.index.length)
  const matched = sorted.find((item) => path === item.index || path.startsWith(`${item.index}/`))
  return matched?.index ?? path
})

const currentMenuItem = computed(() =>
  visibleMenuItems.value.find((item) => item.index === activeMenu.value),
)
const pageTitle = computed(() => route.meta.title || currentMenuItem.value?.title || '控制台')
const pageDescription = computed(
  () => currentMenuItem.value?.description || 'AI 代码评审与质量门禁平台',
)

function handleMenuSelect(index: string) {
  if (index !== route.path) router.push(index)
}
</script>

<template>
  <div class="console-shell">
    <aside class="console-shell__sidebar">
      <div class="console-shell__brand">
        <div class="brand-mark">
          <el-icon><Monitor /></el-icon>
        </div>
        <div class="brand-copy">
          <strong>ACRQG</strong>
          <span>Quality Gate Console</span>
        </div>
      </div>

      <nav class="console-nav" aria-label="主导航">
        <section v-for="group in visibleMenuGroups" :key="group.title" class="console-nav__group">
          <p class="console-nav__group-title">{{ group.title }}</p>
          <button
            v-for="item in group.items"
            :key="item.index"
            class="console-nav__item"
            :class="{ 'is-active': activeMenu === item.index }"
            type="button"
            @click="handleMenuSelect(item.index)"
          >
            <span class="console-nav__icon">
              <el-icon><component :is="item.icon" /></el-icon>
            </span>
            <span class="console-nav__copy">
              <strong>{{ item.title }}</strong>
              <small>{{ item.description }}</small>
            </span>
          </button>
        </section>
      </nav>

      <div class="console-shell__sidebar-footer">
        <el-icon><TrendCharts /></el-icon>
        <span>AI Review Ops</span>
      </div>
    </aside>

    <section class="console-shell__workspace">
      <AppHeader />

      <main class="console-shell__main">
        <div class="page-hero">
          <div>
            <p class="page-hero__eyebrow">Aegis Console</p>
            <h1>{{ pageTitle }}</h1>
            <p>{{ pageDescription }}</p>
          </div>
          <div class="page-hero__status">
            <span class="pulse-dot" />
            <span>Secure pipeline active</span>
          </div>
        </div>

        <router-view v-slot="{ Component: ViewComponent, route: childRoute }">
          <transition name="route-fade" mode="out-in">
            <component :is="ViewComponent" :key="childRoute.fullPath" />
          </transition>
        </router-view>
      </main>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.console-shell {
  height: 100vh;
  display: grid;
  grid-template-columns: var(--acrqg-sider-width) minmax(0, 1fr);
  overflow: hidden;
  background: var(--acrqg-bg-page);

  &__sidebar {
    position: relative;
    display: flex;
    flex-direction: column;
    min-width: 0;
    padding: 22px 18px;
    color: #dbeafe;
    background:
      radial-gradient(circle at 0 0, rgba(37, 99, 235, 0.34), transparent 34%),
      linear-gradient(180deg, var(--acrqg-sidebar-bg-2), var(--acrqg-sidebar-bg));
    border-right: 1px solid var(--acrqg-sidebar-border);
    box-shadow: 18px 0 42px rgba(8, 17, 31, 0.2);
    overflow: hidden;
  }

  &__brand {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 4px 4px 22px;
  }

  &__sidebar-footer {
    margin-top: auto;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 14px;
    border: 1px solid rgba(148, 163, 184, 0.18);
    border-radius: 16px;
    color: rgba(219, 234, 254, 0.74);
    background: rgba(15, 23, 42, 0.42);
    font-size: 12px;
    font-weight: 700;
  }

  &__workspace {
    min-width: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  &__main {
    flex: 1;
    overflow: auto;
    padding: 24px 30px 36px;
  }
}

.brand-mark {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  color: #e0f2fe;
  background: linear-gradient(135deg, var(--acrqg-accent), var(--acrqg-accent-2));
  box-shadow: 0 18px 40px rgba(6, 182, 212, 0.25);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 3px;

  strong {
    font-size: 18px;
    letter-spacing: 0.08em;
  }

  span {
    color: rgba(219, 234, 254, 0.62);
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }
}

.console-nav {
  display: flex;
  flex-direction: column;
  gap: 18px;
  overflow: auto;
  padding-right: 2px;

  &__group-title {
    margin: 0 0 8px 10px;
    color: rgba(191, 219, 254, 0.46);
    font-size: 11px;
    font-weight: 800;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  &__item {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    border: 1px solid transparent;
    border-radius: 16px;
    color: rgba(226, 232, 240, 0.74);
    background: transparent;
    cursor: pointer;
    text-align: left;
    transition: 0.18s ease;

    &:hover {
      color: #fff;
      background: rgba(148, 163, 184, 0.1);
    }

    &.is-active {
      color: #fff;
      border-color: rgba(96, 165, 250, 0.34);
      background: linear-gradient(135deg, rgba(37, 99, 235, 0.38), rgba(6, 182, 212, 0.18));
      box-shadow:
        inset 3px 0 0 #67e8f9,
        0 12px 26px rgba(37, 99, 235, 0.18);
    }
  }

  &__icon {
    width: 36px;
    height: 36px;
    flex: 0 0 auto;
    display: grid;
    place-items: center;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.08);
  }

  &__copy {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;

    strong {
      font-size: 14px;
      line-height: 1.2;
    }

    small {
      color: rgba(219, 234, 254, 0.5);
      font-size: 11px;
      line-height: 1.2;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
}

.page-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
  padding: 24px 26px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 26px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(239, 246, 255, 0.76)),
    radial-gradient(circle at 92% 0%, rgba(6, 182, 212, 0.17), transparent 28%);
  box-shadow: var(--acrqg-shadow-sm);

  &__eyebrow {
    margin: 0 0 8px;
    color: var(--acrqg-accent);
    font-size: 12px;
    font-weight: 800;
    letter-spacing: 0.14em;
    text-transform: uppercase;
  }

  h1 {
    margin: 0;
    color: var(--acrqg-text-primary);
    font-size: 30px;
    line-height: 1.1;
    font-weight: 850;
    letter-spacing: -0.04em;
  }

  p:last-child {
    margin: 8px 0 0;
    color: var(--acrqg-text-secondary);
  }

  &__status {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
    border-radius: 999px;
    color: #0f766e;
    background: rgba(20, 184, 166, 0.12);
    font-size: 12px;
    font-weight: 800;
    white-space: nowrap;
  }
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #14b8a6;
  box-shadow: 0 0 0 6px rgba(20, 184, 166, 0.14);
}

.route-fade-enter-active,
.route-fade-leave-active {
  transition:
    opacity 0.16s ease,
    transform 0.16s ease;
}
.route-fade-enter-from,
.route-fade-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

@media (max-width: 960px) {
  .console-shell {
    grid-template-columns: 84px minmax(0, 1fr);

    &__sidebar {
      padding: 16px 10px;
    }

    &__main {
      padding: 18px;
    }
  }

  .brand-copy,
  .console-nav__copy,
  .console-nav__group-title,
  .console-shell__sidebar-footer span {
    display: none;
  }

  .console-nav__item {
    justify-content: center;
  }

  .page-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
