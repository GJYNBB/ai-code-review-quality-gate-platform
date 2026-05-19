# Changelog — acrqg-web

本前端子项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 风格，并采用 [SemVer 2.0.0](https://semver.org/lang/zh-CN/) 进行版本管理。

每条记录尾部以 `(Rxx)` 注明所覆盖的 EARS 需求编号，便于回溯到 [requirements.md](../.kiro/specs/ai-code-review-quality-gate-platform/requirements.md) 与 [design.md](../.kiro/specs/ai-code-review-quality-gate-platform/design.md)。

## [Unreleased]

### Added
- 暂无。下一阶段按 [tasks.md](../.kiro/specs/ai-code-review-quality-gate-platform/tasks.md) 中的 B0-B.7（可选 Vitest 骨架测试）与 B5-A（UI-001 ~ UI-010 业务页面）推进。

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
