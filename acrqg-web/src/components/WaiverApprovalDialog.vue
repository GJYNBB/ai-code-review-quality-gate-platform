<script setup lang="ts">
/**
 * WaiverApprovalDialog（B5-A.13）。
 *
 * 关联需求：R15.4 / R15.5。
 *
 * 入参：modelValue / waiver: GateWaiverDTO | null
 * 行为：
 * - 展示申请理由 / 申请人 / 申请时间
 * - 选择 approve(true) / reject(false) + 评论 → 调 gateWaiver.approve 或 reject
 * - 成功后 emit('done')；调用方负责刷新列表
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import * as gateWaiverApi from '@/api/gateWaiver'
import { ApiBusinessError } from '@/api/http'
import { formatDateTime } from '@/utils/format'
import type { GateWaiverDTO } from '@/types/api'

const props = defineProps<{
  modelValue: boolean
  waiver: GateWaiverDTO | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'done'): void
}>()

const decision = ref<'APPROVE' | 'REJECT'>('APPROVE')
const comment = ref('')
const submitting = ref(false)

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      decision.value = 'APPROVE'
      comment.value = ''
    }
  },
)

async function submit() {
  if (!props.waiver) return
  submitting.value = true
  try {
    if (decision.value === 'APPROVE') {
      await gateWaiverApi.approve(props.waiver.id, {
        comment: comment.value.trim() || undefined,
      })
      ElMessage.success('已批准豁免申请')
    } else {
      await gateWaiverApi.reject(props.waiver.id, {
        comment: comment.value.trim() || undefined,
      })
      ElMessage.success('已拒绝豁免申请')
    }
    emit('done')
    emit('update:modelValue', false)
  } catch (err) {
    if (err instanceof ApiBusinessError) {
      // 拦截器已弹错
    }
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="审批豁免申请"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <template v-if="waiver">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="申请人">#{{ waiver.applicantId }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{
          formatDateTime(waiver.createdAt)
        }}</el-descriptions-item>
        <el-descriptions-item label="申请理由">
          <span style="white-space: pre-wrap">{{ waiver.reason }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-position="top" style="margin-top: 16px">
        <el-form-item label="审批结果">
          <el-radio-group v-model="decision">
            <el-radio value="APPROVE">批准</el-radio>
            <el-radio value="REJECT">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input
            v-model="comment"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="可选，建议填写以便申请人复盘"
          />
        </el-form-item>
      </el-form>
    </template>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button
        :type="decision === 'APPROVE' ? 'primary' : 'danger'"
        :loading="submitting"
        :disabled="!waiver"
        @click="submit"
      >
        提交{{ decision === 'APPROVE' ? '批准' : '拒绝' }}
      </el-button>
    </template>
  </el-dialog>
</template>
