<script setup lang="ts">
/**
 * UI-002 工作台首页（B5-A.3）。
 *
 * 关联需求：R18（项目质量看板）。
 *
 * 上半部分：
 *   - 项目选择器（基于 projectStore，与全局头部联动）
 *   - 4 张统计卡：任务数 / 通过率 / 平均分 / 平均耗时
 *   - 折线图（vue-echarts）：通过率 + 平均分双轴
 * 下半部分：
 *   - 高风险文件 Top 10 表格
 */
import { computed, ref, watch, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import dayjs from 'dayjs'
import { use as useEcharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
} from 'echarts/components'
import VChart from 'vue-echarts'

import { useProjectStore } from '@/stores/project'
import { trend as fetchTrend, riskFiles as fetchRiskFiles } from '@/api/dashboard'
import type { QualityTrendDTO, RiskFileDTO, TrendPointDTO } from '@/types/api'

useEcharts([
  CanvasRenderer,
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
])

const projectStore = useProjectStore()
const { currentProjectId, projects } = storeToRefs(projectStore)

const trendData = ref<QualityTrendDTO | null>(null)
const riskFilesData = ref<RiskFileDTO[]>([])
const loadingTrend = ref(false)
const loadingRisk = ref(false)

// 默认时间范围：最近 30 天
const dateRange = ref<[string, string]>([
  dayjs().subtract(29, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
])

function toNumber(v: number | string | null | undefined): number {
  if (v == null) return 0
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : 0
}

const stats = computed(() => {
  const points: TrendPointDTO[] = trendData.value?.points ?? []
  const totals = trendData.value?.totals
  const taskCount = totals?.totalTasks ?? points.reduce((sum, p) => sum + (p.taskCount ?? 0), 0)
  const passRate = totals?.overallPassRate != null ? toNumber(totals.overallPassRate) : computeAvgPassRate(points)
  const avgScore = totals?.overallAvgScore != null ? toNumber(totals.overallAvgScore) : computeAvg(points, 'avgScore')
  const avgDuration = computeAvg(points, 'avgDurationSeconds')
  return {
    taskCount,
    passRate,
    avgScore,
    avgDurationSeconds: avgDuration,
  }
})

function computeAvg(points: TrendPointDTO[], field: 'avgScore' | 'avgDurationSeconds'): number {
  const values = points.map((p) => toNumber(p[field])).filter((v) => v > 0)
  if (values.length === 0) return 0
  return values.reduce((a, b) => a + b, 0) / values.length
}

function computeAvgPassRate(points: TrendPointDTO[]): number {
  const totalTasks = points.reduce((s, p) => s + (p.taskCount ?? 0), 0)
  const totalPass = points.reduce((s, p) => s + (p.passCount ?? 0), 0)
  return totalTasks > 0 ? totalPass / totalTasks : 0
}

function formatPercent(v: number): string {
  return `${(v * 100).toFixed(1)}%`
}

function formatScore(v: number): string {
  return v ? v.toFixed(1) : '-'
}

function formatDurationHuman(seconds: number): string {
  if (!seconds || seconds <= 0) return '-'
  if (seconds < 60) return `${seconds.toFixed(0)} 秒`
  const m = seconds / 60
  if (m < 60) return `${m.toFixed(1)} 分钟`
  return `${(m / 60).toFixed(1)} 小时`
}

const chartOption = computed(() => {
  const points = trendData.value?.points ?? []
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['通过率', '平均分'], top: 8 },
    grid: { left: 50, right: 60, bottom: 32, top: 48 },
    xAxis: {
      type: 'category',
      data: points.map((p) => p.date),
      boundaryGap: false,
    },
    yAxis: [
      {
        name: '通过率',
        type: 'value',
        min: 0,
        max: 1,
        axisLabel: { formatter: (v: number) => `${(v * 100).toFixed(0)}%` },
      },
      {
        name: '平均分',
        type: 'value',
        min: 0,
        max: 100,
      },
    ],
    series: [
      {
        name: '通过率',
        type: 'line',
        smooth: true,
        data: points.map((p) => toNumber(p.passRate)),
        yAxisIndex: 0,
      },
      {
        name: '平均分',
        type: 'line',
        smooth: true,
        data: points.map((p) => toNumber(p.avgScore)),
        yAxisIndex: 1,
      },
    ],
  }
})

async function loadDashboard() {
  const projectId = currentProjectId.value
  if (projectId == null) {
    trendData.value = null
    riskFilesData.value = []
    return
  }

  const [startDate, endDate] = dateRange.value

  loadingTrend.value = true
  loadingRisk.value = true
  try {
    const [trend, risk] = await Promise.all([
      fetchTrend(projectId, { startDate, endDate }).catch(() => null),
      fetchRiskFiles(projectId, { limit: 10 }).catch(() => [] as RiskFileDTO[]),
    ])
    trendData.value = trend
    riskFilesData.value = risk ?? []
  } finally {
    loadingTrend.value = false
    loadingRisk.value = false
  }
}

onMounted(async () => {
  await projectStore.loadAll()
  if (currentProjectId.value == null && projects.value.length > 0) {
    projectStore.setCurrentProject(projects.value[0].id)
  }
  await loadDashboard()
})

watch([currentProjectId, dateRange], () => {
  void loadDashboard()
})

function handleProjectChange(id: number | null) {
  projectStore.setCurrentProject(id)
}
</script>

<template>
  <div class="dashboard-page">
    <el-card shadow="never" class="dashboard-page__filters">
      <div class="filters-row">
        <span class="filters-row__label">项目</span>
        <el-select
          :model-value="currentProjectId ?? undefined"
          placeholder="请选择项目"
          filterable
          clearable
          style="width: 240px"
          @change="handleProjectChange"
        >
          <el-option
            v-for="p in projects"
            :key="p.id"
            :label="p.name"
            :value="p.id"
          />
        </el-select>
        <span class="filters-row__label">时间范围</span>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="—"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
      </div>
    </el-card>

    <div v-if="currentProjectId == null" class="dashboard-page__empty">
      <el-empty description="请先选择项目" />
    </div>

    <template v-else>
      <el-row :gutter="16" class="dashboard-page__stats">
        <el-col :span="6">
          <el-card shadow="never">
            <div class="stat-card">
              <span class="stat-card__title">任务数</span>
              <span class="stat-card__value">{{ stats.taskCount }}</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="stat-card">
              <span class="stat-card__title">通过率</span>
              <span class="stat-card__value">{{ formatPercent(stats.passRate) }}</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="stat-card">
              <span class="stat-card__title">平均分</span>
              <span class="stat-card__value">{{ formatScore(stats.avgScore) }}</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="stat-card">
              <span class="stat-card__title">平均耗时</span>
              <span class="stat-card__value">{{ formatDurationHuman(stats.avgDurationSeconds) }}</span>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" class="dashboard-page__chart" v-loading="loadingTrend">
        <template #header>
          <span>质量趋势</span>
        </template>
        <div v-if="(trendData?.points?.length ?? 0) === 0" class="dashboard-page__empty">
          <el-empty description="所选区间暂无数据" />
        </div>
        <VChart v-else :option="chartOption" autoresize style="height: 320px" />
      </el-card>

      <el-card shadow="never" class="dashboard-page__risk" v-loading="loadingRisk">
        <template #header>
          <span>高风险文件 Top 10</span>
        </template>
        <el-table :data="riskFilesData" stripe>
          <el-table-column prop="filePath" label="文件" min-width="280" show-overflow-tooltip />
          <el-table-column prop="issueCount" label="问题数" width="120" align="right" />
          <el-table-column prop="criticalCount" label="严重" width="100" align="right" />
          <el-table-column prop="highCount" label="高" width="100" align="right" />
          <el-table-column label="加权评分" width="140" align="right">
            <template #default="{ row }">
              {{ Number(row.weightedScore).toFixed(2) }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__filters {
    .filters-row {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;

      &__label {
        color: var(--el-text-color-secondary);
        font-size: 13px;
      }
    }
  }

  &__empty {
    padding: 24px 0;
    text-align: center;
  }
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 4px;

  &__title {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  &__value {
    font-size: 24px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
}
</style>
