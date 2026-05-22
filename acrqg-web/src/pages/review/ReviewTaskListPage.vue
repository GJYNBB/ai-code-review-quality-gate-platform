<script setup lang="ts">
/**
 * UI-006 评审任务列表（B5-A.8）。
 *
 * 关联需求：R8.1 / R8.2 / R8.4 / R9。
 *
 * 顶部筛选区：项目 / 状态（多选）/ 触发类型 / 时间范围 / 重置
 * 表格：taskNo / project / pr / commit(7位) / status(tag) / trigger / score / createdAt / 操作
 * 操作：详情 → /review-tasks/:id/report
 * "创建任务"按钮：仅项目成员可见（DEVELOPER/REVIEWER/PROJECT_ADMIN/SYSTEM_ADMIN）
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import dayjs from 'dayjs'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'

import * as reviewTaskApi from '@/api/reviewTask'
import { useProjectStore } from '@/stores/project'
import { useAuthStore } from '@/stores/auth'
import { hasAnyRole } from '@/utils/permission'
import { formatDateTime } from '@/utils/format'
import {
  ALL_REVIEW_TASK_STATUSES,
  ALL_TRIGGER_TYPES,
  REVIEW_TASK_STATUS_LABELS,
  REVIEW_TASK_STATUS_TAG_TYPE,
  TRIGGER_TYPE_LABELS,
} from '@/constants/reviewTaskStatus'
import type { ReviewTaskDTO, ReviewTaskQuery, ReviewTaskStatus, TriggerType } from '@/types/api'
import CreateTaskDialog from '@/pages/review/CreateTaskDialog.vue'

const router = useRouter()
const projectStore = useProjectStore()
const authStore = useAuthStore()
const { projects, currentProjectId } = storeToRefs(projectStore)

/** 创建任务按钮的可见角色：项目成员（开发/评审）以上 */
const canCreateTask = computed(() =>
  hasAnyRole(authStore.roles, ['DEVELOPER', 'REVIEWER', 'PROJECT_ADMIN', 'SYSTEM_ADMIN']),
)

const loading = ref(false)
const items = ref<ReviewTaskDTO[]>([])
const total = ref(0)

interface FilterState {
  projectId: number | null
  statuses: ReviewTaskStatus[]
  triggerType: TriggerType | null
  dateRange: [string, string] | null
  page: number
  pageSize: number
}

const filters = reactive<FilterState>({
  projectId: currentProjectId.value ?? null,
  statuses: [],
  triggerType: null,
  dateRange: null,
  page: 1,
  pageSize: 10,
})

const projectMap = computed(() => {
  const map = new Map<number, string>()
  projects.value.forEach((p) => map.set(p.id, p.name))
  return map
})

function shortSha(sha?: string | null): string {
  if (!sha) return '-'
  return sha.length > 7 ? sha.slice(0, 7) : sha
}

async function loadList() {
  loading.value = true
  try {
    const query: ReviewTaskQuery = {
      page: filters.page,
      pageSize: filters.pageSize,
    }
    if (filters.projectId != null) query.projectId = filters.projectId
    // 多选状态时取第一个传给后端（后端当前接口仅支持单值）；其余在前端过滤
    if (filters.statuses.length > 0) query.status = filters.statuses[0]
    if (filters.triggerType) query.triggerType = filters.triggerType

    const res = await reviewTaskApi.page(query)
    let data = res.items
    // 客户端补充：多状态多选过滤 + 时间区间过滤
    if (filters.statuses.length > 1) {
      data = data.filter((t) => filters.statuses.includes(t.status as ReviewTaskStatus))
    }
    if (filters.dateRange) {
      const [start, end] = filters.dateRange
      const startTs = dayjs(start).startOf('day').valueOf()
      const endTs = dayjs(end).endOf('day').valueOf()
      data = data.filter((t) => {
        const ts = dayjs(t.createdAt).valueOf()
        return ts >= startTs && ts <= endTs
      })
    }
    items.value = data
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  filters.page = 1
  void loadList()
}

function resetFilters() {
  filters.projectId = currentProjectId.value ?? null
  filters.statuses = []
  filters.triggerType = null
  filters.dateRange = null
  filters.page = 1
  void loadList()
}

function handlePageChange(p: number) {
  filters.page = p
  void loadList()
}

function handlePageSizeChange(size: number) {
  filters.pageSize = size
  filters.page = 1
  void loadList()
}

function viewDetail(row: ReviewTaskDTO) {
  router.push(`/review-tasks/${row.id}/report`)
}

const dialogVisible = ref(false)
function openCreateDialog() {
  dialogVisible.value = true
}
function handleCreated() {
  void loadList()
}

onMounted(async () => {
  await projectStore.loadAll()
  if (filters.projectId == null) filters.projectId = currentProjectId.value ?? null
  await loadList()
})

watch(currentProjectId, (id) => {
  // 全局项目切换时同步筛选项
  if (filters.projectId == null) {
    filters.projectId = id ?? null
    void loadList()
  }
})
</script>

<template>
  <div class="review-task-list-page">
    <el-card shadow="never" class="review-task-list-page__filters">
      <div class="filters-row">
        <span class="filters-row__label">项目</span>
        <el-select
          v-model="filters.projectId"
          placeholder="全部项目"
          clearable
          filterable
          style="width: 220px"
        >
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>

        <span class="filters-row__label">状态</span>
        <el-select
          v-model="filters.statuses"
          multiple
          collapse-tags
          placeholder="全部状态"
          style="width: 280px"
        >
          <el-option
            v-for="s in ALL_REVIEW_TASK_STATUSES"
            :key="s"
            :label="REVIEW_TASK_STATUS_LABELS[s]"
            :value="s"
          />
        </el-select>

        <span class="filters-row__label">触发类型</span>
        <el-select v-model="filters.triggerType" placeholder="全部" clearable style="width: 160px">
          <el-option
            v-for="t in ALL_TRIGGER_TYPES"
            :key="t"
            :label="TRIGGER_TYPE_LABELS[t]"
            :value="t"
          />
        </el-select>

        <span class="filters-row__label">时间范围</span>
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="—"
          start-placeholder="开始"
          end-placeholder="结束"
        />

        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
        <div class="filters-row__spacer" />
        <el-button v-if="canCreateTask" type="primary" :icon="Plus" @click="openCreateDialog">
          创建任务
        </el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="review-task-list-page__table" v-loading="loading">
      <el-table :data="items" stripe @row-click="viewDetail">
        <el-table-column prop="taskNo" label="任务编号" width="200">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click.stop="viewDetail(row)">
              {{ row.taskNo }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="项目" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ projectMap.get(row.projectId) ?? `#${row.projectId}` }}
          </template>
        </el-table-column>
        <el-table-column label="PR / commit" min-width="200">
          <template #default="{ row }">
            <span v-if="row.prId">PR #{{ row.prId }}</span>
            <span v-else class="text-secondary">-</span>
            <span class="commit-sha">{{ shortSha(row.commitSha) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag
              :type="
                REVIEW_TASK_STATUS_TAG_TYPE[
                  row.status as keyof typeof REVIEW_TASK_STATUS_TAG_TYPE
                ] ?? 'info'
              "
              size="small"
            >
              {{
                REVIEW_TASK_STATUS_LABELS[row.status as keyof typeof REVIEW_TASK_STATUS_LABELS] ??
                row.status
              }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发" width="100">
          <template #default="{ row }">
            {{
              TRIGGER_TYPE_LABELS[row.triggerType as keyof typeof TRIGGER_TYPE_LABELS] ??
              row.triggerType
            }}
          </template>
        </el-table-column>
        <el-table-column label="得分" width="90" align="right">
          <template #default="{ row }">
            {{ row.score != null ? Number(row.score).toFixed(1) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="viewDetail(row)"
              >详情</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <div class="review-task-list-page__pagination">
        <el-pagination
          background
          :current-page="filters.page"
          :page-size="filters.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <CreateTaskDialog
      v-model="dialogVisible"
      :default-project-id="filters.projectId"
      @created="handleCreated"
    />
  </div>
</template>

<style lang="scss" scoped>
.review-task-list-page {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__filters {
    .filters-row {
      display: flex;
      align-items: center;
      gap: 8px;
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

.commit-sha {
  display: inline-block;
  margin-left: 8px;
  font-family: 'Source Code Pro', Consolas, monospace;
  color: var(--el-text-color-secondary);
}

.text-secondary {
  color: var(--el-text-color-secondary);
}
</style>
