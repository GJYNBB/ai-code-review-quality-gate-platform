<script setup lang="ts">
/**
 * SystemParamPage（B5-A.12，UI-010）。
 *
 * 关联需求：R21.4。仅 SYSTEM_ADMIN 可见（路由守卫已拦截）。
 *
 * 调 admin.listSystemParams + admin.updateSystemParam；
 * 可编辑表格；敏感参数显示 **** 不可编辑明文（可设置新值）。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Edit, Refresh, Search } from '@element-plus/icons-vue'

import * as adminApi from '@/api/admin'
import { ApiBusinessError } from '@/api/http'
import { formatDateTime } from '@/utils/format'
import type { SystemParamDTO } from '@/types/api'

const loading = ref(false)
const items = ref<SystemParamDTO[]>([])
const filterPrefix = ref('')

async function loadList() {
  loading.value = true
  try {
    items.value = await adminApi.listSystemParams(filterPrefix.value.trim() || undefined)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  void loadList()
}

function resetFilters() {
  filterPrefix.value = ''
  void loadList()
}

// ---- 编辑对话框 ----
const dialogVisible = ref(false)
const submitting = ref(false)
const editing = reactive({
  paramKey: '',
  paramValue: '',
  sensitive: false,
})

function openEdit(row: SystemParamDTO) {
  editing.paramKey = row.paramKey
  editing.paramValue = row.sensitive ? '' : row.paramValue
  editing.sensitive = row.sensitive
  dialogVisible.value = true
}

async function submitEdit() {
  if (!editing.paramKey) return
  if (editing.paramValue.length === 0) {
    ElMessage.warning('请输入新值')
    return
  }
  submitting.value = true
  try {
    await adminApi.updateSystemParam(editing.paramKey, editing.paramValue)
    ElMessage.success('已更新')
    dialogVisible.value = false
    await loadList()
  } catch (err) {
    if (err instanceof ApiBusinessError) {
      // 拦截器已弹错
    }
  } finally {
    submitting.value = false
  }
}

onMounted(loadList)
</script>

<template>
  <div class="system-param-page">
    <el-card shadow="never">
      <div class="filters-row">
        <el-input
          v-model="filterPrefix"
          placeholder="按 key 前缀过滤（如 ai. / gate.）"
          style="width: 280px"
          clearable
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="items" stripe>
        <el-table-column prop="paramKey" label="参数 Key" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.paramKey }}</span>
          </template>
        </el-table-column>
        <el-table-column label="值" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.sensitive" class="mono text-secondary">****（敏感参数）</span>
            <span v-else class="mono">{{ row.paramValue }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column label="敏感" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.sensitive" type="warning" size="small">敏感</el-tag>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="Edit" @click="openEdit(row)">
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="`编辑参数：${editing.paramKey}`"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="新值" required>
          <el-input
            v-model="editing.paramValue"
            :type="editing.sensitive ? 'password' : 'text'"
            :show-password="editing.sensitive"
            :placeholder="editing.sensitive ? '请输入新的敏感值（不显示明文）' : '请输入新值'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.system-param-page {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .filters-row {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.mono {
  font-family: 'Source Code Pro', Consolas, monospace;
}

.text-secondary {
  color: var(--el-text-color-secondary);
}
</style>
