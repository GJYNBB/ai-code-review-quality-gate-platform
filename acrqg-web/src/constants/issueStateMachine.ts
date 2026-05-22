import type { CodeIssueStatus } from '@/types/api'

/**
 * 问题状态机合法迁移边集（与后端 `IssueStateMachine.ALLOWED_ISSUE_EDGES` 完全一致）。
 *
 * 关联需求：R17.1 / R17.2。
 *
 * 规则：
 * - NEW              → CONFIRMED, FALSE_POSITIVE
 * - CONFIRMED        → PENDING_VERIFY, CLOSED
 * - PENDING_VERIFY   → CLOSED, REOPENED
 * - CLOSED           → REOPENED
 * - REOPENED         → CONFIRMED, FALSE_POSITIVE
 * - FALSE_POSITIVE   → []（不可流转）
 *
 * 前端使用：在 IssueDetailDrawer 中根据当前状态过滤可选目标状态，避免提交非法迁移。
 */
export const ALLOWED_ISSUE_EDGES: Record<CodeIssueStatus, CodeIssueStatus[]> = {
  NEW: ['CONFIRMED', 'FALSE_POSITIVE'],
  CONFIRMED: ['PENDING_VERIFY', 'CLOSED'],
  PENDING_VERIFY: ['CLOSED', 'REOPENED'],
  CLOSED: ['REOPENED'],
  REOPENED: ['CONFIRMED', 'FALSE_POSITIVE'],
  FALSE_POSITIVE: [],
}

/**
 * 状态切换若目标 ∈ {FALSE_POSITIVE, CLOSED}，则 comment.trim().length ≥ 5（R17.3）。
 */
export const COMMENT_REQUIRED_TARGETS: ReadonlySet<CodeIssueStatus> = new Set([
  'FALSE_POSITIVE',
  'CLOSED',
])

/** 状态显示名（中文） */
export const ISSUE_STATUS_LABELS: Record<CodeIssueStatus, string> = {
  NEW: '新建',
  CONFIRMED: '已确认',
  FALSE_POSITIVE: '误报',
  PENDING_VERIFY: '待验证',
  CLOSED: '已关闭',
  REOPENED: '已重开',
}

/** 状态对应的 el-tag 颜色类型 */
export const ISSUE_STATUS_TAG_TYPE: Record<
  CodeIssueStatus,
  'primary' | 'success' | 'warning' | 'danger' | 'info'
> = {
  NEW: 'danger',
  CONFIRMED: 'warning',
  FALSE_POSITIVE: 'info',
  PENDING_VERIFY: 'primary',
  CLOSED: 'success',
  REOPENED: 'warning',
}

/** 严重度显示名 */
export const SEVERITY_LABELS: Record<string, string> = {
  CRITICAL: '严重',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
  INFO: '提示',
}

/** 严重度对应的 el-tag 颜色类型 */
export const SEVERITY_TAG_TYPE: Record<
  string,
  'primary' | 'success' | 'warning' | 'danger' | 'info'
> = {
  CRITICAL: 'danger',
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'info',
  INFO: 'info',
}
