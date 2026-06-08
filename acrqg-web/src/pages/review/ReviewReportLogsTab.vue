<script setup lang="ts">
/**
 * ReviewReportLogsTab（B5-A.9）。
 *
 * 关联需求：R16.5 / R9.7。
 *
 * 调 report.logs；筛选 stage / level；分页表格 timestamp / stage / level / message；
 * level=ERROR 行展开显示 detail（stack trace 等）。
 */
import { onMounted, reactive, ref, watch } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'

import * as reportApi from '@/api/report'
import { formatDateTime } from '@/utils/format'
import type { TaskLogDTO, TaskLogQuery } from '@/types/api'

const props = defineProps<{ taskId: number }>()

const STAGE_OPTIONS = [
  'PENDING',
  'FETCHING_DIFF',
  'STATIC_SCANNING',
  'AI_REVIEWING',
  'GATE_EVALUATING',
  'WRITEBACK',
  'NOTIFICATION',
  'WORKER_RESUME',
]
const LEVEL_OPTIONS: Array<'INFO' | 'WARN' | 'ERROR'> = ['INFO', 'WARN', 'ERROR']

interface Filters {
  stage: string
  level: 'INFO' | 'WARN' | 'ERROR' | ''
  page: number
  pageSize: number
}

const filters = reactive<Filters>({
  stage: '',
  level: '',
  page: 1,
  pageSize: 20,
})

const loading = ref(false)
const items = ref<TaskLogDTO[]>([])
const total = ref(0)

async function load() {
  if (!Number.isFinite(props.taskId)) return
  loading.value = true
  try {
    const query: TaskLogQuery = {
      page: filters.page,
      pageSize: filters.pageSize,
    }
    if (filters.stage) query.stage = filters.stage
    if (filters.level) query.level = filters.level

    const res = await reportApi.logs(props.taskId, query)
    items.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  filters.page = 1
  void load()
}

function resetFilters() {
  filters.stage = ''
  filters.level = ''
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

function levelTagType(level: string): 'info' | 'warning' | 'danger' {
  if (level === 'ERROR') return 'danger'
  if (level === 'WARN') return 'warning'
  return 'info'
}

function detailJson(detail: Record<string, unknown> | null | undefined): string {
  if (!detail) return ''
  try {
    return JSON.stringify(detail, null, 2)
  } catch {
    return String(detail)
  }
}

onMounted(load)
watch(() => props.taskId, load)
</script>

<template>
  <div class="logs-tab">
    <el-card shadow="never" class="logs-tab__filters">
      <div class="filters-row">
        <span class="filters-row__label">阶段</span>
        <el-select v-model="filters.stage" placeholder="全部" clearable style="width: 200px">
          <el-option v-for="s in STAGE_OPTIONS" :key="s" :label="s" :value="s" />
        </el-select>

        <span class="filters-row__label">级别</span>
        <el-select v-model="filters.level" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="l in LEVEL_OPTIONS" :key="l" :label="l" :value="l" />
        </el-select>

        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <el-card v-loading="loading" shadow="never" class="logs-tab__table">
      <el-table :data="items" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <pre v-if="row.detail" class="logs-tab__detail">{{ detailJson(row.detail) }}</pre>
            <span v-else class="text-secondary">无附加详情</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="200">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="stage" label="阶段" width="160" />
        <el-table-column label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="levelTagType(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="消息" min-width="320" show-overflow-tooltip />
      </el-table>

      <div class="logs-tab__pagination">
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
.logs-tab {
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
    }
  }

  &__detail {
    background: var(--el-fill-color-light);
    padding: 12px;
    border-radius: 4px;
    font-family: 'Source Code Pro', Consolas, monospace;
    font-size: 12px;
    white-space: pre-wrap;
    margin: 0;
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}

.text-secondary {
  color: var(--el-text-color-secondary);
}
</style>
