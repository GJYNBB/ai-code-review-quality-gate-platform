<script setup lang="ts">
/**
 * ReviewReportIssuesTab（B5-A.9）。
 *
 * 关联需求：R16.6 / R17。
 *
 * 调 issue.pageByTask；筛选 severity / status / source / filePath；
 * 点击行打开 IssueDetailDrawer（B5-A.10）。
 */
import { onMounted, reactive, ref, watch } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'

import * as issueApi from '@/api/issue'
import { formatDateTime } from '@/utils/format'
import {
  ISSUE_STATUS_LABELS,
  ISSUE_STATUS_TAG_TYPE,
  SEVERITY_LABELS,
  SEVERITY_TAG_TYPE,
} from '@/constants/issueStateMachine'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import type { CodeIssueDTO, CodeIssueStatus, IssueQuery, IssueSource, Severity } from '@/types/api'

const props = defineProps<{ taskId: number }>()

const SEVERITIES: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO']
const STATUSES: CodeIssueStatus[] = [
  'NEW',
  'CONFIRMED',
  'PENDING_VERIFY',
  'CLOSED',
  'REOPENED',
  'FALSE_POSITIVE',
]
const SOURCES: IssueSource[] = ['SAST', 'AI', 'MANUAL']

interface Filters {
  severity: Severity[]
  status: CodeIssueStatus[]
  source: IssueSource | null
  filePath: string
  page: number
  pageSize: number
}

const filters = reactive<Filters>({
  severity: [],
  status: [],
  source: null,
  filePath: '',
  page: 1,
  pageSize: 20,
})

const loading = ref(false)
const items = ref<CodeIssueDTO[]>([])
const total = ref(0)

const drawerVisible = ref(false)
const activeIssueId = ref<number | null>(null)

async function load() {
  if (!Number.isFinite(props.taskId)) return
  loading.value = true
  try {
    const query: IssueQuery = {
      page: filters.page,
      pageSize: filters.pageSize,
    }
    if (filters.severity.length > 0) query.severity = filters.severity
    if (filters.status.length > 0) query.status = filters.status
    if (filters.source) query.source = filters.source
    if (filters.filePath.trim()) query.filePath = filters.filePath.trim()

    const res = await issueApi.pageByTask(props.taskId, query)
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
  filters.severity = []
  filters.status = []
  filters.source = null
  filters.filePath = ''
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

function openDrawer(row: CodeIssueDTO) {
  activeIssueId.value = row.id
  drawerVisible.value = true
}

function handleIssueChanged() {
  void load()
}

onMounted(load)
watch(() => props.taskId, load)
</script>

<template>
  <div class="issues-tab">
    <el-card shadow="never" class="issues-tab__filters">
      <div class="filters-row">
        <span class="filters-row__label">严重度</span>
        <el-select
          v-model="filters.severity"
          multiple
          collapse-tags
          placeholder="全部"
          style="width: 200px"
        >
          <el-option v-for="s in SEVERITIES" :key="s" :label="SEVERITY_LABELS[s] ?? s" :value="s" />
        </el-select>

        <span class="filters-row__label">状态</span>
        <el-select
          v-model="filters.status"
          multiple
          collapse-tags
          placeholder="全部"
          style="width: 220px"
        >
          <el-option
            v-for="s in STATUSES"
            :key="s"
            :label="ISSUE_STATUS_LABELS[s] ?? s"
            :value="s"
          />
        </el-select>

        <span class="filters-row__label">来源</span>
        <el-select v-model="filters.source" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="s in SOURCES" :key="s" :label="s" :value="s" />
        </el-select>

        <span class="filters-row__label">文件</span>
        <el-input
          v-model="filters.filePath"
          placeholder="按路径过滤"
          clearable
          style="width: 240px"
        />

        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <el-card v-loading="loading" shadow="never" class="issues-tab__table">
      <el-table :data="items" stripe @row-click="openDrawer">
        <el-table-column label="文件 / 行" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.filePath }}</span>
            <span v-if="row.lineNo" class="lineno">L{{ row.lineNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="规则" width="200" show-overflow-tooltip>
          <template #default="{ row }"
            ><span class="mono">{{ row.ruleCode }}</span></template
          >
        </el-table-column>
        <el-table-column label="严重度" width="100">
          <template #default="{ row }">
            <el-tag :type="SEVERITY_TAG_TYPE[row.severity] ?? 'info'" size="small">
              {{ SEVERITY_LABELS[row.severity] ?? row.severity }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="80" prop="source" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag
              :type="
                ISSUE_STATUS_TAG_TYPE[row.status as keyof typeof ISSUE_STATUS_TAG_TYPE] ?? 'info'
              "
              size="small"
            >
              {{
                ISSUE_STATUS_LABELS[row.status as keyof typeof ISSUE_STATUS_LABELS] ?? row.status
              }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="issues-tab__pagination">
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

    <IssueDetailDrawer
      v-model="drawerVisible"
      :issue-id="activeIssueId"
      @changed="handleIssueChanged"
    />
  </div>
</template>

<style lang="scss" scoped>
.issues-tab {
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

  &__pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}

.mono {
  font-family: 'Source Code Pro', Consolas, monospace;
}

.lineno {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-family: 'Source Code Pro', Consolas, monospace;
}
</style>
