<script setup lang="ts">
/**
 * DefaultLayout：业务页主布局（B5-A.3 重构）。
 *
 * 顶部由 AppHeader 组件承担（项目切换器 / 未读通知红点 / 用户菜单）；
 * 左侧菜单按角色显隐，菜单点击通过 router 跳转。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'

import AppHeader from '@/components/AppHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { hasAnyRole } from '@/utils/permission'
import type { Role } from '@/types/api'

interface MenuItem {
  index: string
  title: string
  /** 至少拥有其中一个全局角色才显示；空数组表示任意已登录用户可见 */
  requiredRoles?: Role[]
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { user } = storeToRefs(authStore)

const allMenuItems: MenuItem[] = [
  { index: '/dashboard', title: '工作台' },
  { index: '/projects', title: '项目管理' },
  { index: '/review-tasks', title: '评审任务' },
  { index: '/notifications', title: '通知中心' },
  { index: '/admin/users', title: '用户管理', requiredRoles: ['SYSTEM_ADMIN'] },
  { index: '/admin/model-configs', title: '模型配置', requiredRoles: ['SYSTEM_ADMIN'] },
  { index: '/admin/scanners', title: '扫描器配置', requiredRoles: ['SYSTEM_ADMIN'] },
  { index: '/admin/system-params', title: '系统参数', requiredRoles: ['SYSTEM_ADMIN'] },
  { index: '/admin/audit-logs', title: '审计日志', requiredRoles: ['SYSTEM_ADMIN'] },
]

const visibleMenuItems = computed<MenuItem[]>(() => {
  const roles = user.value?.roles ?? []
  return allMenuItems.filter((item) => {
    if (!item.requiredRoles || item.requiredRoles.length === 0) return true
    return hasAnyRole(roles, item.requiredRoles)
  })
})

const activeMenu = computed(() => {
  const path = route.path
  // 优先匹配前缀最长的菜单项，避免 /admin 类前缀错位
  const sorted = [...visibleMenuItems.value].sort((a, b) => b.index.length - a.index.length)
  const matched = sorted.find((item) => path === item.index || path.startsWith(`${item.index}/`))
  return matched?.index ?? path
})

function handleMenuSelect(index: string) {
  if (index !== route.path) router.push(index)
}
</script>

<template>
  <el-container class="default-layout">
    <el-header class="default-layout__header">
      <AppHeader />
    </el-header>

    <el-container>
      <el-aside class="default-layout__aside" width="220px">
        <el-menu :default-active="activeMenu" :router="false" @select="handleMenuSelect">
          <el-menu-item v-for="item in visibleMenuItems" :key="item.index" :index="item.index">
            <span>{{ item.title }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="default-layout__main">
        <router-view v-slot="{ Component, route: childRoute }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="childRoute.fullPath" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style lang="scss" scoped>
.default-layout {
  height: 100vh;

  &__header {
    height: var(--acrqg-header-height, 56px);
    padding: 0;
  }

  &__aside {
    background: var(--el-bg-color);
    border-right: 1px solid var(--el-border-color-light);
  }

  &__main {
    background: var(--acrqg-content-bg, #f5f7fa);
    padding: 16px 24px;
    overflow: auto;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
