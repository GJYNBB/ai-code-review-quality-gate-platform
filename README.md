# AI 辅助代码评审与质量门禁平台

[![CI](https://github.com/GJYNBB/ai-code-review-quality-gate-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/GJYNBB/ai-code-review-quality-gate-platform/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3.x-42b883)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-336791)
![Redis](https://img.shields.io/badge/Redis-7-dc382d)

> **ai-code-review-quality-gate-platform** 是一个面向 Pull Request / Merge Request 场景的一站式 AI 辅助代码评审与质量门禁平台。
>
> 平台在开发者提交代码后自动拉取差异、执行静态扫描、调用 AI 生成结构化评审意见、聚合质量指标、执行质量门禁规则，并将检查结果回写到代码托管平台，帮助团队把代码评审、质量度量和发布准入流程标准化。

[English README](./README_EN.md)

## 目录

- [项目特性](#项目特性)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [仓库结构](#仓库结构)
- [快速开始](#快速开始)
- [环境变量](#环境变量)
- [本地开发](#本地开发)
- [测试与质量保障](#测试与质量保障)
- [模块划分](#模块划分)
- [文档导航](#文档导航)
- [开发规范](#开发规范)
- [License](#license)

## 项目特性

- **自动化代码评审流水线**：接收 webhook 或手动触发评审任务，自动完成 diff 拉取、扫描、AI 评审、报告聚合和状态回写。
- **多维质量门禁**：支持基于严重问题数、测试覆盖率、重复率、安全风险等指标的规则配置与门禁判定。
- **AI 辅助评审**：兼容 OpenAI Chat Completions 风格接口，通过 JSON Schema 约束输出结构，并提供敏感信息过滤与降级策略。
- **静态扫描适配层**：封装 Checkstyle、ESLint、Pylint、Semgrep 等扫描器，统一问题格式和严重级别。
- **异步任务处理**：基于 Redis Stream 构建评审任务队列，API 与 Worker 分离部署，适合扩展后台处理能力。
- **安全与审计**：提供 JWT 鉴权、黑名单、仓库 token 加密、越权测试矩阵和 append-only 审计日志。
- **质量可视化**：提供问题流转、报告聚合、项目趋势看板和质量指标报告。
- **工程化保障**：内置 Maven / Vitest / Testcontainers / jqwik / k6 / GitHub Actions，覆盖单元测试、集成测试、属性测试、性能基准和 CI 构建。

## 系统架构

平台采用前后端分离 + 异步 Worker 架构：

```text
┌──────────────┐      ┌─────────────────┐      ┌──────────────────┐
│ Code Hosting │─────▶│ Backend API      │─────▶│ PostgreSQL        │
│ GitHub etc.  │      │ Spring Boot      │      │ Business Storage  │
└──────────────┘      └────────┬────────┘      └──────────────────┘
                               │
                               ▼
                      ┌─────────────────┐
                      │ Redis Stream     │
                      │ Queue / Cache    │
                      └────────┬────────┘
                               │
                               ▼
                      ┌─────────────────┐      ┌──────────────────┐
                      │ Worker           │─────▶│ Scanner / AI      │
                      │ Review Pipeline  │      │ External Services │
                      └─────────────────┘      └──────────────────┘
                               │
                               ▼
                      ┌─────────────────┐
                      │ Frontend SPA     │
                      │ Vue + Element UI │
                      └─────────────────┘
```

核心流程：

1. 代码平台创建 PR/MR 或发送 webhook。
2. Backend 校验签名与幂等键，创建评审任务。
3. Worker 从 Redis Stream 消费任务。
4. Worker 拉取 diff，执行静态扫描和 AI 评审。
5. 平台聚合问题、覆盖率、重复率等质量指标。
6. 质量门禁规则引擎输出通过 / 失败 / 豁免中等结果。
7. 报告、通知和 commit status 被写回平台或展示在前端看板。

更完整的架构设计见 [docs/architecture.md](./docs/architecture.md)。

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Spring Boot 3.3.5、Java 17、MyBatis-Plus、Spring Security、Spring Actuator |
| 前端 | Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router、ECharts |
| 数据库 | PostgreSQL 15、Flyway |
| 缓存 / 队列 | Redis 7、Redis Stream |
| AI 接入 | OpenAI Chat Completions 兼容接口、JSON Schema、敏感信息过滤 |
| 静态扫描 | Checkstyle、ESLint、Pylint、Semgrep |
| 测试 | JUnit 5、Mockito、Spring Security Test、Testcontainers、WireMock、jqwik、Vitest、k6 |
| 可观测性 | Spring Actuator、Micrometer、Prometheus Registry、JSON Logback Encoder |
| 工程化 | Maven、npm、Docker、docker-compose、GitHub Actions |

## 仓库结构

```text
ai-code-review-quality-gate-platform/
├── acrqg-platform/         # 后端 Maven 单模块工程（Spring Boot 3 + Java 17）
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/{main,test}/{java,resources}/
├── acrqg-web/              # 前端 Vue 单包工程（Vue 3 + Vite + TypeScript）
│   ├── package.json
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/{api,components,layouts,pages,router,stores,styles,types}/
├── docker-compose.yml      # PostgreSQL + Redis + backend + worker + frontend 编排
├── .env.example            # 环境变量模板，复制为 .env 后使用
├── docs/                   # 架构、前端、OpenAPI 契约、IT-6 验证报告
├── perf/                   # k6 性能基准脚本
├── scripts/smoke.sh        # docker-compose 冒烟测试脚本
├── .github/workflows/      # GitHub Actions CI
├── CHANGELOG.md            # 批次变更记录
├── README.md               # 中文项目说明
└── README_EN.md            # English README
```

## 快速开始

### 前置条件

| 工具 | 建议版本 |
|---|---|
| JDK | 17+ |
| Maven | 3.9+ |
| Node.js | 20+ |
| npm | 10+ |
| Docker | 24+ |
| Docker Compose | v2+ |

### 使用 Docker Compose 启动

```bash
cp .env.example .env
# 按需修改 .env 中的 DB_PASSWORD、JWT_SECRET、TOKEN_ENCRYPTION_KEY 等配置

docker compose up -d
bash scripts/smoke.sh
```

默认服务地址：

| 服务 | 地址 |
|---|---|
| Backend API | http://localhost:8080 |
| Actuator Health | http://localhost:8080/actuator/health |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Frontend / Nginx | http://localhost |
| PostgreSQL | localhost:5432 |
| Redis | localhost:6379 |

默认种子管理员账号：

```text
username: admin
password: admin
```

> 初始密码哈希由 `V1__init.sql` 注入。生产或公开演示环境必须立即修改默认账号密码，并替换所有默认密钥。

## 环境变量

`.env.example` 提供本地运行模板。关键配置如下：

| 变量 | 说明 | 默认 / 示例 |
|---|---|---|
| `DB_USER` | PostgreSQL 用户名 | `acrqg` |
| `DB_PASSWORD` | PostgreSQL 密码 | `replace-me-in-prod` |
| `AI_REVIEW_BASE_URL` | AI 评审服务地址；留空时走降级路径 | 空 |
| `AI_REVIEW_TIMEOUT_SECONDS` | AI 请求超时时间 | `60` |
| `REVIEW_WORKER_CONCURRENCY` | Worker 消费线程数 | `4` |
| `WEBHOOK_SIGNATURE_HEADER` | webhook 签名请求头 | `X-Hub-Signature-256` |
| `TOKEN_ENCRYPTION_KEY` | 仓库 token 加密密钥 | 本地开发默认值 |
| `JWT_SECRET` | JWT HS256 签名密钥 | 本地开发默认值 |
| `JWT_ACCESS_TTL_SECONDS` | Access token 有效期 | `7200` |
| `JWT_REFRESH_TTL_SECONDS` | Refresh token 有效期 | `604800` |

生产环境最低要求：

- 替换 `DB_PASSWORD`、`TOKEN_ENCRYPTION_KEY`、`JWT_SECRET`。
- 使用足够长度的随机密钥，避免复用 `.env.example` 中的开发默认值。
- 根据实际 AI 网关或模型服务配置 `AI_REVIEW_BASE_URL`。
- 根据机器资源调整 `REVIEW_WORKER_CONCURRENCY`。

## 本地开发

### 后端

```bash
cd acrqg-platform
mvn -B verify
```

常用命令：

```bash
# 仅运行测试
mvn -B test

# 运行完整校验，包含 Flyway、测试和 JaCoCo check
mvn -B verify

# 本地启动 API
mvn spring-boot:run
```

### 前端

```bash
cd acrqg-web
npm ci
npm run dev
```

常用命令：

```bash
npm run build
npm run lint
npm run test:unit -- --run
```

开发服务器默认通过 Vite proxy 将 `/api` 转发到 `http://localhost:8080`。

## 测试与质量保障

### 测试命令

| 类别 | 命令 | 说明 |
|---|---|---|
| 后端单元 + 集成测试 | `cd acrqg-platform && mvn -B test` | 包含 JUnit / Mockito / Spring 测试 |
| 后端完整校验 | `cd acrqg-platform && mvn -B verify` | 包含 Flyway、测试和 JaCoCo 门槛 |
| 属性测试（PBT） | `cd acrqg-platform && mvn -B test -Dtest='*PropertyTest'` | jqwik 属性测试 P1 ~ P8 |
| 越权矩阵 | `cd acrqg-platform && mvn -B test -Dtest=AuthorizationMatrixIT` | 权限边界验证 |
| 端到端 Smoke | `cd acrqg-platform && mvn -B test -Dtest=EndToEndSmokeIT` | SD-1 ~ SD-6 |
| 前端单元测试 | `cd acrqg-web && npm run test:unit -- --run` | Vitest |
| 容器化冒烟 | `bash scripts/smoke.sh` | docker-compose 启动后的关键 endpoint 检查 |

### 覆盖率门槛

后端通过 JaCoCo 执行覆盖率校验：

- Instruction coverage ≥ 70%
- Line coverage ≥ 70%

CI 会上传 JaCoCo 报告和测试报告 artifact，便于追踪未覆盖类与失败用例。

### 性能基准

项目提供 k6 脚本用于报告查询、趋势看板和任务流水线压测：

```bash
TS=$(date +%Y%m%d_%H%M%S)

k6 run -e TIMESTAMP=$TS -e TASK_ID_LIST=101,102,...,200 perf/report-query.js
k6 run -e TIMESTAMP=$TS -e PROJECT_ID_LIST=1,2,3 perf/dashboard-trend.js
k6 run -e TIMESTAMP=$TS -e PROJECT_ID=1 -e WEBHOOK_SECRET=mock-secret perf/task-pipeline.js
```

IT-6 验收要求报告查询 P95 ≤ 2s。详见 [docs/it6-verification-report.md](./docs/it6-verification-report.md)。

## 模块划分

| 模块 | 名称 | 主要职责 |
|---|---|---|
| M01 | Auth + Audit | 登录、JWT 黑名单、用户管理、append-only 审计 |
| M02 | Project + RepositoryBinding | 项目、成员、仓库绑定、token 加密 |
| M03 | ReviewTask + Webhook | 评审任务状态机、Redis Stream 消费、webhook 签名与幂等 |
| M04 | Diff | PR/MR 差异拉取、unified diff 解析 |
| M05 | Static Scanner | Checkstyle / ESLint / Pylint / Semgrep 适配与 severity 归一化 |
| M06 | AI Review | AI 客户端、SensitiveFilter、JSON Schema、降级策略 |
| M07 | Quality Gate + Waiver | 门禁规则、指标 collector、规则引擎、豁免审批 |
| M08 | Issue + Report + Dashboard | 问题状态流转、报告聚合、项目趋势看板 |
| M09 | Notification + Writeback | 站内通知、commit status 回写 |
| M10 | System Admin | 模型、扫描器、系统参数管理，敏感参数加密 |
| M11 | Quality Metric Reports | JaCoCo CSV 覆盖率、PMD-CPD XML 重复率真实接入 |

## 集成验证节点

| 节点 | 触发批次 | 必须通过 |
|---|---|---|
| IT-1 | B0 完成 | docker-compose 启动；`/health=UP`；JaCoCo ≥ 70% |
| IT-2 | B1 完成 | 登录鉴权、用户、项目、审计、系统管理端到端；越权 6 类用例 |
| IT-3 | B2 完成 | 仓库绑定连通性与加密；门禁规则 6×5×2 配置 |
| IT-4 | B3 完成 | SD-1 ~ SD-4；AI 三类降级；PBT P1/P3/P4/P5/P7 |
| IT-5 | B4 完成 | 报告、看板、通知、回写、豁免；SD-5；PBT P2 |
| IT-6 | B6 完成 | k6 P95 ≤ 2s；越权矩阵；PBT 8 条全通过；JaCoCo ≥ 70% |

## 文档导航

| 文档 | 说明 |
|---|---|
| [README_EN.md](./README_EN.md) | English README |
| [docs/architecture.md](./docs/architecture.md) | 系统架构、模块拆分、分支与集成策略、目录结构 |
| [docs/frontend.md](./docs/frontend.md) | 前端状态管理、组件契约、错误码映射、路由权限矩阵 |
| [docs/openapi-baseline.json](./docs/openapi-baseline.json) | OpenAPI 契约基线，由 `OpenApiContractIT` 校验 |
| [docs/it6-verification-report.md](./docs/it6-verification-report.md) | IT-6 集成验证手册 |
| [CHANGELOG.md](./CHANGELOG.md) | 按批次记录的变更日志 |

## 开发规范

### 分支模型

当前仓库已合并功能分支至 `main`。继续开发时建议使用以下约定：

- `main`：保持可构建、可部署。
- `develop`：集成分支，每个 Integration Node 通过后合入。
- `feat/<module-id>-<short-name>`：功能分支，例如 `feat/m06-ai-review`。
- `fix/<scope>-<short-name>`：缺陷修复分支。
- `chore/<scope>-<short-name>`：工程配置、文档或维护任务。

### 数据库迁移

DDL 使用 Flyway 管理，命名格式：

```text
V{seq}__{module}_{purpose}.sql
```

建议序号区段：

| 批次 | 范围 |
|---|---|
| B0 | V1 ~ V9 |
| B1 | V10 ~ V19 |
| B2 | V20 ~ V29 |
| B3 | V30 ~ V49 |
| B4 | V50 ~ V69 |
| M11 | V13 |

### 提交前检查

提交代码前建议执行：

```bash
cd acrqg-platform && mvn -B verify
cd ../acrqg-web && npm ci && npm run build && npm run test:unit -- --run
```

## 项目状态

- 当前主线：`main`
- 发布版本：`v1.0.0`
- 项目性质：课程项目 / 毕业设计原型

## License

本项目仅作为课程项目 / 毕业设计原型使用。若需开源发布或商业使用，请先补充正式许可证文件。
