<script setup lang="ts">
/**
 * ReviewReportOverviewTab（B5-A.9）。
 *
 * 关联需求：R16.1 / R16.2 / R16.3。
 *
 * 职责：调 report.report，展示 status / score / aiAvailability / gateResultSummary
 *      （failed/passedRules 表格）+ issueCounts 饼图（按 severity 聚合）。
 *
 * 通过 expose 暴露 reload() 给父组件，便于豁免审批后刷新。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { use as useEcharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import VChart from 'vue-echarts'

import * as reportApi from '@/api/report'
import { formatDateTime } from '@/utils/format'
import {
  REVIEW_TASK_STATUS_LABELS,
  REVIEW_TASK_STATUS_TAG_TYPE,
} from '@/constants/reviewTaskStatus'
import { SEVERITY_LABELS } from '@/constants/issueStateMachine'
import type { ReviewReportDTO, ReviewTaskStatus, Severity } from '@/types/api'

useEcharts([CanvasRenderer, PieChart, TooltipComponent, LegendComponent, TitleComponent])

const props = defineProps<{ taskId: number }>()
const emit = defineEmits<{ (e: 'loaded', report: ReviewReportDTO): void }>()

const loading = ref(false)
const report = ref<ReviewReportDTO | null>(null)

async function load() {
  if (!Number.isFinite(props.taskId)) return
  loading.value = true
  try {
    report.value = await reportApi.report(props.taskId)
    if (report.value) emit('loaded', report.value)
  } finally {
    loading.value = false
  }
}

defineExpose({ reload: load, report })

onMounted(load)
watch(() => props.taskId, load)

const overview = computed(() => report.value?.taskOverview ?? null)

const severityAgg = computed(() => {
  const map = new Map<Severity, number>()
  for (const ic of report.value?.issueCounts ?? []) {
    const sev = ic.severity as Severity
    map.set(sev, (map.get(sev) ?? 0) + ic.count)
  }
  return Array.from(map.entries()).map(([sev, count]) => ({
    name: SEVERITY_LABELS[sev] ?? sev,
    value: count,
  }))
})

const totalIssues = computed(() => severityAgg.value.reduce((s, x) => s + x.value, 0))

const SEVERITY_COLORS: Record<string, string> = {
  严重: '#f56c6c',
  高: '#e6a23c',
  中: '#409eff',
  低: '#909399',
  提示: '#67c23a',
}

const pieOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { orient: 'vertical', left: 'left' },
  series: [
    {
      name: '问题分布',
      type: 'pie',
      radius: ['45%', '70%'],
      data: severityAgg.value,
      label: { formatter: '{b}: {c} ({d}%)' },
      color: severityAgg.value.map((x) => SEVERITY_COLORS[x.name] ?? '#909399'),
    },
  ],
}))
</script>

<template>
  <div v-loading="loading" class="overview-tab">
    <el-empty v-if="!report" description="暂无报告" />

    <template v-else>
      <el-row :gutter="16">
        <el-col :span="16">
          <el-descriptions :column="2" border title="任务概览">
            <el-descriptions-item label="任务编号">
              {{ overview?.taskNo }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag
                v-if="overview?.status"
                :type="REVIEW_TASK_STATUS_TAG_TYPE[overview.status as ReviewTaskStatus] ?? 'info'"
                size="small"
              >
                {{
                  REVIEW_TASK_STATUS_LABELS[overview.status as ReviewTaskStatus] ?? overview.status
                }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="得分">
              {{ overview?.score != null ? Number(overview.score).toFixed(1) : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="AI 可用">
              <el-tag :type="report.aiAvailability ? 'success' : 'warning'" size="small">
                {{ report.aiAvailability ? '可用' : '降级' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="源分支">{{ overview?.sourceBranch }}</el-descriptions-item>
            <el-descriptions-item label="目标分支">{{
              overview?.targetBranch
            }}</el-descriptions-item>
            <el-descriptions-item label="commit">
              <span class="mono">{{ overview?.commitSha?.slice(0, 12) ?? '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="PR ID">{{ overview?.prId ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{
              formatDateTime(overview?.createdAt)
            }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{
              formatDateTime(overview?.finishedAt)
            }}</el-descriptions-item>
            <el-descriptions-item label="耗时(秒)" :span="2">
              {{ overview?.durationSeconds ?? '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-col>

        <el-col :span="8">
          <el-card shadow="never" class="overview-tab__pie">
            <template #header>
              <span>问题分布（共 {{ totalIssues }} 条）</span>
            </template>
            <VChart
              v-if="severityAgg.length > 0"
              :option="pieOption"
              autoresize
              style="height: 260px"
            />
            <el-empty v-else description="暂无问题" />
          </el-card>
        </el-col>
      </el-row>

      <el-card v-if="report.gateResultSummary" shadow="never" class="overview-tab__gate">
        <template #header>
          <span>
            门禁判定：
            <el-tag
              :type="
                report.gateResultSummary.status === 'PASSED'
                  ? 'success'
                  : report.gateResultSummary.status === 'WAIVED'
                    ? 'warning'
                    : 'danger'
              "
              size="small"
            >
              {{ report.gateResultSummary.status }}
            </el-tag>
          </span>
        </template>

        <h4 class="overview-tab__rules-title">
          未通过规则（{{ report.gateResultSummary.failedRules.length }}）
        </h4>
        <el-table :data="report.gateResultSummary.failedRules" stripe empty-text="无">
          <el-table-column prop="metric" label="指标" min-width="160" />
          <el-table-column prop="operator" label="比较" width="80" />
          <el-table-column prop="threshold" label="阈值" width="100" />
          <el-table-column prop="actual" label="实际" width="100" />
          <el-table-column label="严重度" width="100">
            <template #default="{ row }">
              <el-tag :type="row.severity === 'BLOCKER' ? 'danger' : 'warning'" size="small">
                {{ row.severity }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.passed ? 'success' : 'danger'" size="small">
                {{ row.passed ? 'PASS' : 'FAIL' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <h4 class="overview-tab__rules-title overview-tab__rules-title--passed">
          通过规则（{{ report.gateResultSummary.passedRules.length }}）
        </h4>
        <el-table :data="report.gateResultSummary.passedRules" stripe empty-text="无">
          <el-table-column prop="metric" label="指标" min-width="160" />
          <el-table-column prop="operator" label="比较" width="80" />
          <el-table-column prop="threshold" label="阈值" width="100" />
          <el-table-column prop="actual" label="实际" width="100" />
          <el-table-column label="严重度" width="100">
            <template #default="{ row }">
              <el-tag :type="row.severity === 'BLOCKER' ? 'danger' : 'warning'" size="small">
                {{ row.severity }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.passed ? 'success' : 'danger'" size="small">
                {{ row.passed ? 'PASS' : 'FAIL' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <slot name="extra" :report="report" />
    </template>
  </div>
</template>

<style lang="scss" scoped>
.overview-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__pie {
    height: 100%;
  }

  &__gate {
    margin-top: 8px;
  }

  &__rules-title {
    margin: 16px 0 8px;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-color-danger);

    &--passed {
      color: var(--el-color-success);
    }
  }
}

.mono {
  font-family: 'Source Code Pro', Consolas, monospace;
}
</style>
