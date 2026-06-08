<script setup lang="ts">
/**
 * UI-003 项目列表（B5-A.4）。
 *
 * 关联需求：R4（项目管理）、R6（成员）。
 *
 * 功能：
 * - 关键字搜索 + 分页表格
 * - "新建项目" 按钮（仅 PROJECT_ADMIN / SYSTEM_ADMIN 可见）
 * - 点击行跳转到项目详情
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'

import * as projectApi from '@/api/project'
import { useAuthStore } from '@/stores/auth'
import { hasAnyRole } from '@/utils/permission'
import { formatDateTime } from '@/utils/format'
import type { ProjectCreateRequest, ProjectDTO, ProjectLanguage } from '@/types/api'

const router = useRouter()
const authStore = useAuthStore()

const canCreateProject = computed(() =>
  hasAnyRole(authStore.roles, ['PROJECT_ADMIN', 'SYSTEM_ADMIN']),
)

const loading = ref(false)
const items = ref<ProjectDTO[]>([])
const total = ref(0)

const query = reactive({
  keyword: '',
  page: 1,
  pageSize: 10,
})

async function loadList() {
  loading.value = true
  try {
    const res = await projectApi.page({
      keyword: query.keyword.trim() || undefined,
      page: query.page,
      pageSize: query.pageSize,
    })
    items.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  void loadList()
}

function resetSearch() {
  query.keyword = ''
  query.page = 1
  void loadList()
}

function handlePageChange(page: number) {
  query.page = page
  void loadList()
}

function handlePageSizeChange(size: number) {
  query.pageSize = size
  query.page = 1
  void loadList()
}

function viewDetail(row: ProjectDTO) {
  router.push(`/projects/${row.id}`)
}

// ---- 创建项目对话框 ----
const dialogVisible = ref(false)
const formRef = ref<FormInstance | null>(null)
const submitting = ref(false)

const LANGUAGES: ProjectLanguage[] = ['Java', 'Python', 'JavaScript', 'TypeScript', 'Go']

const form = reactive<ProjectCreateRequest>({
  name: '',
  description: '',
  defaultBranch: 'main',
  language: 'Java',
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { max: 128, message: '项目名称长度不超过 128', trigger: 'blur' },
  ],
  defaultBranch: [
    { required: true, message: '请输入默认分支', trigger: 'blur' },
    { max: 128, message: '默认分支长度不超过 128', trigger: 'blur' },
  ],
  language: [{ required: true, message: '请选择语言', trigger: 'change' }],
  description: [{ max: 512, message: '描述长度不超过 512', trigger: 'blur' }],
}

function openCreateDialog() {
  form.name = ''
  form.description = ''
  form.defaultBranch = 'main'
  form.language = 'Java'
  dialogVisible.value = true
}

async function submitCreate() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const created = await projectApi.create({
      name: form.name.trim(),
      description: form.description?.trim() || undefined,
      defaultBranch: form.defaultBranch.trim(),
      language: form.language,
    })
    ElMessage.success('项目创建成功')
    dialogVisible.value = false
    await loadList()
    // 创建成功后跳到详情，便于配置仓库 / 成员
    router.push(`/projects/${created.id}`)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadList()
})
</script>

<template>
  <div class="project-list-page">
    <el-card shadow="never" class="project-list-page__filters">
      <div class="filters-row">
        <el-input
          v-model="query.keyword"
          placeholder="按名称 / 描述搜索"
          style="width: 280px"
          clearable
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
        <div class="filters-row__spacer" />
        <el-button v-if="canCreateProject" type="primary" :icon="Plus" @click="openCreateDialog">
          新建项目
        </el-button>
      </div>
    </el-card>

    <el-card v-loading="loading" shadow="never" class="project-list-page__table">
      <el-table :data="items" stripe @row-click="viewDetail">
        <el-table-column prop="name" label="项目名称" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click.stop="viewDetail(row)">
              {{ row.name }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="language" label="语言" width="120" />
        <el-table-column prop="defaultBranch" label="默认分支" width="160" />
        <el-table-column prop="memberCount" label="成员数" width="100" align="right" />
        <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="project-list-page__pagination">
        <el-pagination
          background
          :current-page="query.page"
          :page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建项目" width="520px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="项目名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="如：智评平台后端"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="默认分支" prop="defaultBranch">
          <el-input v-model="form.defaultBranch" placeholder="main" maxlength="128" />
        </el-form-item>
        <el-form-item label="语言" prop="language">
          <el-select v-model="form.language" placeholder="请选择语言" style="width: 100%">
            <el-option v-for="lang in LANGUAGES" :key="lang" :label="lang" :value="lang" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.project-list-page {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__filters {
    .filters-row {
      display: flex;
      align-items: center;
      gap: 12px;

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
