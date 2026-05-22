<script setup lang="ts">
/**
 * AuditLogPage（B5-A.12，UI-010）。
 *
 * 关联需求：R22。仅 SYSTEM_ADMIN 可见（路由守卫已拦截）。
 *
 * 调 admin.listAuditLogs，按 operator / action / 时间分页；
 * detail 字段折叠展开为 pre 标签 JSON 美化展示。
 */
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'

import * as adminApi from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import type { AuditLogDTO, AuditQuery } from '@/types/api'

const loading = ref(false)
const items = ref<AuditLogDTO[]>([])
const total = ref(0)

interface Filters {
  operator: string
  action: string
  dateRange: [string, string] | null
  page: number
  pageSize: number
}

const filters = reactive<Filters>({
  operator: '',
  action: '',
  dateRange: null,
  page: 1,
  pageSize: 20,
})

async function loadList() {
  loading.value = true
  try {
    const query: AuditQuery = {
      page: filters.page,
      pageSize: filters.pageSize,
    }
    if (filters.operator.trim()) query.operator = filters.operator.trim()
    if (filters.action.trim()) query.action = filters.action.trim()
    if (filters.dateRange) {
      query.startDate = filters.dateRange[0]
      query.endDate = filters.dateRange[1]
    }
    const res = await adminApi.listAuditLogs(query)
    items.value = res.items
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
  filters.operator = ''
  filters.action = ''
  filters.dateRange = null
  filters.page = 1
  void loadList()
}

function handlePageChange(p: number) {
  filters.page = p
  void loadList()
}

function handleSizeChange(s: number) {
  filters.pageSize = s
  filters.page = 1
  void loadList()
}

function detailJson(detail: Record<string, unknown> | null | undefined): string {
  if (!detail) return ''
  try {
    return JSON.stringify(detail, null, 2)
  } catch {
    return String(detail)
  }
}

onMounted(loadList)
</script>

<template>
  <div class="audit-log-page">
    <el-card shadow="never">
      <div class="filters-row">
        <el-input
          v-model="filters.operator"
          placeholder="操作人"
          style="width: 200px"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="filters.action"
          placeholder="动作（如 USER_CREATED）"
          style="width: 240px"
          clearable
          @keyup.enter="handleSearch"
        />
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
      </div>
    </el-card>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="items" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <pre v-if="row.detail" class="audit-log-page__detail">{{ detailJson(row.detail) }}</pre>
            <span v-else class="text-secondary">无附加详情</span>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="160">
          <template #default="{ row }">
            {{ row.operatorUsername ?? `#${row.operatorId ?? '-'}` }}
          </template>
        </el-table-column>
        <el-table-column prop="action" label="动作" width="200" />
        <el-table-column prop="resourceType" label="资源类型" width="160" />
        <el-table-column prop="resourceId" label="资源 ID" min-width="160" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="160" />
      </el-table>

      <div class="audit-log-page__pagination">
        <el-pagination
          background
          :current-page="filters.page"
          :page-size="filters.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.audit-log-page {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .filters-row {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
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
