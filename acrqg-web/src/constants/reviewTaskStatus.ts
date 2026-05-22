import type { ReviewTaskStatus, TriggerType } from '@/types/api'

/**
 * 评审任务状态展示常量（design §7.4 / R8.x / R9.x）。
 */

export const REVIEW_TASK_STATUS_LABELS: Record<ReviewTaskStatus, string> = {
  PENDING: '排队中',
  FETCHING_DIFF: '拉取差异',
  STATIC_SCANNING: '静态扫描',
  AI_REVIEWING: 'AI 评审',
  GATE_EVALUATING: '门禁判定',
  PASSED: '通过',
  FAILED_GATE: '未通过门禁',
  EXECUTION_FAILED: '执行失败',
}

export const REVIEW_TASK_STATUS_TAG_TYPE: Record<
  ReviewTaskStatus,
  'primary' | 'success' | 'warning' | 'danger' | 'info'
> = {
  PENDING: 'info',
  FETCHING_DIFF: 'primary',
  STATIC_SCANNING: 'primary',
  AI_REVIEWING: 'primary',
  GATE_EVALUATING: 'primary',
  PASSED: 'success',
  FAILED_GATE: 'danger',
  EXECUTION_FAILED: 'danger',
}

export const ALL_REVIEW_TASK_STATUSES: ReviewTaskStatus[] = [
  'PENDING',
  'FETCHING_DIFF',
  'STATIC_SCANNING',
  'AI_REVIEWING',
  'GATE_EVALUATING',
  'PASSED',
  'FAILED_GATE',
  'EXECUTION_FAILED',
]

export const TRIGGER_TYPE_LABELS: Record<TriggerType, string> = {
  WEBHOOK: 'Webhook',
  MANUAL: '手动',
  CI_CD: 'CI/CD',
}

export const ALL_TRIGGER_TYPES: TriggerType[] = ['WEBHOOK', 'MANUAL', 'CI_CD']
