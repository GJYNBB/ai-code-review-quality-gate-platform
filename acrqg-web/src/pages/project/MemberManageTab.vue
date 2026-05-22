<script setup lang="ts">
/**
 * UI-004 成员管理标签页（B5-A.6）。
 *
 * 关联需求：R6（项目成员管理）。
 *
 * 功能：
 * - 成员列表（username / role / joinedAt）
 * - 「添加成员」对话框：用户搜索（el-autocomplete + user.page）+ projectRole 下拉
 * - 「移除」操作：el-popconfirm 确认后调 project.removeMember
 * - 仅当前用户为该项目 PROJECT_ADMIN 或全局 SYSTEM_ADMIN 时可见添加 / 移除
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

import * as projectApi from '@/api/project'
import * as userApi from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { hasAnyRole } from '@/utils/permission'
import { formatDateTime } from '@/utils/format'
import type {
  AddMemberRequest,
  ProjectMemberDTO,
  ProjectRole,
  UserDTO,
} from '@/types/api'

const props = defineProps<{
  projectId: number
}>()

const authStore = useAuthStore()

const PROJECT_ROLES: Array<{ label: string; value: ProjectRole }> = [
  { label: '开发者（DEVELOPER）', value: 'DEVELOPER' },
  { label: '评审人（REVIEWER）', value: 'REVIEWER' },
  { label: '项目管理员（PROJECT_ADMIN）', value: 'PROJECT_ADMIN' },
]

const loading = ref(false)
const members = ref<ProjectMemberDTO[]>([])

/** 当前用户是否拥有该项目的管理权限 */
const canManage = computed(() => {
  // 全局 SYSTEM_ADMIN：直接放行
  if (hasAnyRole(authStore.roles, ['SYSTEM_ADMIN'])) return true
  // 项目级 PROJECT_ADMIN：基于成员列表中本人的 role
  const me = members.value.find((m) => m.userId === authStore.user?.id)
  return me?.role === 'PROJECT_ADMIN'
})

async function loadMembers() {
  if (!Number.isFinite(props.projectId)) return
  loading.value = true
  try {
    members.value = await projectApi.listMembers(props.projectId)
  } finally {
    loading.value = false
  }
}

// ---- 添加成员 ----
const dialogVisible = ref(false)
const formRef = ref<FormInstance | null>(null)
const submitting = ref(false)

const form = reactive<AddMemberRequest & { _displayLabel?: string }>({
  userId: 0,
  role: 'DEVELOPER',
})
const userSearchKeyword = ref('')

const rules: FormRules = {
  userId: [
    {
      required: true,
      validator: (_rule, _v, cb) => {
        if (!form.userId || form.userId <= 0) cb(new Error('请选择要添加的用户'))
        else cb()
      },
      trigger: 'change',
    },
  ],
  role: [{ required: true, message: '请选择项目角色', trigger: 'change' }],
}

function openAddDialog() {
  form.userId = 0
  form.role = 'DEVELOPER'
  form._displayLabel = ''
  userSearchKeyword.value = ''
  dialogVisible.value = true
}

interface UserSuggestion {
  value: string
  user: UserDTO
}

async function queryUserAsync(
  keyword: string,
  cb: (data: UserSuggestion[]) => void,
) {
  const trimmed = keyword.trim()
  if (!trimmed) {
    cb([])
    return
  }
  try {
    const res = await userApi.page({
      keyword: trimmed,
      status: 'ENABLED',
      page: 1,
      pageSize: 10,
    })
    const memberIds = new Set(members.value.map((m) => m.userId))
    cb(
      res.items
        .filter((u) => !memberIds.has(u.id))
        .map((u) => ({ value: `${u.username} (${u.email})`, user: u })),
    )
  } catch {
    cb([])
  }
}

function handleUserSelect(suggestion: UserSuggestion) {
  form.userId = suggestion.user.id
  form._displayLabel = suggestion.value
  userSearchKeyword.value = suggestion.value
}

function handleKeywordChange(val: string) {
  // 用户清空 / 修改输入时清掉已选 id
  if (val !== form._displayLabel) {
    form.userId = 0
    form._displayLabel = ''
  }
}

async function submitAdd() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await projectApi.addMember(props.projectId, {
      userId: form.userId,
      role: form.role,
    })
    ElMessage.success('成员已添加')
    dialogVisible.value = false
    await loadMembers()
  } finally {
    submitting.value = false
  }
}

// ---- 移除成员 ----
async function removeMember(row: ProjectMemberDTO) {
  await projectApi.removeMember(props.projectId, row.userId)
  ElMessage.success('已移除成员')
  await loadMembers()
}

onMounted(() => {
  void loadMembers()
})

watch(
  () => props.projectId,
  () => {
    void loadMembers()
  },
)
</script>

<template>
  <div class="member-manage-tab" v-loading="loading">
    <div class="member-manage-tab__toolbar">
      <span>共 {{ members.length }} 位成员</span>
      <div class="member-manage-tab__spacer" />
      <el-button
        v-if="canManage"
        type="primary"
        :icon="Plus"
        @click="openAddDialog"
      >
        添加成员
      </el-button>
    </div>

    <el-table :data="members" stripe>
      <el-table-column prop="username" label="用户名" min-width="160" />
      <el-table-column label="项目角色" width="200">
        <template #default="{ row }">
          <el-tag size="small" :type="row.role === 'PROJECT_ADMIN' ? 'danger' : 'info'">
            {{ row.role }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="加入时间" width="200">
        <template #default="{ row }">{{ formatDateTime(row.joinedAt) }}</template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-popconfirm
            :title="`确认移除 ${row.username}？`"
            confirm-button-text="移除"
            cancel-button-text="取消"
            @confirm="removeMember(row)"
          >
            <template #reference>
              <el-button type="danger" link>移除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="添加项目成员" width="480px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="用户" prop="userId">
          <el-autocomplete
            v-model="userSearchKeyword"
            :fetch-suggestions="queryUserAsync"
            placeholder="按用户名 / 邮箱搜索"
            clearable
            style="width: 100%"
            value-key="value"
            @select="handleUserSelect"
            @input="handleKeywordChange"
          />
        </el-form-item>
        <el-form-item label="项目角色" prop="role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option
              v-for="opt in PROJECT_ROLES"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdd">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.member-manage-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__toolbar {
    display: flex;
    align-items: center;
    color: var(--el-text-color-secondary);
    gap: 12px;
  }

  &__spacer {
    flex: 1 1 auto;
  }
}
</style>
