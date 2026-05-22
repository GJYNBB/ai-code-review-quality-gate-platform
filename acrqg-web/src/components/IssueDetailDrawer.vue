<script setup lang="ts">
/**
 * IssueDetailDrawer（UI-008，B5-A.10）。
 *
 * 关联需求：R17.1 ~ R17.6。
 *
 * 入参：modelValue / issueId
 * 行为：
 * - 抽屉宽度 50%
 * - 顶部展示 filePath / lineNo / severity tag / source / status tag / ruleCode / message
 * - "切换状态" 区：状态 select（按当前 status 过滤合法目标，前端常量 ALLOWED_ISSUE_EDGES）
 *   + comment textarea；目标 ∈ {FALSE_POSITIVE, CLOSED} 时 comment.trim().length ≥ 5
 * - "添加评论" 区：textarea + 提交（issue.addComment）
 * - 评论时间线 / 历史时间线 倒序展示
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import * as issueApi from '@/api/issue'
import { ApiBusinessError } from '@/api/http'
import { formatDateTime } from '@/utils/format'
import {
  ALLOWED_ISSUE_EDGES,
  COMMENT_REQUIRED_TARGETS,
  ISSUE_STATUS_LABELS,
  ISSUE_STATUS_TAG_TYPE,
  SEVERITY_LABELS,
  SEVERITY_TAG_TYPE,
} from '@/constants/issueStateMachine'
import type { CodeIssueDTO, CodeIssueStatus } from '@/types/api'

const props = defineProps<{
  modelValue: boolean
  issueId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'changed'): void
}>()

const loading = ref(false)
const issue = ref<CodeIssueDTO | null>(null)
const submittingStatus = ref(false)
const submittingComment = ref(false)

// 状态切换表单
const targetStatus = ref<CodeIssueStatus | ''>('')
const statusComment = ref('')

// 添加评论表单
const newComment = ref('')

const allowedTargets = computed<CodeIssueStatus[]>(() => {
  if (!issue.value) return []
  return ALLOWED_ISSUE_EDGES[issue.value.status] ?? []
})

const commentRequired = computed(() =>
  targetStatus.value ? COMMENT_REQUIRED_TARGETS.has(targetStatus.value) : false,
)

const statusSubmitDisabled = computed(() => {
  if (!targetStatus.value) return true
  if (commentRequired.value && statusComment.value.trim().length < 5) return true
  return false
})

async function loadIssue() {
  if (!props.issueId) {
    issue.value = null
    return
  }
  loading.value = true
  targetStatus.value = ''
  statusComment.value = ''
  newComment.value = ''
  try {
    issue.value = await issueApi.get(props.issueId)
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.issueId] as const,
  ([visible, _id]) => {
    if (visible) void loadIssue()
  },
)

async function submitStatus() {
  if (!issue.value || !targetStatus.value) return
  submittingStatus.value = true
  try {
    await issueApi.changeStatus(issue.value.id, {
      status: targetStatus.value,
      comment: statusComment.value.trim() || undefined,
    })
    ElMessage.success('状态已更新')
    emit('changed')
    await loadIssue()
  } catch (err) {
    if (err instanceof ApiBusinessError) {
      // 常见：DEVELOPER 操作非自身任务返回 VALIDATION_ERROR；http 拦截器已弹错
      // 兜底 ElMessage 已存在 → 此处不再重复
    }
  } finally {
    submittingStatus.value = false
  }
}

async function submitComment() {
  if (!issue.value) return
  const content = newComment.value.trim()
  if (content.length === 0) {
    ElMessage.warning('请填写评论内容')
    return
  }
  submittingComment.value = true
  try {
    await issueApi.addComment(issue.value.id, content)
    ElMessage.success('评论已添加')
    newComment.value = ''
    await loadIssue()
  } finally {
    submittingComment.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

const sortedComments = computed(() => {
  const list = issue.value?.comments ?? []
  return [...list].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
})

const sortedHistory = computed(() => {
  const list = issue.value?.history ?? []
  return [...list].sort((a, b) => (a.changedAt < b.changedAt ? 1 : -1))
})
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :before-close="
      (done) => {
        close()
        done()
      }
    "
    direction="rtl"
    size="50%"
    :title="issue ? `问题 #${issue.id}` : '问题详情'"
  >
    <div v-loading="loading" class="issue-drawer">
      <el-empty v-if="!issue" description="未选择问题" />

      <template v-else>
        <el-card shadow="never" class="issue-drawer__head">
          <div class="issue-drawer__path mono">
            {{ issue.filePath }}<span v-if="issue.lineNo">:L{{ issue.lineNo }}</span>
          </div>
          <div class="issue-drawer__tags">
            <el-tag :type="SEVERITY_TAG_TYPE[issue.severity] ?? 'info'" size="small">
              {{ SEVERITY_LABELS[issue.severity] ?? issue.severity }}
            </el-tag>
            <el-tag size="small" type="info">{{ issue.source }}</el-tag>
            <el-tag :type="ISSUE_STATUS_TAG_TYPE[issue.status] ?? 'info'" size="small">
              {{ ISSUE_STATUS_LABELS[issue.status] ?? issue.status }}
            </el-tag>
            <el-tag size="small" type="info" effect="plain">{{ issue.ruleCode }}</el-tag>
          </div>
          <p class="issue-drawer__message">{{ issue.description }}</p>
          <p v-if="issue.suggestion" class="issue-drawer__suggestion">
            <strong>建议：</strong>{{ issue.suggestion }}
          </p>
        </el-card>

        <el-card shadow="never" class="issue-drawer__section">
          <template #header><span>切换状态</span></template>
          <el-form label-position="top">
            <el-form-item label="目标状态">
              <el-select
                v-model="targetStatus"
                placeholder="选择目标状态"
                style="width: 240px"
                :disabled="allowedTargets.length === 0"
              >
                <el-option
                  v-for="t in allowedTargets"
                  :key="t"
                  :label="ISSUE_STATUS_LABELS[t]"
                  :value="t"
                />
              </el-select>
              <span
                v-if="allowedTargets.length === 0"
                class="text-secondary"
                style="margin-left: 8px"
              >
                当前状态不可继续流转
              </span>
            </el-form-item>
            <el-form-item :label="commentRequired ? '评论（必填，至少 5 字）' : '评论（可选）'">
              <el-input
                v-model="statusComment"
                type="textarea"
                :rows="3"
                maxlength="1000"
                show-word-limit
                placeholder="说明本次状态变更原因"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :disabled="statusSubmitDisabled"
                :loading="submittingStatus"
                @click="submitStatus"
              >
                提交切换
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="issue-drawer__section">
          <template #header><span>添加评论</span></template>
          <el-form label-position="top">
            <el-form-item>
              <el-input
                v-model="newComment"
                type="textarea"
                :rows="3"
                maxlength="1000"
                show-word-limit
                placeholder="撰写评论..."
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submittingComment" @click="submitComment">
                提交评论
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="issue-drawer__section">
          <template #header
            ><span>评论时间线（{{ sortedComments.length }}）</span></template
          >
          <el-empty v-if="sortedComments.length === 0" description="暂无评论" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="c in sortedComments"
              :key="c.id"
              :timestamp="formatDateTime(c.createdAt)"
              placement="top"
            >
              <strong>{{ c.operatorName }}</strong>
              <p class="issue-drawer__comment">{{ c.content }}</p>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <el-card shadow="never" class="issue-drawer__section">
          <template #header
            ><span>状态历史（{{ sortedHistory.length }}）</span></template
          >
          <el-empty v-if="sortedHistory.length === 0" description="暂无记录" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="h in sortedHistory"
              :key="h.id"
              :timestamp="formatDateTime(h.changedAt)"
              placement="top"
            >
              <span>
                <el-tag size="small">{{ h.fromStatus }}</el-tag>
                <span class="arrow">→</span>
                <el-tag size="small" type="primary">{{ h.toStatus }}</el-tag>
              </span>
              <p class="issue-drawer__history-meta">
                操作人：{{ h.operatorName ?? `#${h.operatorId ?? '-'}` }}
              </p>
              <p v-if="h.comment" class="issue-drawer__comment">{{ h.comment }}</p>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </template>
    </div>
  </el-drawer>
</template>

<style lang="scss" scoped>
.issue-drawer {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__head {
    .issue-drawer__path {
      font-size: 14px;
      font-weight: 600;
      word-break: break-all;
    }

    .issue-drawer__tags {
      display: flex;
      gap: 8px;
      margin: 8px 0;
      flex-wrap: wrap;
    }

    .issue-drawer__message {
      margin: 8px 0 0;
      color: var(--el-text-color-primary);
      white-space: pre-wrap;
    }

    .issue-drawer__suggestion {
      margin: 8px 0 0;
      color: var(--el-text-color-secondary);
      white-space: pre-wrap;
    }
  }

  &__section {
    .issue-drawer__comment {
      margin: 8px 0 0;
      color: var(--el-text-color-primary);
      white-space: pre-wrap;
    }

    .issue-drawer__history-meta {
      margin: 4px 0 0;
      color: var(--el-text-color-secondary);
      font-size: 12px;
    }

    .arrow {
      margin: 0 8px;
      color: var(--el-text-color-secondary);
    }
  }
}

.mono {
  font-family: 'Source Code Pro', Consolas, monospace;
}

.text-secondary {
  color: var(--el-text-color-secondary);
}
</style>
