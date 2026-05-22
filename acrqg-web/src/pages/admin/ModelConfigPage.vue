<script setup lang="ts">
/**
 * ModelConfigPage（B5-A.12，UI-010）。
 *
 * 关联需求：R21.1 / R21.2。仅 SYSTEM_ADMIN 可见（路由守卫已拦截）。
 *
 * 列表展示 modelName / provider / endpoint / apiKeyMasked（****）+ 启用切换；
 * 新建 / 编辑对话框：apiKey 输入框 type=password。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'

import * as adminApi from '@/api/admin'
import { ApiBusinessError } from '@/api/http'
import { formatDateTime } from '@/utils/format'
import type {
  ModelConfigCreateRequest,
  ModelConfigDTO,
  ModelConfigUpdateRequest,
} from '@/types/api'

const loading = ref(false)
const items = ref<ModelConfigDTO[]>([])

async function loadList() {
  loading.value = true
  try {
    items.value = await adminApi.listModels()
  } finally {
    loading.value = false
  }
}

// ---- 编辑对话框 ----
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance | null>(null)
const editingId = ref<number | null>(null)

const form = reactive<ModelConfigCreateRequest & { id?: number }>({
  name: '',
  baseUrl: '',
  apiKey: '',
  timeoutSeconds: 30,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  apiKey: [
    {
      validator: (_r, value, cb) => {
        if (dialogMode.value === 'create' && (!value || String(value).trim().length === 0)) {
          cb(new Error('请输入 API Key'))
        } else {
          cb()
        }
      },
      trigger: 'blur',
    },
  ],
  timeoutSeconds: [
    { required: true, message: '请输入超时(秒)', trigger: 'blur' },
    { type: 'number', min: 1, max: 600, message: '范围 1-600', trigger: 'blur' },
  ],
}

function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  form.name = ''
  form.baseUrl = ''
  form.apiKey = ''
  form.timeoutSeconds = 30
  dialogVisible.value = true
}

function openEditDialog(row: ModelConfigDTO) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.name = row.name
  form.baseUrl = row.baseUrl
  form.apiKey = ''
  form.timeoutSeconds = row.timeoutSeconds
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await adminApi.createModel({
        name: form.name.trim(),
        baseUrl: form.baseUrl.trim(),
        apiKey: form.apiKey,
        timeoutSeconds: Number(form.timeoutSeconds),
      })
      ElMessage.success('已创建')
    } else if (editingId.value != null) {
      const req: ModelConfigUpdateRequest = {
        baseUrl: form.baseUrl.trim(),
        timeoutSeconds: Number(form.timeoutSeconds),
      }
      if (form.apiKey && form.apiKey.length > 0) req.apiKey = form.apiKey
      await adminApi.updateModel(editingId.value, req)
      ElMessage.success('已更新')
    }
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

async function toggleEnabled(row: ModelConfigDTO) {
  try {
    await adminApi.updateModel(row.id, { enabled: !row.enabled })
    ElMessage.success(row.enabled ? '已禁用' : '已启用')
    await loadList()
  } catch {
    /* 拦截器已弹错 */
  }
}

onMounted(loadList)
</script>

<template>
  <div class="model-config-page">
    <el-card shadow="never">
      <div class="header-row">
        <h3 class="header-row__title">AI 模型配置</h3>
        <div class="header-row__spacer" />
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建模型</el-button>
      </div>
    </el-card>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="items" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="baseUrl" label="Base URL" min-width="240" show-overflow-tooltip />
        <el-table-column prop="apiKeyMasked" label="API Key" width="160" />
        <el-table-column prop="timeoutSeconds" label="超时(秒)" width="100" align="right" />
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="toggleEnabled(row)" />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEditDialog(row)"
              >编辑</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建模型' : '编辑模型'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="如 https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item
          :label="dialogMode === 'edit' ? 'API Key（留空则不更新）' : 'API Key'"
          prop="apiKey"
        >
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="超时(秒)" prop="timeoutSeconds">
          <el-input-number v-model="form.timeoutSeconds" :min="1" :max="600" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.model-config-page {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .header-row {
    display: flex;
    align-items: center;
    gap: 12px;

    &__title {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
    }
    &__spacer {
      flex: 1 1 auto;
    }
  }
}
</style>
