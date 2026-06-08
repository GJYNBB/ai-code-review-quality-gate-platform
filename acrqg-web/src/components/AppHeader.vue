<script setup lang="ts">
/**
 * 顶部工具栏：项目切换、通知、用户会话。
 * 视觉上作为控制台 command bar，功能保持原实现。
 */
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Bell, FolderOpened, SwitchButton } from '@element-plus/icons-vue'

import { logout as logoutApi } from '@/api/auth'
import { unreadCount as fetchUnreadCount } from '@/api/notification'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { useProjectStore } from '@/stores/project'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()
const projectStore = useProjectStore()

const { user } = storeToRefs(authStore)
const { unreadCount } = storeToRefs(notificationStore)
const { projects, currentProjectId } = storeToRefs(projectStore)

const POLL_INTERVAL_MS = 30_000
let pollTimer: ReturnType<typeof setInterval> | null = null

const projectOptions = computed(() => projects.value.map((p) => ({ label: p.name, value: p.id })))
const currentProjectName = computed(
  () => projects.value.find((p) => p.id === currentProjectId.value)?.name || '未选择项目',
)
const userInitial = computed(() => (user.value?.username || 'U').slice(0, 1).toUpperCase())
const roleLabel = computed(() => user.value?.roles?.join(' / ') || '未分配角色')
const routeTitle = computed(() => String(route.meta.title || '控制台'))

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

function handleProjectChange(id: number | null) {
  projectStore.setCurrentProject(id)
}

function goToNotifications() {
  router.push('/notifications')
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录？', '退出登录', {
      confirmButtonText: '确认退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  try {
    await logoutApi(authStore.refreshToken ?? undefined)
  } catch {
    /* best-effort */
  }
  authStore.logout()
  notificationStore.reset()
  projectStore.reset()
  ElMessage.success('已退出登录')
  router.push({ name: 'login' })
}

onMounted(() => {
  void loadProjects()
  void pollUnreadOnce()
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
    <div class="app-header__context">
      <span class="context-pill">{{ routeTitle }}</span>
      <strong>{{ currentProjectName }}</strong>
    </div>

    <div class="app-header__project">
      <el-icon><FolderOpened /></el-icon>
      <el-select
        :model-value="currentProjectId ?? undefined"
        placeholder="选择项目"
        filterable
        clearable
        :empty-values="[null, undefined]"
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
        <el-button class="icon-action" :icon="Bell" circle @click="goToNotifications" />
      </el-badge>

      <el-dropdown trigger="click">
        <button class="user-chip" type="button">
          <span class="user-chip__avatar">{{ userInitial }}</span>
          <span class="user-chip__copy">
            <strong>{{ user?.username ?? '未登录' }}</strong>
            <small>{{ roleLabel }}</small>
          </span>
          <el-icon><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>角色：{{ user?.roles?.join(', ') || '-' }}</el-dropdown-item>
            <el-dropdown-item divided :icon="SwitchButton" @click="handleLogout"
              >退出登录</el-dropdown-item
            >
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style lang="scss" scoped>
.app-header {
  height: var(--acrqg-header-height);
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 0 28px;
  background: rgba(247, 250, 255, 0.78);
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(18px);

  &__context {
    min-width: 220px;
    display: flex;
    flex-direction: column;
    gap: 4px;

    strong {
      color: var(--acrqg-text-primary);
      font-size: 15px;
      line-height: 1.1;
    }
  }

  &__project {
    width: min(420px, 34vw);
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border: 1px solid rgba(15, 23, 42, 0.08);
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.82);
    box-shadow: 0 8px 22px rgba(15, 23, 42, 0.04);

    .el-icon {
      color: var(--acrqg-accent);
      margin-left: 4px;
    }

    :deep(.el-select) {
      flex: 1;
    }

    :deep(.el-select__wrapper) {
      min-height: 32px;
      background: transparent !important;
      box-shadow: none !important;
    }
  }

  &__actions {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.context-pill {
  width: fit-content;
  padding: 3px 9px;
  border-radius: 999px;
  color: var(--acrqg-accent);
  background: var(--acrqg-accent-soft);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.icon-action {
  width: 40px;
  height: 40px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05);
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px 7px 7px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05);
  cursor: pointer;

  &__avatar {
    width: 34px;
    height: 34px;
    display: grid;
    place-items: center;
    border-radius: 999px;
    color: #fff;
    background: linear-gradient(135deg, var(--acrqg-accent), var(--acrqg-accent-2));
    font-weight: 850;
  }

  &__copy {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;

    strong {
      color: var(--acrqg-text-primary);
      font-size: 13px;
      line-height: 1;
    }

    small {
      max-width: 180px;
      color: var(--acrqg-text-muted);
      font-size: 11px;
      line-height: 1;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
}

@media (max-width: 900px) {
  .app-header {
    padding: 0 16px;

    &__context,
    .user-chip__copy {
      display: none;
    }

    &__project {
      width: min(360px, 56vw);
    }
  }
}
</style>
