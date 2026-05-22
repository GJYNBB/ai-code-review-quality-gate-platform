<script setup lang="ts">
/**
 * ScannerConfigPage（B5-A.12，UI-010）。
 *
 * 关联需求：R21.3。仅 SYSTEM_ADMIN 可见。
 *
 * 表格：name / language / enabled / command / resultParserType + 启用切换；
 * 新建 / 编辑：command 字段为 textarea，提示支持 {workdir} {file} 占位符。
 * upsert 接口以 name 为业务键。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'

import * as adminApi from '@/api/admin'
import { ApiBusinessError } from '@/api/http'
import { formatDateTime } from '@/utils/format'
import type { ScannerConfigDTO, ScannerConfigRequest } from '@/types/api'

const loading = ref(false)
const items = ref<ScannerConfigDTO[]>([])

async function loadList() {
  loading.value = true
  try {
    items.value = await adminApi.listScanners()
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance | null>(null)

const form = reactive<ScannerConfigRequest>({
  name: '',
  language: 'Java',
  enabled: true,
  command: '',
  resultParserType: 'SARIF',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  language: [{ required: true, message: '请选择语言', trigger: 'change' }],
  command: [{ required: true, message: '请输入扫描命令', trigger: 'blur' }],
  resultParserType: [{ required: true, message: '请选择结果解析器', trigger: 'change' }],
}

const LANGUAGES = ['Java', 'Python', 'JavaScript', 'TypeScript', 'Go']
const PARSER_TYPES = ['SARIF', 'SEMGREP_JSON', 'PYLINT_JSON', 'ESLINT_JSON', 'GENERIC_JSON']

function openCreateDialog() {
  dialogMode.value = 'create'
  form.name = ''
  form.language = 'Java'
  form.enabled = true
  form.command = ''
  form.resultParserType = 'SARIF'
  dialogVisible.value = true
}

function openEditDialog(row: ScannerConfigDTO) {
  dialogMode.value = 'edit'
  form.name = row.name
  form.language = row.language
  form.enabled = row.enabled
  form.command = row.command
  form.resultParserType = row.resultParserType
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
    await adminApi.upsertScanner({
      name: form.name.trim(),
      language: form.language,
      enabled: form.enabled,
      command: form.command,
      resultParserType: form.resultParserType,
    })
    ElMessage.success(dialogMode.value === 'create' ? '已创建' : '已更新')
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

async function toggleEnabled(row: ScannerConfigDTO) {
  try {
    await adminApi.upsertScanner({
      name: row.name,
      language: row.language,
      enabled: !row.enabled,
      command: row.command,
      resultParserType: row.resultParserType,
    })
    ElMessage.success(row.enabled ? '已禁用' : '已启用')
    await loadList()
  } catch {
    /* 拦截器已弹错 */
  }
}

onMounted(loadList)
</script>

<template>
  <div class="scanner-config-page">
    <el-card shadow="never">
      <div class="header-row">
        <h3 class="header-row__title">扫描器配置</h3>
        <div class="header-row__spacer" />
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建扫描器</el-button>
      </div>
    </el-card>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="items" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="language" label="语言" width="120" />
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="toggleEnabled(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="command" label="扫描命令" min-width="320" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.command }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="resultParserType" label="解析器" width="160" />
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
      :title="dialogMode === 'create' ? '新建扫描器' : '编辑扫描器'"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="语言" prop="language">
          <el-select v-model="form.language" style="width: 100%">
            <el-option v-for="l in LANGUAGES" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="扫描命令" prop="command">
          <el-input
            v-model="form.command"
            type="textarea"
            :rows="3"
            placeholder="支持占位符 {workdir} 与 {file}"
          />
          <div class="hint">
            支持 <span class="mono">{workdir}</span> / <span class="mono">{file}</span> 占位符
          </div>
        </el-form-item>
        <el-form-item label="结果解析器" prop="resultParserType">
          <el-select v-model="form.resultParserType" style="width: 100%">
            <el-option v-for="p in PARSER_TYPES" :key="p" :label="p" :value="p" />
          </el-select>
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
.scanner-config-page {
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

.mono {
  font-family: 'Source Code Pro', Consolas, monospace;
}

.hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
