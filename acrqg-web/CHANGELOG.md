# Changelog — acrqg-web

本前端子项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 风格，并采用 [SemVer 2.0.0](https://semver.org/lang/zh-CN/) 进行版本管理。

每条记录尾部以 `(Rxx)` 注明所覆盖的 EARS 需求编号。

## [Unreleased]

### Added — Batch B5-A 业务页面拼装（feat/web-pages）

> 业务页面批次（git 分支 `feat/web-pages`）。在 B0-B Bootstrap 基础上落地 design §5 路由表对应的全部 15 条业务路由 + 通知 / 系统管理页面，与后端 M01 ~ M10 接口完成联调。

#### Wave 1（IT-1 ~ IT-3 节点）

- **B5-A.1** 12 个 axios API 客户端：`src/api/{auth,user,project,repository,reviewTask,issue,report,gate,gateWaiver,dashboard,notification,admin}.ts`，全量 DTO 类型在 `src/types/api.d.ts`，错误码到中文映射在 `src/api/errorCodes.ts` (R1, R3 ~ R22)
- **B5-A.2** UI-001 登录页 `LoginPage.vue`：表单 + 校验 + 错误码映射（AUTH_INVALID_CREDENTIALS / AUTH_ACCOUNT_DISABLED）+ redirect 跳转 (R1.1, R1.2, R3.2)
- **B5-A.3** UI-002 工作台 `DashboardPage.vue`：项目选择器 + 4 张统计卡 + 质量趋势折线图（vue-echarts）+ 高风险文件 Top 10；`AppHeader.vue` 全局头部承接项目切换 / 未读通知红点（30s 轮询 `/notifications/unread-count`）/ 用户菜单 (R18, R19, R1.6)
- **B5-A.4** UI-003 项目列表 `ProjectListPage.vue` + UI-004 项目详情 `ProjectDetailPage.vue`（含基本信息 / 成员 / 仓库三个 Tab） (R4, R6)
- **B5-A.5** UI-005 仓库绑定 Tab `RepositoryBindingTab.vue`：测试连通性 + 绑定 + Webhook URL 复制 (R5)
- **B5-A.6** 成员管理 Tab `MemberManageTab.vue`：autocomplete 搜索用户 + 增删 + 角色变更 (R6)
- **B5-A.7** UI-009 质量门禁 `QualityGatePage.vue`：动态规则表格 + 默认模板 + 历史版本 + 服务端 GATE_RULE_INVALID details 错误标红 (R13)
- **B5-A.14** Vue Router 路由表对齐 design §5.2，`router/guards.ts` 鉴权 / 角色守卫；`stores/{auth,notification,project}.ts` 状态切片

#### Wave 2（IT-4 ~ IT-5 节点）

- **B5-A.8** UI-006 评审任务列表 `ReviewTaskListPage.vue` + 创建对话框 `CreateTaskDialog.vue`：项目 / 状态多选 / 触发类型 / 时间范围筛选 + 分页表格；创建任务时通过 `Idempotency-Key` 头携带前端生成的 UUID 保证 24h 幂等 (R8.1, R8.2, R8.4)
- **B5-A.9** UI-007 评审报告 `ReviewReportPage.vue` 4 Tab（概览 / 问题 / 差异 / 日志）+ 通用 `DiffViewer.vue` 组件；顶部按角色 / 状态条件展示 重试 / 取消 / 申请豁免 按钮；申请豁免对话框 reason ≥ 10 字符 + expireAt 必须为未来时间的前端校验 (R9.4, R9.6, R15, R16)
- **B5-A.10** UI-008 问题详情抽屉 `IssueDetailDrawer.vue`：状态机合法目标过滤（前端常量 `ALLOWED_ISSUE_EDGES` 与后端 `IssueStateMachine.ALLOWED_ISSUE_EDGES` 完全一致）；FALSE_POSITIVE / CLOSED 切换强制 comment.trim().length ≥ 5；评论 + 历史时间线倒序展示 (R17.1 ~ R17.6)
- **B5-A.11** 通知中心 `NotificationListPage.vue`：read/type 筛选 + 一键已读 + 按 type 跳转评审报告（TASK_FINISHED → 报告页；WAIVER_REQUEST → 报告页 #waiver） (R19)
- **B5-A.12** 系统管理 5 页面（仅 SYSTEM_ADMIN）：`UserManagePage`（创建 + 启用/禁用） / `ModelConfigPage`（API Key 掩码 + 编辑） / `ScannerConfigPage`（command 占位符 {workdir} {file}） / `SystemParamPage`（敏感参数 ****） / `AuditLogPage`（detail JSON 折叠） (R3, R21, R22, R23.3)
- **B5-A.13** 门禁豁免审批：`WaiverApprovalDialog.vue` + 在 `ReviewReportPage` 概览 Tab 嵌入豁免列表；PROJECT_ADMIN / REVIEWER / SYSTEM_ADMIN 可对 PENDING 条目 approve/reject（拒绝审批自己提交的申请） (R15.4, R15.5)
- **B5-A.16** 本 CHANGELOG 与 [docs/frontend.md](../docs/frontend.md)（状态切片 + 组件契约）

### Notes
- **B5-A.15（前端 Vitest 测试）** 标记为 optional，本批次未交付，由后续独立任务派发。
- **依赖安装时机**：本批次未在工作流中执行 `npm install`，由 GitHub Actions `frontend-build` job 在 PR 合并时执行 `npm ci`。
- **TypeScript 5.9 警告**：`@typescript-eslint/typescript-estree` 提示当前 TS 版本超出官方支持范围（>=4.7.4 <5.6.0），仅为兼容性警告，不影响 lint 与构建。

## [0.1.0-bootstrap] - Batch B0-B 前端基础设施 Bootstrap

> 前端基础设施 Bootstrap 批次（git 分支 `chore/web-bootstrap`，Integration Node IT-1）。完成 7 项交付，为 B5-A 业务页面落地与后端联调提供完整的脚手架、HTTP 拦截器、布局、状态、路由与容器化运行时。

### Added
- **B0-B.1** Vue 3 + Vite + TypeScript 项目骨架：`package.json`（锁定 `vue@^3.4` / `vite@^5` / `typescript@^5.4`，并安装 `element-plus`、`@element-plus/icons-vue`、`pinia`、`vue-router@4`、`axios`、`dayjs`、`echarts`、`vue-echarts` 与 `vitest` / `@vue/test-utils` / `jsdom` / `eslint` / `prettier` 等开发依赖）；`vite.config.ts`（alias `@/` → `src/`、`/api` 代理至 `http://localhost:8080`、生产分块、Vitest jsdom 环境）；`tsconfig.json` + `tsconfig.app.json` + `tsconfig.node.json` 双 reference；`index.html`、`env.d.ts`、`.eslintrc.cjs`、`.prettierrc.json`、`.editorconfig`、`.gitignore`、`.npmrc`；`src/main.ts`、`src/App.vue`；通用类型 `src/types/api.d.ts` 与工具 `src/utils/{format,permission,uuid}.ts` (R23.6)
- **B0-B.2** axios HTTP 客户端：`src/api/http.ts` 拦截器（注入 `Authorization: Bearer <accessToken>` 与 `X-Request-Id`、解包 `code === 0`、`AUTH_INVALID_TOKEN` 单飞 refresh + 一次重放、refresh 失败回退 `/login`）；`src/api/errorCodes.ts` 完整 15 条错误码中文映射（覆盖 design §8.2 全部条目，`SUCCESS` 不入映射） (R1.4, R1.5, R23.3)
- **B0-B.3** 布局体系：`DefaultLayout.vue`（header 含项目切换器占位 / 用户菜单 / 未读通知红点 + 按角色过滤的左侧菜单 + 路由出口）；`BlankLayout.vue`（公开页居中卡片）；`src/styles/index.scss` 全局基础样式与 Element Plus 设计令牌 (R2)
- **B0-B.4** Pinia 状态：`src/stores/index.ts` 提供 `setupStores(app)` 注册函数；`src/stores/auth.ts` 持久化 accessToken / refreshToken / user 至 `localStorage`，`hydrate()`、`setSession()`、`setAccessToken()`、`logout()` 完整生命周期；`src/stores/notification.ts` 占位（`unreadCount` / `items` / `markRead`） (R1, R19)
- **B0-B.5** Vue Router：`src/router/index.ts` 注册 design §5.2 全部 15 条路由 + 兜底 404，meta 标注 `public` / `requiredRoles` / `title`；`src/router/guards.ts` 全局守卫（未登录 → `/login?redirect=<原路径>`；越权 → `/forbidden`）；占位 `PlaceholderPage.vue`、`LoginPage.vue`、`ForbiddenPage.vue`、`NotFoundPage.vue`；尚未在本批次落地的业务页面统一指向 PlaceholderPage 直至 B5-A 替换 (R2.1, R2.2, R2.3, R2.4, R2.5)
- **B0-B.6** 容器化：多阶段 `Dockerfile`（builder=`node:20-alpine` 构建 → runtime=`nginx:1.25-alpine` serve `/usr/share/nginx/html`）；`nginx.conf`（SPA history `try_files`、`/api/` 反代到 `http://backend:8080`、HSTS / X-Content-Type-Options / X-Frame-Options 安全头、gzip 压缩、长缓存静态资源） (R23.6)
- **B0-B.8** 文档：本 `CHANGELOG.md` 与 `README.md`（开发命令、环境变量、目录结构、与后端契约、容器化指南、后续批次预告）

### Notes
- **依赖安装时机**：本批次未在工作流中执行 `npm install`，由 GitHub Actions `frontend-build` job（B0-A.12 已注册）在 PR 合并时执行 `npm ci`。本地开发首次进入需手动 `npm install`。
- **B0-B.7（可选 Vitest 骨架测试）** 在本批次未交付，由后续独立任务派发（按 tasks.md 标注的 `*` 可选规则）。
- **业务 API 客户端**（`src/api/auth.ts` / `src/api/project.ts` 等）暂未创建，将随对应业务模块在 B5-A 批次按需补充；当前 `src/api/http.ts` 已提供 `request<T>(config)` 通用入口与 `ApiBusinessError` 异常类型，可被后续业务客户端复用。
- **TS 严格模式诊断**：`tsconfig.app.json` 的 `types: ["vite/client", "node"]` 在依赖安装前会报告"找不到类型定义文件"，属预期；`npm install` 后自动消失。

[Unreleased]: ./CHANGELOG.md
[0.1.0-bootstrap]: ./CHANGELOG.md
