<script setup lang="ts">
/**
 * 全局头部 AppHeader（B5-A.3）。
 *
 * 关联需求：
 * - R4 / R6：项目切换器（基于 projectStore）
 * - R19：未读通知红点 30s 轮询 `/notifications/unread-count`
 * - R1.6：用户菜单 / 登出
 *
 * 行为：
 * - 项目下拉绑定 projectStore.currentProjectId；切换时持久化到 localStorage
 * - mounted 时立即拉一次未读数 → 写入 notificationStore；之后每 30s 轮询；unmount 清定时器
 * - 登出：调 auth.logout API（best-effort）→ 清 store → router.push('/login')
 */
import { computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessageBox, ElMessage } from 'element-plus'
import { ArrowDown, Bell } from '@element-plus/icons-vue'

import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { useProjectStore } from '@/stores/project'
import { logout as logoutApi } from '@/api/auth'
import { unreadCount as fetchUnreadCount } from '@/api/notification'

const router = useRouter()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()
const projectStore = useProjectStore()

const { user } = storeToRefs(authStore)
const { unreadCount } = storeToRefs(notificationStore)
const { projects, currentProjectId } = storeToRefs(projectStore)

const POLL_INTERVAL_MS = 30_000
let pollTimer: ReturnType<typeof setInterval> | null = null

const projectOptions = computed(() =>
  projects.value.map((p) => ({ label: p.name, value: p.id })),
)

async function loadProjects() {
  try {
    await projectStore.loadAll()
  } catch {
    // 列表加载失败不阻塞头部展示，全局拦截器已弹出错误提示
  }
}

async function pollUnreadOnce() {
  try {
    const res = await fetchUnreadCount()
    notificationStore.setUnreadCount(res?.count ?? 0)
  } catch {
    // 轮询请求已配置 skipErrorMessage，避免噪声
  }
}

function handleProjectChange(id: number) {
  projectStore.setCurrentProject(id)
}

function goToNotifications() {
  router.push('/notifications')
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
  // 调用后端登出（best-effort：失败也继续清除本地态）
  try {
    await logoutApi()
  } catch {
    /* ignore */
  }
  authStore.logout()
  notificationStore.reset()
  projectStore.reset()
  ElMessage.success('已退出登录')
  router.push({ name: 'login' })
}

onMounted(() => {
  loadProjects()
  pollUnreadOnce()
  pollTimer = setInterval(pollUnreadOnce, POLL_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<template>
  <header class="app-header">
    <div class="app-header__brand">AI 代码评审平台</div>

    <div class="app-header__project">
      <el-select
        :model-value="currentProjectId ?? undefined"
        placeholder="选择项目"
        size="default"
        filterable
        clearable
        :empty-values="[null, undefined]"
        style="width: 220px"
        @change="handleProjectChange"
      >
        <el-option
          v-for="opt in projectOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </div>

    <div class="app-header__actions">
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
        <el-button text :icon="Bell" @click="goToNotifications">通知</el-button>
      </el-badge>

      <el-dropdown trigger="click">
        <span class="app-header__user">
          {{ user?.username ?? '未登录' }}
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>角色：{{ user?.roles?.join(', ') || '-' }}</el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style lang="scss" scoped>
.app-header {
  height: var(--acrqg-header-height, 56px);
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 24px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

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
}
</style>
