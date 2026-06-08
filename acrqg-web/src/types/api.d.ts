/**
 * 与后端 DTO 对齐的类型定义。
 *
 * 来源对照：
 * - design.md §8.1 / §8.4：统一响应包装、关键 DTO 与 Bean Validation 约束
 * - design.md §8.7：接口清单
 * - design.md §16.x：评审报告 / 错误码
 * - acrqg-platform 中的 record 定义（保持字段一一对应）
 *
 * 注意事项：
 * 1. 后端 OffsetDateTime / LocalDate 在 JSON 中均为 ISO 字符串；前端类型统一为 `string`
 * 2. 后端 BigDecimal 默认序列化为 number 字符串（Jackson 配置可能为字面量数字）；这里
 *    用 `number | string` 兼容两种情况
 * 3. 所有可空字段使用 `field?: T | null` 风格；列表字段除非后端明确允许 null，否则保持 `T[]`
 */

// =============== 角色 / 状态 / 枚举 =============== //

/** 全局角色编码（与后端 Role 表 code 字段一致） */
export type Role = 'DEVELOPER' | 'REVIEWER' | 'PROJECT_ADMIN' | 'SYSTEM_ADMIN' | 'CI_CD'

/** 项目级角色（project_member.project_role） */
export type ProjectRole = 'DEVELOPER' | 'REVIEWER' | 'PROJECT_ADMIN'

/** 用户状态（user.status） */
export type UserStatus = 'ENABLED' | 'DISABLED'

/** 评审任务状态字典（design §7.4） */
export type ReviewTaskStatus =
  | 'PENDING'
  | 'FETCHING_DIFF'
  | 'STATIC_SCANNING'
  | 'AI_REVIEWING'
  | 'GATE_EVALUATING'
  | 'PASSED'
  | 'FAILED_GATE'
  | 'EXECUTION_FAILED'

/** 任务触发来源 */
export type TriggerType = 'WEBHOOK' | 'MANUAL' | 'CI_CD'

/** 问题状态字典 */
export type CodeIssueStatus =
  | 'NEW'
  | 'CONFIRMED'
  | 'FALSE_POSITIVE'
  | 'PENDING_VERIFY'
  | 'CLOSED'
  | 'REOPENED'

/** 严重等级 */
export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO'

/** 问题来源 */
export type IssueSource = 'SAST' | 'AI' | 'MANUAL'

/** 项目支持语言 */
export type ProjectLanguage = 'Java' | 'Python' | 'JavaScript' | 'TypeScript' | 'Go'

/** 仓库平台 */
export type Provider = 'GITHUB' | 'GITLAB' | 'GITEE'

/** 门禁规则严重度 */
export type GateRuleSeverity = 'BLOCKER' | 'WARN'

/** 门禁规则比较运算符 */
export type GateOperator = 'GT' | 'GTE' | 'LT' | 'LTE' | 'EQ' | 'NEQ'

/** 门禁判定状态 */
export type GateStatus = 'PASSED' | 'FAILED' | 'WAIVED' | 'PENDING'

/** 门禁可用 metric（design §13.5 / R13.6） */
export type GateMetric =
  | 'critical_issue_count'
  | 'security_issue_count'
  | 'test_coverage'
  | 'duplicate_rate'
  | 'ai_risk_score'
  | 'new_issue_count'

/** 豁免审批状态 */
export type GateWaiverStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

// =============== 通用 =============== //

/** 后端统一响应包装（design §8.1） */
export interface ApiResponse<T> {
  /** 0 表示成功；失败时为字符串错误码 */
  code: number | string
  message: string
  data: T | null
  details?: FieldError[] | null
  requestId?: string
}

export interface FieldError {
  field: string
  reason: string
}

/** 分页结果（design §8.1） */
export interface PageResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

/** 通用分页参数（多数列表接口共用） */
export interface PageQuery {
  page?: number
  pageSize?: number
}

// =============== 认证 / 用户（R1, R3） =============== //

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResultDTO {
  accessToken: string
  expiresIn: number
  user: UserDTO
}

export interface RefreshResultDTO {
  accessToken: string
  expiresIn: number
}

export interface UserDTO {
  id: number
  username: string
  email: string
  status: UserStatus
  roles: Role[]
  createdAt: string
}

export interface UserQuery extends PageQuery {
  keyword?: string
  status?: UserStatus
  role?: Role
}

export interface UserCreateRequest {
  username: string
  email: string
  password: string
  roles: Role[]
}

export interface UserStatusChangeRequest {
  status: UserStatus
}

// =============== 项目 / 成员（R4, R6） =============== //

export interface ProjectDTO {
  id: number
  name: string
  description?: string | null
  defaultBranch: string
  language: ProjectLanguage | string
  createdBy: number
  memberCount: number
  createdAt: string
}

export interface ProjectQuery extends PageQuery {
  keyword?: string
}

export interface ProjectCreateRequest {
  name: string
  description?: string
  defaultBranch: string
  language: ProjectLanguage
}

export interface ProjectUpdateRequest {
  description?: string
  defaultBranch?: string
  language?: ProjectLanguage
}

export interface ProjectMemberDTO {
  userId: number
  username: string
  role: ProjectRole
  joinedAt: string
}

export interface AddMemberRequest {
  userId: number
  role: ProjectRole
}

// =============== 仓库绑定（R5） =============== //

export interface RepositoryTestRequest {
  provider: Provider
  repoUrl: string
  accessToken: string
}

export interface RepositoryBindRequest {
  provider: Provider
  repoUrl: string
  accessToken: string
  webhookSecret: string
}

export interface RepositoryBindingDTO {
  id: number
  projectId: number
  provider: Provider | string
  repoUrl: string
  webhookUrl: string
  status: 'ACTIVE' | 'INACTIVE' | string
  lastCheckedAt?: string | null
}

export interface ConnectivityResultDTO {
  reachable: boolean
  message: string
}

// =============== 评审任务（R7, R8, R9） =============== //

export interface ReviewTaskDTO {
  id: number
  taskNo: string
  projectId: number
  prId?: string | null
  sourceBranch: string
  targetBranch: string
  commitSha: string
  status: ReviewTaskStatus | string
  triggerType: TriggerType | string
  score?: number | null
  aiRiskScore?: number | null
  aiAvailable: boolean
  attempt: number
  createdBy?: number | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface ReviewTaskQuery extends PageQuery {
  projectId?: number
  status?: ReviewTaskStatus
  triggerType?: TriggerType
}

export interface ReviewTaskCreateRequest {
  projectId: number
  sourceBranch: string
  targetBranch: string
  commitSha?: string
  prId?: string
  triggerType: TriggerType
}

export interface RetryRequest {
  reason?: string
}

export interface CancelRequest {
  reason: string
}

export interface TaskLogDTO {
  id: number
  taskId: number
  stage: string
  level: 'INFO' | 'WARN' | 'ERROR'
  message: string
  detail?: Record<string, unknown> | null
  createdAt: string
}

export interface TaskLogQuery extends PageQuery {
  stage?: string
  level?: 'INFO' | 'WARN' | 'ERROR'
}

// =============== 问题（R16, R17） =============== //

export interface IssueHistoryDTO {
  id: number
  codeIssueId: number
  fromStatus: CodeIssueStatus | string
  toStatus: CodeIssueStatus | string
  comment?: string | null
  operatorId?: number | null
  operatorName?: string | null
  changedAt: string
}

export interface IssueCommentDTO {
  id: number
  codeIssueId: number
  content: string
  operatorId: number
  operatorName: string
  createdAt: string
}

export interface CodeIssueDTO {
  id: number
  taskId: number
  filePath: string
  lineNo?: number | null
  ruleCode: string
  source: IssueSource | string
  severity: Severity
  status: CodeIssueStatus
  description: string
  suggestion?: string | null
  confidence?: number | string | null
  createdAt: string
  updatedAt: string
  /** 列表接口为 null；详情接口非 null */
  history?: IssueHistoryDTO[] | null
  /** 列表接口为 null；详情接口非 null */
  comments?: IssueCommentDTO[] | null
}

export interface IssueQuery extends PageQuery {
  severity?: Severity[]
  status?: CodeIssueStatus[]
  source?: IssueSource
  filePath?: string
  keyword?: string
}

export interface IssueStatusChangeRequest {
  status: CodeIssueStatus
  comment?: string
}

export interface IssueCommentCreateRequest {
  content: string
}

// =============== 报告（R16） =============== //

export interface TaskOverviewDTO {
  taskId: number
  taskNo: string
  projectId: number
  projectName?: string | null
  prId?: string | null
  commitSha: string
  sourceBranch: string
  targetBranch: string
  status: ReviewTaskStatus | string
  score?: number | null
  durationSeconds?: number | null
  createdAt: string
  finishedAt?: string | null
}

export interface IssueCountAggDTO {
  severity: Severity
  source: IssueSource | string
  count: number
}

export interface RuleEvalDTO {
  metric: string
  operator: string
  threshold: string
  severity: GateRuleSeverity | string
  actual?: number | string | null
  passed: boolean
}

export interface GateResultSummaryDTO {
  status: GateStatus | string
  score?: number | null
  aiRiskScore?: number | null
  aiAvailable: boolean
  failedRules: RuleEvalDTO[]
  passedRules: RuleEvalDTO[]
}

export interface ReviewReportDTO {
  taskOverview: TaskOverviewDTO
  gateResultSummary?: GateResultSummaryDTO | null
  issueCounts: IssueCountAggDTO[]
  aiAvailability: boolean
}

export interface DiffHunkDTO {
  oldStart: number
  oldLines: number
  newStart: number
  newLines: number
  header?: string | null
  /** 行内容（含 +/- 前缀），具体格式由后端 DiffViewDTO 决定；前端做兜底渲染 */
  lines?: string[]
}

export interface DiffFileDTO {
  filePath: string
  changeType?: string | null
  addedLines?: number
  deletedLines?: number
  hunks: DiffHunkDTO[]
}

export interface DiffViewDTO {
  taskId: number
  changedFileCount: number
  files: DiffFileDTO[]
}

// =============== 门禁（R13, R14, R15） =============== //

export interface GateRuleDTO {
  id?: number | null
  metric: GateMetric | string
  operator: GateOperator | string
  threshold: string
  severity: GateRuleSeverity
  enabled?: boolean | null
}

export interface QualityGateDTO {
  id?: number | null
  projectId?: number | null
  name: string
  version?: number | null
  enabled: boolean
  createdBy?: number | null
  createdAt?: string | null
  rules: GateRuleDTO[]
}

export interface QualityGateSaveRequest {
  name: string
  rules: GateRuleDTO[]
}

export interface GateResultSummary {
  failedRules: RuleEvalDTO[]
  passedRules: RuleEvalDTO[]
  metricValues?: Record<string, number | string> | null
  aiAvailable: boolean
}

export interface GateResultDTO {
  id: number
  taskId: number
  status: GateStatus | string
  score?: number | null
  aiRiskScore?: number | null
  aiAvailable: boolean
  summary: GateResultSummary
  createdAt: string
  updatedAt: string
}

export interface GateWaiverDTO {
  id: number
  taskId: number
  projectId: number
  reason: string
  status: GateWaiverStatus | string
  applicantId: number
  approverId?: number | null
  approvedAt?: string | null
  approvalComment?: string | null
  createdAt: string
  updatedAt: string
}

export interface GateWaiverSubmitRequest {
  reason: string
}

export interface GateWaiverApproveRequest {
  approve: boolean
  comment?: string
}

// =============== 看板（R18） =============== //

export interface DashboardQuery {
  startDate: string
  endDate: string
  branch?: string
}

export interface TrendPointDTO {
  date: string
  taskCount: number
  passCount: number
  failCount: number
  passRate?: number | string | null
  avgScore?: number | string | null
  avgDurationSeconds?: number | string | null
}

export interface QualityTrendTotals {
  totalTasks: number
  overallPassRate?: number | string | null
  overallAvgScore?: number | string | null
}

export interface QualityTrendDTO {
  projectId: number
  startDate: string
  endDate: string
  points: TrendPointDTO[]
  totals: QualityTrendTotals
}

export interface RiskFileDTO {
  filePath: string
  issueCount: number
  weightedScore: number | string
  criticalCount: number
  highCount: number
}

// =============== 通知（R19） =============== //

export interface NotificationDTO {
  id: number
  userId: number
  type: string
  title: string
  body: string
  link?: string | null
  read: boolean
  relatedType?: string | null
  relatedId?: number | null
  createdAt: string
  readAt?: string | null
}

export interface NotificationQuery extends PageQuery {
  type?: string
  read?: boolean
}

export interface UnreadCountDTO {
  count: number
}

// =============== 系统管理（R21, R22） =============== //

export interface ModelConfigDTO {
  id: number
  name: string
  baseUrl: string
  apiKeyMasked: string
  timeoutSeconds: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface ModelConfigCreateRequest {
  name: string
  baseUrl: string
  apiKey: string
  timeoutSeconds: number
}

export interface ModelConfigUpdateRequest {
  baseUrl?: string
  apiKey?: string
  timeoutSeconds?: number
  enabled?: boolean
}

export interface ScannerConfigDTO {
  id: number
  name: string
  language: string
  enabled: boolean
  command: string
  resultParserType: string
  createdAt: string
  updatedAt: string
}

export interface ScannerConfigRequest {
  name: string
  language: string
  enabled?: boolean
  command: string
  resultParserType: string
}

export interface SystemParamDTO {
  paramKey: string
  paramValue: string
  description?: string | null
  sensitive: boolean
  updatedBy?: number | null
  updatedAt: string
}

export interface SystemParamUpdateRequest {
  value: string
}

export interface AuditLogDTO {
  id: number
  operatorId?: number | null
  operatorUsername?: string | null
  action: string
  resourceType: string
  resourceId: string
  ip: string
  detail?: Record<string, unknown> | null
  createdAt: string
}

export interface AuditQuery extends PageQuery {
  operator?: string
  action?: string
  startDate?: string
  endDate?: string
}
