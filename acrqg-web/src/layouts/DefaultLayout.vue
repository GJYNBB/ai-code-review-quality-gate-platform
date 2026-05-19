<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { storeToRefs } from 'pinia'

import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { hasAnyRole } from '@/utils/permission'
import type { Role } from '@/types/api'

interface MenuItem {
  index: string
  title: string
  icon?: string
  /** 至少拥有其中一个全局角色才显示；空数组表示任意已登录用户可见 */
  requiredRoles?: Role[]
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()
const { user } = storeToRefs(authStore)
const { unreadCount } = storeToRefs(notificationStore)

// 顶部项目切换器（B0-B 仅占位，B5 接入 project store）
const currentProjectName = ref<string>('全部项目')

const allMenuItems: MenuItem[] = [
  { index: '/dashboard', title: '工作台' },
  { index: '/projects', title: '项目管理' },
  { index: '/review-tasks', title: '评审任务' },
  { index: '/notifications', title: '通知中心' },
  { index: '/admin/users', title: '用户管理', requiredRoles: ['SYSTEM_ADMIN'] },
  { index: '/admin/model-configs', title: '模型配置', requiredRoles: ['SYSTEM_ADMIN'] },
  { index: '/admin/scanners', title: '扫描器配置', requiredRoles: ['SYSTEM_ADMIN'] },
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
  // 取一级路径作为高亮（如 /admin/users → 仍命中精确项）
  const path = route.path
  const matched = visibleMenuItems.value.find((item) => path.startsWith(item.index))
  return matched?.index ?? path
})

function handleMenuSelect(index: string) {
  if (index !== route.path) router.push(index)
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  authStore.logout()
  notificationStore.reset()
  router.push({ name: 'login' })
}

function goToNotifications() {
  router.push('/notifications')
}
</script>

<template>
  <el-container class="default-layout">
    <el-header class="default-layout__header">
      <div class="default-layout__brand">AI 代码评审平台</div>
      <div class="default-layout__project">
        <!-- 项目切换器：B0-B 仅占位，B5 接入 project store 实现真正切换 -->
        <el-select v-model="currentProjectName" size="small" placeholder="选择项目" :disabled="true">
          <el-option label="全部项目" value="全部项目" />
        </el-select>
      </div>
      <div class="default-layout__actions">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="notify-badge">
          <el-button text type="primary" @click="goToNotifications">通知</el-button>
        </el-badge>
        <el-dropdown trigger="click">
          <span class="default-layout__user">
            {{ user?.username ?? '未登录' }}
            <el-icon><arrow-down /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>角色：{{ user?.roles?.join(', ') || '-' }}</el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
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
    height: var(--acrqg-header-height);
    display: flex;
    align-items: center;
    gap: 24px;
    padding: 0 24px;
    background: var(--el-bg-color);
    border-bottom: 1px solid var(--el-border-color-light);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  }

  &__brand {
    font-size: 16px;
    font-weight: 600;
    color: var(--el-color-primary);
  }

  &__project {
    flex: 0 0 auto;
  }

  &__actions {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 16px;
  }

  &__user {
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }

  &__aside {
    background: var(--el-bg-color);
    border-right: 1px solid var(--el-border-color-light);
  }

  &__main {
    background: var(--acrqg-content-bg);
    padding: 16px 24px;
    overflow: auto;
  }
}

.notify-badge {
  margin-right: 4px;
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
