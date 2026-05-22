<script setup lang="ts">
/**
 * NotificationListPage（B5-A.11）。
 *
 * 关联需求：R19。
 *
 * 筛选：read（all/unread/read）+ type（任意字符串，常见值列在常量中）
 * 列表：title / content / type / createdAt / 状态徽章
 * 点击：调 notification.markRead 并按 type 跳转：
 *   - TASK_FINISHED → /review-tasks/:taskId/report
 *   - WAIVER_REQUEST → /review-tasks/:taskId/report#waiver
 * 「全部已读」按钮 → notification.markAllRead，并刷新列表 + unreadCount
 */
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Check } from '@element-plus/icons-vue'

import * as notificationApi from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import { formatDateTime } from '@/utils/format'
import type { NotificationDTO, NotificationQuery } from '@/types/api'

const router = useRouter()
const notificationStore = useNotificationStore()

/**
 * 已知类型清单（与后端 NotificationType 保持一致；后端可能扩展，
 * 这里仅作为下拉提示，未列出的 type 仍能正常筛选）。
 */
const TYPE_OPTIONS = [
  'TASK_FINISHED',
  'TASK_FAILED',
  'WAIVER_REQUEST',
  'WAIVER_APPROVED',
  'WAIVER_REJECTED',
  'ISSUE_ASSIGNED',
  'SYSTEM',
]

interface Filters {
  read: 'all' | 'unread' | 'read'
  type: string
  page: number
  pageSize: number
}

const filters = reactive<Filters>({
  read: 'all',
  type: '',
  page: 1,
  pageSize: 20,
})

const loading = ref(false)
const items = ref<NotificationDTO[]>([])
const total = ref(0)

async function load() {
  loading.value = true
  try {
    const query: NotificationQuery = {
      page: filters.page,
      pageSize: filters.pageSize,
    }
    if (filters.read === 'unread') query.read = false
    else if (filters.read === 'read') query.read = true
    if (filters.type) query.type = filters.type
    const res = await notificationApi.page(query)
    items.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function refreshUnread() {
  try {
    const res = await notificationApi.unreadCount()
    notificationStore.setUnreadCount(res?.count ?? 0)
  } catch {
    /* skip */
  }
}

function handleSearch() {
  filters.page = 1
  void load()
}

function resetFilters() {
  filters.read = 'all'
  filters.type = ''
  filters.page = 1
  void load()
}

function handlePageChange(p: number) {
  filters.page = p
  void load()
}

function handleSizeChange(s: number) {
  filters.pageSize = s
  filters.page = 1
  void load()
}

function navigateByType(item: NotificationDTO) {
  // 优先用后端提供的 link
  if (item.link) {
    router.push(item.link)
    return
  }
  // 其次按 type + relatedId 推断
  const taskId = item.relatedId
  switch (item.type) {
    case 'TASK_FINISHED':
    case 'TASK_FAILED':
    case 'ISSUE_ASSIGNED':
      if (taskId) router.push(`/review-tasks/${taskId}/report`)
      break
    case 'WAIVER_REQUEST':
    case 'WAIVER_APPROVED':
    case 'WAIVER_REJECTED':
      if (taskId) router.push(`/review-tasks/${taskId}/report#waiver`)
      break
    default:
      // SYSTEM 等无导航
      break
  }
}

async function handleClickItem(item: NotificationDTO) {
  if (!item.read) {
    try {
      await notificationApi.markRead([item.id])
      notificationStore.markRead(item.id)
      item.read = true
    } catch {
      /* 拦截器已弹错 */
    }
    void refreshUnread()
  }
  navigateByType(item)
}

async function handleMarkAllRead() {
  try {
    await ElMessageBox.confirm('确认将所有通知标记为已读？', '操作确认', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await notificationApi.markAllRead()
    ElMessage.success('已全部标记为已读')
    notificationStore.setUnreadCount(0)
    await load()
  } catch {
    /* 拦截器已弹错 */
  }
}

onMounted(async () => {
  await load()
  await refreshUnread()
})

watch(
  () => [filters.read, filters.type] as const,
  () => {
    /* 仅当用户点击搜索时再刷新；避免下拉切换造成抖动 */
  },
)
</script>

<template>
  <div class="notification-list-page">
    <el-card shadow="never" class="notification-list-page__filters">
      <div class="filters-row">
        <span class="filters-row__label">阅读状态</span>
        <el-radio-group v-model="filters.read">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="unread">未读</el-radio-button>
          <el-radio-button value="read">已读</el-radio-button>
        </el-radio-group>

        <span class="filters-row__label">类型</span>
        <el-select v-model="filters.type" placeholder="全部" clearable style="width: 220px">
          <el-option v-for="t in TYPE_OPTIONS" :key="t" :label="t" :value="t" />
        </el-select>

        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>

        <div class="filters-row__spacer" />

        <el-button :icon="Check" @click="handleMarkAllRead">全部已读</el-button>
      </div>
    </el-card>

    <el-card v-loading="loading" shadow="never" class="notification-list-page__table">
      <el-table :data="items" stripe>
        <el-table-column label="阅读" width="80">
          <template #default="{ row }">
            <el-tag v-if="!row.read" type="danger" size="small">未读</el-tag>
            <el-tag v-else type="info" size="small">已读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="200">
          <template #default="{ row }">
            <el-link
              :type="row.read ? 'default' : 'primary'"
              :underline="false"
              @click="handleClickItem(row)"
            >
              {{ row.title }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="body" label="内容" min-width="300" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="160" />
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="notification-list-page__pagination">
        <el-pagination
          background
          :current-page="filters.page"
          :page-size="filters.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.notification-list-page {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__filters {
    .filters-row {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;

      &__label {
        color: var(--el-text-color-secondary);
        font-size: 13px;
      }

      &__spacer {
        flex: 1 1 auto;
      }
    }
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}
</style>
