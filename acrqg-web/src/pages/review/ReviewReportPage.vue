<script setup lang="ts">
/**
 * UI-007 评审报告页（B5-A.9）。
 *
 * 关联需求：R9.4 / R9.6 / R15 / R16 / R17。
 *
 * 顶部：taskNo / status tag / score / 操作按钮组
 *   - 重试：PROJECT_ADMIN / REVIEWER / SYSTEM_ADMIN 可见 + status ∈ {PASSED, FAILED_GATE, EXECUTION_FAILED}
 *   - 取消：PROJECT_ADMIN / SYSTEM_ADMIN 可见 + status === PENDING
 *   - 申请豁免：项目成员可见 + status === FAILED_GATE
 *
 * el-tabs 4 个 Tab：Overview / Issues / Diff / Logs
 *
 * B5-A.13 会在此基础上注入豁免列表与审批入口。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowLeft, RefreshLeft, CircleClose, ChatLineSquare } from '@element-plus/icons-vue';
import dayjs from 'dayjs';

import * as reviewTaskApi from '@/api/reviewTask';
import * as gateWaiverApi from '@/api/gateWaiver';
import { ApiBusinessError } from '@/api/http';
import { useAuthStore } from '@/stores/auth';
import { hasAnyRole } from '@/utils/permission';
import {
  REVIEW_TASK_STATUS_LABELS,
  REVIEW_TASK_STATUS_TAG_TYPE,
} from '@/constants/reviewTaskStatus';
import type { ReviewTaskDTO, ReviewTaskStatus } from '@/types/api';

import ReviewReportOverviewTab from '@/pages/review/ReviewReportOverviewTab.vue';
import ReviewReportIssuesTab from '@/pages/review/ReviewReportIssuesTab.vue';
import ReviewReportDiffTab from '@/pages/review/ReviewReportDiffTab.vue';
import ReviewReportLogsTab from '@/pages/review/ReviewReportLogsTab.vue';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const taskId = computed(() => Number(route.params.taskId));
const task = ref<ReviewTaskDTO | null>(null);
const taskLoading = ref(false);
const activeTab = ref<'overview' | 'issues' | 'diff' | 'logs'>('overview');

const overviewRef = ref<{ reload: () => Promise<void> } | null>(null);

const status = computed<ReviewTaskStatus | null>(() =>
  task.value ? (task.value.status as ReviewTaskStatus) : null,
);

const canRetry = computed(() => {
  if (!status.value) return false;
  return (
    hasAnyRole(authStore.roles, ['PROJECT_ADMIN', 'REVIEWER', 'SYSTEM_ADMIN']) &&
    ['PASSED', 'FAILED_GATE', 'EXECUTION_FAILED'].includes(status.value)
  );
});

const canCancel = computed(() => {
  if (!status.value) return false;
  return hasAnyRole(authStore.roles, ['PROJECT_ADMIN', 'SYSTEM_ADMIN']) && status.value === 'PENDING';
});

const canApplyWaiver = computed(() => {
  if (!status.value) return false;
  return (
    hasAnyRole(authStore.roles, ['DEVELOPER', 'REVIEWER', 'PROJECT_ADMIN', 'SYSTEM_ADMIN']) &&
    status.value === 'FAILED_GATE'
  );
});

async function loadTask() {
  if (!Number.isFinite(taskId.value)) return;
  taskLoading.value = true;
  try {
    task.value = await reviewTaskApi.get(taskId.value);
  } finally {
    taskLoading.value = false;
  }
}

function goBack() {
  router.push('/review-tasks');
}

async function handleRetry() {
  if (!task.value) return;
  let reason = '';
  try {
    const ans = (await ElMessageBox.prompt('请输入重试原因（可选）', '重试任务', {
      confirmButtonText: '确认重试',
      cancelButtonText: '取消',
      inputType: 'textarea',
    })) as { value: string };
    reason = ans.value ?? '';
  } catch {
    return;
  }
  try {
    await reviewTaskApi.retry(task.value.id, { reason: reason.trim() || undefined });
    ElMessage.success('已发起重试');
    await loadTask();
  } catch (err) {
    if (err instanceof ApiBusinessError) {
      // 拦截器已弹错
    }
  }
}

async function handleCancel() {
  if (!task.value) return;
  let reason = '';
  try {
    const ans = (await ElMessageBox.prompt('请输入取消原因', '取消任务', {
      confirmButtonText: '确认取消',
      cancelButtonText: '关闭',
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim().length > 0 ? true : '取消原因不能为空'),
    })) as { value: string };
    reason = ans.value;
  } catch {
    return;
  }
  try {
    await reviewTaskApi.cancel(task.value.id, { reason: reason.trim() });
    ElMessage.success('任务已取消');
    await loadTask();
  } catch (err) {
    if (err instanceof ApiBusinessError) {
      // 拦截器已弹错
    }
  }
}

// ---- 申请豁免 ----
const waiverDialogVisible = ref(false);
const waiverReason = ref('');
const waiverExpireAt = ref<string>('');
const waiverSubmitting = ref(false);

const waiverFormValid = computed(() => {
  if (waiverReason.value.trim().length < 10) return false;
  if (!waiverExpireAt.value) return false;
  if (dayjs(waiverExpireAt.value).isBefore(dayjs())) return false;
  return true;
});

function openWaiverDialog() {
  waiverReason.value = '';
  waiverExpireAt.value = '';
  waiverDialogVisible.value = true;
}

async function submitWaiver() {
  if (!task.value || !waiverFormValid.value) return;
  waiverSubmitting.value = true;
  try {
    await gateWaiverApi.apply(task.value.id, {
      reason: waiverReason.value.trim(),
      // 后端 DTO 当前仅含 reason；expireAt 为前端校验保留字段
    });
    ElMessage.success('豁免申请已提交');
    waiverDialogVisible.value = false;
    // B5-A.13 接入豁免列表后会调用 reloadWaivers
  } catch (err) {
    if (err instanceof ApiBusinessError) {
      // 拦截器已弹错
    }
  } finally {
    waiverSubmitting.value = false;
  }
}

onMounted(async () => {
  await loadTask();
});

watch(taskId, async () => {
  await loadTask();
});
</script>

<template>
  <div v-loading="taskLoading" class="review-report-page">
    <el-card shadow="never" class="review-report-page__header">
      <div class="header-row">
        <el-button :icon="ArrowLeft" link @click="goBack">返回列表</el-button>
        <span class="header-row__taskno">{{ task?.taskNo ?? '加载中...' }}</span>
        <el-tag v-if="status" :type="REVIEW_TASK_STATUS_TAG_TYPE[status] ?? 'info'" size="small">
          {{ REVIEW_TASK_STATUS_LABELS[status] ?? status }}
        </el-tag>
        <span v-if="task?.score != null" class="header-row__score">
          得分 {{ Number(task.score).toFixed(1) }}
        </span>

        <div class="header-row__spacer" />

        <el-button v-if="canRetry" type="primary" :icon="RefreshLeft" @click="handleRetry">
          重试
        </el-button>
        <el-button v-if="canCancel" type="danger" :icon="CircleClose" @click="handleCancel">
          取消
        </el-button>
        <el-button v-if="canApplyWaiver" type="warning" :icon="ChatLineSquare" @click="openWaiverDialog">
          申请豁免
        </el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="review-report-page__tabs">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="概览" name="overview">
          <ReviewReportOverviewTab ref="overviewRef" :task-id="taskId" />
        </el-tab-pane>

        <el-tab-pane label="问题" name="issues" lazy>
          <ReviewReportIssuesTab :task-id="taskId" />
        </el-tab-pane>

        <el-tab-pane label="代码差异" name="diff" lazy>
          <ReviewReportDiffTab :task-id="taskId" />
        </el-tab-pane>

        <el-tab-pane label="执行日志" name="logs" lazy>
          <ReviewReportLogsTab :task-id="taskId" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 申请豁免对话框 -->
    <el-dialog
      v-model="waiverDialogVisible"
      title="申请门禁豁免"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="申请理由（≥ 10 字符）" required>
          <el-input
            v-model="waiverReason"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请详细说明本次豁免理由"
          />
        </el-form-item>
        <el-form-item label="豁免有效期（必须为未来时间）" required>
          <el-date-picker
            v-model="waiverExpireAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择截止时间"
            style="width: 100%"
            :disabled-date="(d: Date) => dayjs(d).isBefore(dayjs(), 'day')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="waiverDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!waiverFormValid"
          :loading="waiverSubmitting"
          @click="submitWaiver"
        >
          提交申请
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.review-report-page {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .header-row {
    display: flex;
    align-items: center;
    gap: 12px;

    &__taskno {
      font-weight: 600;
      font-size: 16px;
    }

    &__score {
      color: var(--el-text-color-secondary);
      font-size: 13px;
    }

    &__spacer {
      flex: 1 1 auto;
    }
  }
}
</style>
