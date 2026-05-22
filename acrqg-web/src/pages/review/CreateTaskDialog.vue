<script setup lang="ts">
/**
 * CreateTaskDialog（B5-A.8）。
 *
 * 关联需求：R8.1 / R8.2 / R8.4。
 *
 * 字段：projectId / sourceBranch / targetBranch / commitSha / prId
 *  - commitSha 与 prId 至少填一项，否则前端校验失败
 *  - 提交时调 reviewTask.create，并在请求头携带 Idempotency-Key（前端 UUID 生成）
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import * as reviewTaskApi from '@/api/reviewTask'
import { ApiBusinessError } from '@/api/http'
import { useProjectStore } from '@/stores/project'
import { uuidv4 } from '@/utils/uuid'
import type { ReviewTaskCreateRequest, TriggerType } from '@/types/api'

const props = defineProps<{
  modelValue: boolean
  /** 默认项目 id（一般来自路由 / 当前项目上下文） */
  defaultProjectId?: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'created'): void
}>()

const projectStore = useProjectStore()

const formRef = ref<FormInstance | null>(null)
const submitting = ref(false)

const form = reactive<ReviewTaskCreateRequest>({
  projectId: 0,
  sourceBranch: '',
  targetBranch: '',
  commitSha: '',
  prId: '',
  triggerType: 'MANUAL' as TriggerType,
})

function resetForm() {
  form.projectId = props.defaultProjectId ?? projectStore.currentProjectId ?? 0
  form.sourceBranch = ''
  form.targetBranch = ''
  form.commitSha = ''
  form.prId = ''
  form.triggerType = 'MANUAL'
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      // 每次打开时重置；保留 projectId 默认
      resetForm()
      // 确保项目下拉有数据
      void projectStore.loadAll()
    }
  },
)

const rules: FormRules = {
  projectId: [
    {
      required: true,
      validator: (_r, value, cb) => {
        if (!value || Number(value) <= 0) cb(new Error('请选择项目'))
        else cb()
      },
      trigger: 'change',
    },
  ],
  sourceBranch: [
    { required: true, message: '请输入源分支', trigger: 'blur' },
    { max: 128, message: '长度不超过 128', trigger: 'blur' },
  ],
  targetBranch: [
    { required: true, message: '请输入目标分支', trigger: 'blur' },
    { max: 128, message: '长度不超过 128', trigger: 'blur' },
  ],
  triggerType: [{ required: true, message: '请选择触发类型', trigger: 'change' }],
  // commitSha / prId 至少一项：通过 validateRefs 在提交时统一校验
}

function close() {
  emit('update:modelValue', false)
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const commitSha = (form.commitSha ?? '').trim()
  const prId = (form.prId ?? '').trim()
  if (!commitSha && !prId) {
    ElMessage.warning('commitSha 与 prId 至少需要填写一项')
    return
  }

  submitting.value = true
  try {
    const idempotencyKey = uuidv4()
    await reviewTaskApi.create(
      {
        projectId: Number(form.projectId),
        sourceBranch: form.sourceBranch.trim(),
        targetBranch: form.targetBranch.trim(),
        commitSha: commitSha || undefined,
        prId: prId || undefined,
        triggerType: form.triggerType,
      },
      idempotencyKey,
    )
    ElMessage.success('任务已创建')
    emit('created')
    close()
  } catch (err) {
    if (err instanceof ApiBusinessError) {
      // 全局拦截器已弹提示；此处保留 catch 防 unhandled
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="新建评审任务"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="项目" prop="projectId">
        <el-select v-model="form.projectId" placeholder="请选择项目" filterable style="width: 100%">
          <el-option v-for="p in projectStore.projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="源分支 sourceBranch" prop="sourceBranch">
        <el-input v-model="form.sourceBranch" placeholder="如 feat/foo" maxlength="128" />
      </el-form-item>

      <el-form-item label="目标分支 targetBranch" prop="targetBranch">
        <el-input v-model="form.targetBranch" placeholder="如 main" maxlength="128" />
      </el-form-item>

      <el-form-item label="commitSha">
        <el-input
          v-model="form.commitSha"
          placeholder="40 位 sha；与 PR ID 至少填一项"
          maxlength="64"
        />
      </el-form-item>

      <el-form-item label="PR ID">
        <el-input v-model="form.prId" placeholder="如 42；与 commitSha 至少填一项" maxlength="64" />
      </el-form-item>

      <el-form-item label="触发类型 triggerType" prop="triggerType">
        <el-select v-model="form.triggerType" style="width: 100%">
          <el-option label="手动 (MANUAL)" value="MANUAL" />
          <el-option label="Webhook" value="WEBHOOK" />
          <el-option label="CI/CD" value="CI_CD" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">创建</el-button>
    </template>
  </el-dialog>
</template>
