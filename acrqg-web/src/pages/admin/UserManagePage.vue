<script setup lang="ts">
/**
 * UserManagePage（B5-A.12，UI-010）。
 *
 * 关联需求：R3 / R23.3。仅 SYSTEM_ADMIN 可见（路由守卫已拦截）。
 *
 * 功能：
 * - 列表 + 搜索 + 角色 / 状态筛选 + 分页
 * - 创建用户对话框：username / email / password / roles
 * - 启用 / 禁用切换：调 user.changeStatus
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'

import * as userApi from '@/api/user'
import { ApiBusinessError } from '@/api/http'
import { formatDateTime } from '@/utils/format'
import type { Role, UserCreateRequest, UserDTO, UserStatus } from '@/types/api'

const ROLES: Role[] = ['DEVELOPER', 'REVIEWER', 'PROJECT_ADMIN', 'SYSTEM_ADMIN', 'CI_CD']

const loading = ref(false)
const items = ref<UserDTO[]>([])
const total = ref(0)

interface Filters {
  keyword: string
  status: UserStatus | ''
  role: Role | ''
  page: number
  pageSize: number
}

const filters = reactive<Filters>({
  keyword: '',
  status: '',
  role: '',
  page: 1,
  pageSize: 20,
})

async function loadList() {
  loading.value = true
  try {
    const res = await userApi.page({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      role: filters.role || undefined,
      page: filters.page,
      pageSize: filters.pageSize,
    })
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
  filters.keyword = ''
  filters.status = ''
  filters.role = ''
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

// ---- 创建用户对话框 ----
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance | null>(null)
const form = reactive<UserCreateRequest>({
  username: '',
  email: '',
  password: '',
  roles: ['DEVELOPER'],
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '长度 3-64', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 128, message: '长度 8-128', trigger: 'blur' },
  ],
  roles: [
    { required: true, type: 'array', min: 1, message: '至少分配一个角色', trigger: 'change' },
  ],
}

function openCreateDialog() {
  form.username = ''
  form.email = ''
  form.password = ''
  form.roles = ['DEVELOPER']
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
    await userApi.create({
      username: form.username.trim(),
      email: form.email.trim(),
      password: form.password,
      roles: form.roles,
    })
    ElMessage.success('用户已创建')
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

// ---- 启用 / 禁用 ----
async function toggleStatus(row: UserDTO) {
  const next: UserStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const action = next === 'ENABLED' ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认${action}用户「${row.username}」？`, '操作确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await userApi.changeStatus(row.id, next)
    ElMessage.success(`已${action}`)
    await loadList()
  } catch (err) {
    if (err instanceof ApiBusinessError) {
      // 拦截器已弹错
    }
  }
}

onMounted(loadList)
</script>

<template>
  <div class="user-manage-page">
    <el-card shadow="never" class="user-manage-page__filters">
      <div class="filters-row">
        <el-input
          v-model="filters.keyword"
          placeholder="按用户名 / 邮箱搜索"
          style="width: 240px"
          clearable
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="启用" value="ENABLED" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
        <el-select v-model="filters.role" placeholder="角色" clearable style="width: 200px">
          <el-option v-for="r in ROLES" :key="r" :label="r" :value="r" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
        <div class="filters-row__spacer" />
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建用户</el-button>
      </div>
    </el-card>

    <el-card v-loading="loading" shadow="never" class="user-manage-page__table">
      <el-table :data="items" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="160" />
        <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
        <el-table-column label="角色" min-width="220">
          <template #default="{ row }">
            <el-tag
              v-for="r in row.roles"
              :key="r"
              size="small"
              type="info"
              effect="plain"
              style="margin-right: 4px"
            >
              {{ r }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              size="small"
              :type="row.status === 'ENABLED' ? 'danger' : 'primary'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 'ENABLED' ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="user-manage-page__pagination">
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

    <el-dialog v-model="dialogVisible" title="新建用户" width="520px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" maxlength="64" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" maxlength="128" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" maxlength="128" show-password />
        </el-form-item>
        <el-form-item label="角色" prop="roles">
          <el-select v-model="form.roles" multiple style="width: 100%" placeholder="选择角色">
            <el-option v-for="r in ROLES" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.user-manage-page {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__filters {
    .filters-row {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
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
