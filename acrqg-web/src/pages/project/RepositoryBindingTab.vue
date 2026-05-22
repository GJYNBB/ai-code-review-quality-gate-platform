<script setup lang="ts">
/**
 * UI-005 仓库绑定（B5-A.5）。
 *
 * 关联需求：R5（仓库绑定）。
 *
 * 功能：
 * - 表单：provider / repoUrl / accessToken（密码框）/ webhookSecret
 * - 「测试连通性」按钮：调 repository.test，提示 reachable + message
 * - 「保存绑定」按钮：调 repository.bind，成功后展示 webhookUrl + 复制按钮
 * - 已有绑定时只读显示，提供「修改」切换为可编辑
 */
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { CopyDocument, Edit, Refresh } from '@element-plus/icons-vue'

import * as repositoryApi from '@/api/repository'
import { ApiBusinessError } from '@/api/http'
import type {
  Provider,
  RepositoryBindRequest,
  RepositoryBindingDTO,
} from '@/types/api'

const props = defineProps<{
  projectId: number
}>()

const PROVIDER_OPTIONS: Array<{ label: string; value: Provider }> = [
  { label: 'GitHub', value: 'GITHUB' },
  { label: 'GitLab', value: 'GITLAB' },
  { label: 'Gitee', value: 'GITEE' },
]

const loading = ref(false)
const binding = ref<RepositoryBindingDTO | null>(null)
const editMode = ref(false)
const submitting = ref(false)
const testing = ref(false)
const formRef = ref<FormInstance | null>(null)

const form = reactive<RepositoryBindRequest>({
  provider: 'GITHUB',
  repoUrl: '',
  accessToken: '',
  webhookSecret: '',
})

const rules: FormRules = {
  provider: [{ required: true, message: '请选择平台', trigger: 'change' }],
  repoUrl: [
    { required: true, message: '请输入仓库地址', trigger: 'blur' },
    {
      pattern: /^https?:\/\/.+/,
      message: '请输入合法的 http(s) 地址',
      trigger: 'blur',
    },
  ],
  accessToken: [
    { required: true, message: '请输入访问令牌', trigger: 'blur' },
    { min: 8, max: 512, message: '令牌长度需在 8-512 之间', trigger: 'blur' },
  ],
  webhookSecret: [
    { required: true, message: '请输入 Webhook Secret', trigger: 'blur' },
    { min: 8, max: 256, message: '长度需在 8-256 之间', trigger: 'blur' },
  ],
}

async function loadBinding() {
  if (!Number.isFinite(props.projectId)) return
  loading.value = true
  try {
    binding.value = await repositoryApi.get(props.projectId)
    if (binding.value) {
      // 已绑定 → 只读模式；编辑时回填基础字段（access token 不回填）
      editMode.value = false
      form.provider = (binding.value.provider as Provider) || 'GITHUB'
      form.repoUrl = binding.value.repoUrl
      form.accessToken = ''
      form.webhookSecret = ''
    } else {
      editMode.value = true
    }
  } finally {
    loading.value = false
  }
}

async function handleTest() {
  if (!formRef.value) return
  try {
    await formRef.value.validateField(['provider', 'repoUrl', 'accessToken'])
  } catch {
    return
  }
  testing.value = true
  try {
    const result = await repositoryApi.test(props.projectId, {
      provider: form.provider,
      repoUrl: form.repoUrl.trim(),
      accessToken: form.accessToken,
    })
    if (result.reachable) {
      ElMessage.success(result.message || '连通性测试成功')
    } else {
      ElMessage.warning(result.message || '仓库不可访问')
    }
  } catch (err) {
    if (err instanceof ApiBusinessError) {
      ElMessage.error(err.message)
    }
  } finally {
    testing.value = false
  }
}

async function handleSave() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const saved = await repositoryApi.bind(props.projectId, {
      provider: form.provider,
      repoUrl: form.repoUrl.trim(),
      accessToken: form.accessToken,
      webhookSecret: form.webhookSecret,
    })
    binding.value = saved
    editMode.value = false
    ElMessage.success('仓库绑定已保存')
  } finally {
    submitting.value = false
  }
}

function switchToEdit() {
  editMode.value = true
  // 已有绑定且回到编辑：保留 provider/repoUrl，敏感字段必填重输
  form.accessToken = ''
  form.webhookSecret = ''
}

function cancelEdit() {
  editMode.value = false
  void loadBinding()
}

async function copyWebhookUrl() {
  if (!binding.value?.webhookUrl) return
  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(binding.value.webhookUrl)
    } else {
      // 降级：使用临时 textarea
      const ta = document.createElement('textarea')
      ta.value = binding.value.webhookUrl
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ElMessage.success('Webhook URL 已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选取复制')
  }
}

onMounted(() => {
  void loadBinding()
})

watch(
  () => props.projectId,
  () => {
    void loadBinding()
  },
)
</script>

<template>
  <div class="repository-binding-tab" v-loading="loading">
    <!-- 已绑定 + 只读：展示绑定信息与 webhookUrl -->
    <template v-if="binding && !editMode">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="平台">{{ binding.provider }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="binding.status === 'ACTIVE'" type="success" size="small">已激活</el-tag>
          <el-tag v-else type="info" size="small">{{ binding.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="仓库地址" :span="2">
          <el-link :href="binding.repoUrl" target="_blank" :underline="false">
            {{ binding.repoUrl }}
          </el-link>
        </el-descriptions-item>
        <el-descriptions-item label="Webhook URL" :span="2">
          <div class="webhook-url-row">
            <code class="webhook-url-row__url">{{ binding.webhookUrl }}</code>
            <el-button :icon="CopyDocument" size="small" @click="copyWebhookUrl">复制</el-button>
          </div>
        </el-descriptions-item>
      </el-descriptions>

      <div class="repository-binding-tab__actions">
        <el-button :icon="Edit" type="primary" @click="switchToEdit">修改</el-button>
        <el-button :icon="Refresh" @click="loadBinding">刷新</el-button>
      </div>
    </template>

    <!-- 编辑表单 -->
    <el-form
      v-else
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="repository-binding-tab__form"
    >
      <el-form-item label="平台" prop="provider">
        <el-radio-group v-model="form.provider">
          <el-radio-button v-for="opt in PROVIDER_OPTIONS" :key="opt.value" :label="opt.value">
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="仓库地址" prop="repoUrl">
        <el-input v-model="form.repoUrl" placeholder="如 https://github.com/your-org/your-repo" />
      </el-form-item>
      <el-form-item label="访问令牌" prop="accessToken">
        <el-input
          v-model="form.accessToken"
          type="password"
          placeholder="个人访问令牌（PAT）"
          show-password
          autocomplete="new-password"
        />
      </el-form-item>
      <el-form-item label="Webhook Secret" prop="webhookSecret">
        <el-input
          v-model="form.webhookSecret"
          type="password"
          placeholder="用于签名校验"
          show-password
          autocomplete="new-password"
        />
      </el-form-item>

      <div class="repository-binding-tab__actions">
        <el-button :loading="testing" @click="handleTest">测试连通性</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">保存绑定</el-button>
        <el-button v-if="binding" @click="cancelEdit">取消</el-button>
      </div>
    </el-form>
  </div>
</template>

<style lang="scss" scoped>
.repository-binding-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__form {
    max-width: 640px;
  }

  &__actions {
    display: flex;
    gap: 12px;
  }
}

.webhook-url-row {
  display: flex;
  align-items: center;
  gap: 8px;

  &__url {
    flex: 1;
    background: var(--el-fill-color-light);
    padding: 6px 10px;
    border-radius: 4px;
    font-family: 'Source Code Pro', Consolas, monospace;
    font-size: 13px;
    word-break: break-all;
  }
}
</style>
