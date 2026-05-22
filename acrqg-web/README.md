# acrqg-web

AI 辅助代码评审与质量门禁平台 — 前端 SPA。

> 本目录承载 Vue 3 + Vite + TypeScript 的单页应用骨架。本批次 (B0-B) 仅交付前端基础设施 Bootstrap，业务页面（UI-001 ~ UI-010）将在 B5-A 批次按里程碑（IT-2 / IT-3 / IT-4 / IT-5）逐步落地。

## 1. 技术栈

| 维度 | 选型 | 版本 |
|---|---|---|
| 框架 | [Vue 3](https://vuejs.org/) | ^3.4 |
| 构建 | [Vite](https://vitejs.dev/) | ^5 |
| 语言 | TypeScript | ^5.4 |
| UI 组件 | [Element Plus](https://element-plus.org/) + `@element-plus/icons-vue` | ^2.8 |
| 路由 | [Vue Router](https://router.vuejs.org/) | ^4 |
| 状态 | [Pinia](https://pinia.vuejs.org/) | ^2 |
| HTTP | [axios](https://axios-http.com/) | ^1.7 |
| 时间 | [dayjs](https://day.js.org/) | ^1.11 |
| 图表 | [echarts](https://echarts.apache.org/) + [vue-echarts](https://github.com/ecomfe/vue-echarts) | ^5 / ^7 |
| 测试 | [Vitest](https://vitest.dev/) + `@vue/test-utils` + `jsdom` | ^2 |
| 代码风格 | ESLint + `eslint-plugin-vue` + `@vue/eslint-config-typescript` + Prettier | — |

详见 [`package.json`](./package.json) 中的精确版本。

## 2. 目录结构

```
acrqg-web/
├── index.html                # 入口 HTML
├── vite.config.ts            # Vite 配置：alias @、/api 代理、构建分块、Vitest
├── tsconfig*.json            # TS 配置（app + node 双 reference）
├── env.d.ts                  # Vite / 全局类型声明
├── package.json
├── Dockerfile                # 多阶段构建（node:20-alpine → nginx:1.25-alpine）
├── nginx.conf                # 容器内 nginx 站点（SPA history + /api 反代）
├── src/
│   ├── api/                  # axios 实例 + 错误码映射；后续按业务拆分
│   │   ├── http.ts
│   │   └── errorCodes.ts
│   ├── components/           # 通用组件（B5-A 起新增）
│   ├── layouts/
│   │   ├── DefaultLayout.vue # header + sidebar + main，按角色过滤菜单
│   │   └── BlankLayout.vue   # 公开页（登录等）
│   ├── pages/                # 页面（占位 + B5-A 替换为完整实现）
│   │   ├── PlaceholderPage.vue
│   │   ├── login/LoginPage.vue
│   │   ├── forbidden/ForbiddenPage.vue
│   │   └── notfound/NotFoundPage.vue
│   ├── router/
│   │   ├── index.ts          # design §5.2 的 15 条路由 + meta 角色
│   │   └── guards.ts         # 未登录 → /login；越权 → /forbidden
│   ├── stores/               # Pinia
│   │   ├── index.ts
│   │   ├── auth.ts           # accessToken / refreshToken / user 持久化
│   │   └── notification.ts   # 占位
│   ├── styles/index.scss     # 全局基础样式 + Element Plus 设计令牌
│   ├── types/api.d.ts        # 与后端 DTO 对齐的前端类型
│   ├── utils/                # 通用工具（format / permission / uuid）
│   ├── App.vue
│   └── main.ts
└── public/                   # 静态资源直出（如 favicon）
```

## 3. 开发命令

```bash
# 安装依赖（首次 / 依赖变更后）
npm install

# 启动开发服务器（默认 http://localhost:5173；/api 自动代理到 http://localhost:8080）
npm run dev

# 类型检查 + 生产构建（产物输出到 dist/）
npm run build

# ESLint 静态检查并自动修复
npm run lint

# Vitest 单元测试（一次性运行，CI 友好）
npm run test:unit

# 本地预览生产产物
npm run preview
```

## 4. 环境变量

前端通过 Vite `import.meta.env` 读取以 `VITE_` 开头的变量。可在项目根目录创建 `.env.local`（已被 `.gitignore` 忽略）。

| 变量 | 用途 | 默认 |
|---|---|---|
| `VITE_API_BASE_URL` | 直连后端绝对地址；为空时通过 vite 代理走相对路径 `/api/v1` | （空） |
| `VITE_APP_TITLE` | 页面标题，浏览器标签栏与登录页头部 | 智评 — AI 代码评审与质量门禁平台 |

部署到生产后，`/api/` 由容器内 [`nginx.conf`](./nginx.conf) 反代到 `http://backend:8080`，与 [`docker-compose.yml`](../docker-compose.yml) 中 `backend` 服务对齐。

## 5. 与后端的契约

- **响应包装**：`code === 0` 表示成功，HTTP 拦截器解包返回 `data`；其它情况按错误码弹 `ElMessage`。
- **错误码**：完整中文映射见 [`src/api/errorCodes.ts`](./src/api/errorCodes.ts)，与后端 `ErrorCode` 枚举一一对应。
- **Token 刷新**：拦截器自动监听 `AUTH_INVALID_TOKEN`，使用 refreshToken 单飞重放原请求；refresh 失败时清空认证态并跳转 `/login?redirect=...`。
- **请求追踪**：每个请求自动注入 `X-Request-Id`（UUID v4），与后端 MDC `traceId` 串联。

## 6. 路由与权限

完整路由表与必需角色见 [`src/router/index.ts`](./src/router/index.ts)：

- 公开路由：`/login`、`/forbidden`、`/:pathMatch(.*)*`（404）
- 已登录可访问：`/dashboard`、`/projects`、`/review-tasks`、`/notifications` 等
- `PROJECT_ADMIN` / `SYSTEM_ADMIN`：`/projects/:projectId/{repository,members,quality-gate}`
- `SYSTEM_ADMIN`：`/admin/*`

未登录访问受保护路由会被守卫重定向到 `/login?redirect=<原路径>`；登录后角色不足将跳转 `/forbidden`。

## 7. 容器化

- 镜像构建：`docker build -t acrqg/frontend:dev .`（多阶段，最终产物 ≈ 30 MB）
- 容器启动：`docker run --rm -p 80:80 acrqg/frontend:dev`，访问 `http://localhost/`
- 编排：与后端一并由仓库根的 [`docker-compose.yml`](../docker-compose.yml) 拉起；nginx 将 `/api/` 反代到 `backend:8080`。

## 8. 后续批次预告

| 批次 | 交付内容 | 跟踪文件 |
|---|---|---|
| B0-B.7 (可选)  | 前端骨架 Vitest 单元测试（http 拦截器 / router 守卫）   | `tests/unit/*.spec.ts` |
| B5-A           | UI-001 ~ UI-010 全部业务页面，按 IT-2 / IT-3 / IT-4 / IT-5 里程碑分阶段拼装 | `src/pages/*` |
