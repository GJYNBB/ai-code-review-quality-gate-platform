# 前端架构文档（acrqg-web）

> 关联设计：[design.md](../.kiro/specs/ai-code-review-quality-gate-platform/design.md) §5（路由 / 组件树）、§16（报告页结构）、§17（问题状态机）、§19（通知）。
> 关联需求：R1 ~ R3、R4 ~ R6、R8 ~ R9、R13 ~ R19、R21 ~ R23。
>
> 本文档面向「想理解 acrqg-web 中状态管理结构与跨组件契约」的开发者，对应 B5-A.16 交付。
> 命令、目录结构、容器化指南详见 [acrqg-web/README.md](../acrqg-web/README.md)。

## 1. 目录速览

```
acrqg-web/src/
├── api/                # 12 个轻量 axios 客户端（auth/user/project/...）
├── components/         # 跨页面复用组件
│   ├── AppHeader.vue       # 全局头部（项目切换 / 红点 / 用户菜单）
│   ├── DiffViewer.vue      # 通用 diff 渲染器
│   ├── IssueDetailDrawer.vue
│   └── WaiverApprovalDialog.vue
├── constants/          # 与后端常量对齐的前端镜像
│   ├── issueStateMachine.ts    # ALLOWED_ISSUE_EDGES (R17.1)
│   └── reviewTaskStatus.ts     # 状态/触发类型展示常量
├── layouts/            # DefaultLayout / BlankLayout
├── pages/
│   ├── admin/      # B5-A.12: User / Model / Scanner / SystemParam / AuditLog
│   ├── dashboard/  # UI-002 工作台
│   ├── login/      # UI-001 登录
│   ├── notification/   # B5-A.11 通知中心
│   ├── project/    # UI-003 / 004 / 005 / 009
│   ├── review/     # B5-A.8 / 9：列表 + 报告页（4 Tab）+ 4 个 Tab 子组件
│   ├── forbidden/ / notfound/
│   └── PlaceholderPage.vue
├── router/         # routes + guards (R2.x)
├── stores/         # auth / notification / project (Pinia)
├── styles/
├── types/api.d.ts  # 与后端 DTO 对齐的全量类型
└── utils/          # format / permission / uuid
```

## 2. Pinia 状态切片说明

### 2.1 `useAuthStore` (`src/stores/auth.ts`) — R1 / R3

| 字段 | 类型 | 说明 |
|---|---|---|
| `accessToken` | `string \| null` | JWT，注入 axios `Authorization: Bearer ...` |
| `refreshToken` | `string \| null` | 刷新令牌；http 拦截器单飞刷新使用 |
| `expiresAt` | `number \| null` | accessToken 失效绝对时间戳（ms）；getter `isExpired` 比较 `Date.now()` |
| `user` | `UserDTO \| null` | 当前登录用户；`roles` 决定路由守卫与按钮可见性 |
| `hydrated` | `boolean` | 是否已从 localStorage 还原；guards / http 拦截器调用前调用 `hydrate()` |

**Actions**

- `hydrate()` — 应用启动 / 拦截器调用前的幂等还原。
- `setSession({accessToken, refreshToken, expiresIn, user})` — 登录成功后调用。
- `setAccessToken({accessToken, expiresIn})` — 仅刷新 access token，refresh / user 不变。
- `setUser(user | null)` — 用于 `/auth/me` 拉取后回填。
- `logout()` — 清空内存与 localStorage（持久化键 `acrqg.auth`）。

**Getters**

- `isAuthenticated` — `accessToken && user` 同时存在；
- `roles: Role[]` — `user.roles ?? []`，配合 `utils/permission.hasAnyRole` 使用；
- `isExpired` — 启发式过期判定，仅用于 UI 展示，权威判定仍由后端 `AUTH_INVALID_TOKEN` 触发。

### 2.2 `useNotificationStore` (`src/stores/notification.ts`) — R19

| 字段 | 类型 | 说明 |
|---|---|---|
| `unreadCount` | `number` | 红点徽章绑定值；30s 轮询由 `AppHeader` 维护 |
| `items` | `NotificationDTO[]` | NotificationListPage 加载列表后写入；`AppHeader` 不依赖此字段 |

**Actions**

- `setUnreadCount(n)` — 钳位到 `>= 0`；
- `setItems(items)` — 列表页加载后调用；
- `markRead(id)` — 列表内单条已读时同步内存（不重复请求后端）；
- `reset()` — 登出时清理。

### 2.3 `useProjectStore` (`src/stores/project.ts`) — R4 / R6

| 字段 | 类型 | 说明 |
|---|---|---|
| `projects` | `ProjectDTO[]` | 用户可见项目列表（pageSize=200 一次拉取） |
| `currentProjectId` | `number \| null` | 当前项目上下文；持久化键 `acrqg.project.current` |
| `loaded` | `boolean` | 是否已 loadAll；避免重复请求 |
| `loading` | `boolean` | 防并发 |

**Actions**

- `loadAll(force = false)` — 拉一次项目列表，并自动校准 `currentProjectId`（失效则取首个）；
- `setCurrentProject(id)` — 切换上下文，AppHeader / Dashboard 通过 `storeToRefs` 监听；
- `reset()` — 登出时清理。

**Getters**

- `currentProject` — 在 `projects` 中按 id 反查；用于头部展示 / Dashboard 默认项目。

## 3. 组件契约

### 3.1 `AppHeader.vue`（无入参）

挂载点：`DefaultLayout.vue`。

**外部依赖**：

- `useAuthStore` — 展示用户名 / 角色 / 登出；
- `useNotificationStore` — `unreadCount` 红点；
- `useProjectStore` — 项目下拉绑定；
- `notificationApi.unreadCount()` — 30s `setInterval` 轮询，失败静默（`skipErrorMessage: true`）；
- `authApi.logout()` — 登出时 best-effort，失败也清本地态。

**事件 / Slot**：无。组件挂载时拉项目列表 + 启动轮询定时器；卸载时 `clearInterval`。

### 3.2 `DiffViewer.vue` (`src/components/DiffViewer.vue`)

```ts
defineProps<{ files: DiffFileDTO[] }>()
```

**渲染逻辑**：

- 每个文件用 `el-collapse-item` 包裹（默认全部展开，name 取 `filePath`）；
- 头部展示 `filePath / changeType / +addedLines / -deletedLines`；
- 每个 hunk 渲染为多行：
  - `+` 开头 → 绿色背景 (`#e6ffed`)，新行号递增；
  - `-` 开头 → 红色背景 (`#ffeef0`)，旧行号递增；
  - 空格 / 无前缀 → 上下文行，新旧行号同时递增；
  - hunk header 单独成行（灰底）；
- 行号双列展示（旧 / 新）；内容栏支持横向滚动以容纳长行。

### 3.3 `IssueDetailDrawer.vue` (`src/components/IssueDetailDrawer.vue`) — R17

```ts
defineProps<{
  modelValue: boolean
  issueId: number | null
}>()

defineEmits<{
  'update:modelValue': [v: boolean]
  changed: []   // 状态切换或评论提交成功后触发，调用方刷新列表
}>()
```

**关键约束**：

- 抽屉宽度 50%（`size="50%"`）；
- 状态切换下拉的可选项，根据 `issue.status` 在前端常量 `ALLOWED_ISSUE_EDGES` 中查表得到；常量与后端 `IssueStateMachine.ALLOWED_ISSUE_EDGES` 完全一致：

  | from | allowed targets |
  |---|---|
  | `NEW` | `CONFIRMED, FALSE_POSITIVE` |
  | `CONFIRMED` | `PENDING_VERIFY, CLOSED` |
  | `PENDING_VERIFY` | `CLOSED, REOPENED` |
  | `CLOSED` | `REOPENED` |
  | `REOPENED` | `CONFIRMED, FALSE_POSITIVE` |
  | `FALSE_POSITIVE` | `[]`（终态） |

- 目标状态 ∈ `{FALSE_POSITIVE, CLOSED}` 时，`comment.trim().length >= 5` 才允许提交（与后端 `IssueServiceImpl.STATUS_COMMENT_MIN_LENGTH` 对齐）；
- 评论 / 历史时间线均按时间倒序展示。
- 后端返回 `VALIDATION_ERROR`（如 DEVELOPER 操作非自身任务的 issue，R17.5）由全局 axios 拦截器统一弹 `ElMessage.error`。

### 3.4 `WaiverApprovalDialog.vue` (`src/components/WaiverApprovalDialog.vue`) — R15

```ts
defineProps<{
  modelValue: boolean
  waiver: GateWaiverDTO | null   // 父组件传入待审批对象；为 null 时禁用提交按钮
}>()

defineEmits<{
  'update:modelValue': [v: boolean]
  done: []   // 审批成功后触发，调用方负责 reloadWaivers + reloadTask
}>()
```

**行为**：

- `decision: 'APPROVE' | 'REJECT'`（默认 `APPROVE`）；
- `comment: string`（可选）；
- 调用对应 `gateWaiverApi.approve(id, {comment})` 或 `gateWaiverApi.reject(id, {comment})`；
- 不允许审批自己的申请——`ReviewReportPage` 在表格上层过滤 `applicantId === user.id` 时不展示「审批」按钮。

### 3.5 ReviewReport 4 Tab（`src/pages/review/`）

`ReviewReportPage.vue` 提供：

- `taskId: number` 由路由 param 解析；
- 4 个 Tab 子组件均接收 `defineProps<{ taskId: number }>()` 并在 onMounted / watch(taskId) 时拉自己的数据：
  - `ReviewReportOverviewTab` — `report.report` + 饼图（按 severity 聚合）；通过 `defineExpose({ reload, report })` 让父组件在豁免审批后强制刷新。提供具名 slot `extra` 供父组件注入豁免列表卡片。
  - `ReviewReportIssuesTab` — `issue.pageByTask` + `IssueDetailDrawer`；
  - `ReviewReportDiffTab` — `report.diff` + `DiffViewer`；
  - `ReviewReportLogsTab` — `report.logs`，`level=ERROR` 行展开显示 `detail`（JSON 美化）。

### 3.6 `CreateTaskDialog.vue` (`src/pages/review/`)

```ts
defineProps<{
  modelValue: boolean
  defaultProjectId?: number | null   // 默认项目，一般传当前路由上下文
}>()

defineEmits<{
  'update:modelValue': [v: boolean]
  created: []   // 创建成功后触发，调用方刷新列表
}>()
```

**约束**：

- `commitSha` 与 `prId` 至少填一项（前端校验，未填则提示）；
- 提交时通过 `crypto.randomUUID()`（`utils/uuid.ts`）生成幂等键，注入 `Idempotency-Key` 请求头；后端 24h 内同 key 返回同一任务（design §8.5 / R8.4）。

## 4. 路由与权限矩阵

| 路径 | 组件 | 守卫 |
|---|---|---|
| `/login` | LoginPage | 公开 |
| `/dashboard` | DashboardPage | 已登录 |
| `/projects` | ProjectListPage | 已登录 |
| `/projects/:projectId` | ProjectDetailPage | 项目成员（后端校验） |
| `/projects/:projectId/quality-gate` | QualityGatePage | PROJECT_ADMIN / SYSTEM_ADMIN |
| `/review-tasks` | ReviewTaskListPage | 已登录 |
| `/review-tasks/:taskId/report` | ReviewReportPage | 项目成员（后端校验） |
| `/notifications` | NotificationListPage | 已登录 |
| `/admin/*` | UserManagePage / ModelConfigPage / ScannerConfigPage / SystemParamPage / AuditLogPage | SYSTEM_ADMIN |
| `/forbidden`、`/:pathMatch(.*)*` | ForbiddenPage / NotFoundPage | 公开 |

路由 meta 的 `requiredRoles?: Role[]` 由 `router/guards.ts` 在 `beforeEach` 中匹配；空数组 / 不写均表示「任意已登录可访问」；非空数组表示「至少拥有其中一个角色」。「项目成员」级别的限制在前端无法严格判定，统一交由后端在接口层面返回 `PERMISSION_DENIED`，由 axios 拦截器弹错。

## 5. 错误码 → 中文映射

`src/api/errorCodes.ts` 维护错误码到中文提示的映射，与 design §8.2 ErrorCode 枚举完全对齐。所有非 `code === 0` 响应在 axios 响应拦截器中统一弹 `ElMessage.error(describeErrorCode(code, message))`，业务侧若需自行处理可通过 `skipErrorMessage: true` 屏蔽（如登录失败、连通性测试、未读数轮询）。

## 6. 与后端契约清单（接口骨架）

> 完整定义见 [acrqg-web/src/api/](../acrqg-web/src/api/)；本节仅列举关键约束。

- **认证**：`POST /auth/login` / `POST /auth/refresh` / `POST /auth/logout` / `GET /auth/me`。`refresh` 调用由 axios 拦截器在 `AUTH_INVALID_TOKEN` 时单飞触发，业务代码无需关心。
- **评审任务创建幂等**：`POST /review-tasks` 携带请求头 `Idempotency-Key: <uuid>`；后端 24h 内同 key 返回同一任务（R8.4）。
- **问题状态切换**：`PATCH /issues/{id}/status`；`{status, comment?}`，FALSE_POSITIVE / CLOSED 时 comment 必填且 ≥ 5 字符。前端常量 `ALLOWED_ISSUE_EDGES` 镜像后端状态机。
- **门禁规则保存**：`POST /projects/{projectId}/quality-gate`；非法规则返回 `GATE_RULE_INVALID` 与 `details: FieldError[]`，前端在 `QualityGatePage` 解析 `rules[i].field` 进行行级标红。
- **豁免**：`POST /review-tasks/{taskId}/waivers` 申请 / `POST /waivers/{id}/approval` 审批（`{approve: boolean, comment?}`）。
- **通知未读数轮询**：`GET /notifications/unread-count`，30s 间隔，`skipErrorMessage: true`。
- **看板**：`GET /projects/{id}/dashboard/trend?startDate&endDate&branch?` + `GET /projects/{id}/dashboard/risk-files?limit=`。

## 7. 后续工作

- B5-A.15（前端 Vitest 测试） — 为 stores、constants、关键组件补单元测试与组件交互测试；
- B6-A 系列 — E2E（Cypress / Playwright）覆盖登录 → 创建任务 → 报告查看 → 问题状态流转 → 豁免审批主流程；
- B6-B 性能基线 — 大 diff（500+ 文件、5000+ 行）渲染性能、Logs 分页虚拟滚动改造（如有需要）。
