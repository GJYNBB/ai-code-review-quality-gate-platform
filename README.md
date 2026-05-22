# AI 辅助代码评审与质量门禁平台

> Software 名称：**ai-code-review-quality-gate-platform**
> 一站式的 AI 辅助代码评审 + 质量门禁平台：在开发者提交代码或创建 PR/MR 后，自动拉取代码差异，执行静态扫描、AI 辅助评审、质量指标汇总和质量门禁判定，并生成结构化评审报告，将门禁结果回写到代码平台。

## 文档导航

| 文档 | 作用 |
|---|---|
| [docs/architecture.md](./docs/architecture.md) | 系统架构、模块拆分、分支与集成策略、目录结构 |
| [docs/frontend.md](./docs/frontend.md) | 前端状态管理、组件契约、错误码映射、路由权限矩阵 |
| [docs/openapi-baseline.json](./docs/openapi-baseline.json) | OpenAPI 契约基线，由 `OpenApiContractIT` 校验 |
| [docs/it6-verification-report.md](./docs/it6-verification-report.md) | IT-6 集成验证手册（覆盖率 / k6 / 越权 / PBT / SD / 安全） |
| [acrqg-platform/](./acrqg-platform/) | 后端 Maven 工程 |
| [acrqg-web/](./acrqg-web/) | 前端 Vue 工程 |
| [CHANGELOG.md](./CHANGELOG.md) | 变更记录（按批次） |

## 技术栈

- **后端**：Spring Boot 3.x（Java 17）+ MyBatis-Plus + Spring Security + Redis Stream（任务队列）
- **前端**：Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router
- **数据库**：PostgreSQL 15
- **缓存 / 队列 / 黑名单 / 幂等**：Redis 7
- **静态扫描器**：Checkstyle / ESLint / Pylint / Semgrep（适配层 `StaticScannerAdapter`）
- **AI 客户端**：兼容 OpenAI Chat Completions + JSON Schema 校验 + SensitiveFilter（路径白名单 + Token 正则 + 哈希前后比对）
- **测试**：JUnit 5 + Mockito + Testcontainers + jqwik（PBT）+ Vitest + k6
- **容器**：Docker + docker-compose

## 仓库结构

```
ai-code-review-quality-gate-platform/
├── acrqg-platform/         # 后端 Maven 单模块（Spring Boot 3 + Java 17）
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/{main,test}/{java,resources}/
├── acrqg-web/              # 前端 Vue 单包（Vue 3 + Vite + TypeScript）
│   ├── package.json
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/{api,components,layouts,pages,router,stores,styles,types}/
├── docker-compose.yml      # postgres + redis + backend + worker + frontend
├── .env.example            # 环境变量模板（复制为 .env）
├── docs/                   # 架构 / 前端 / 契约基线 / IT-6 验收报告
├── perf/                   # k6 性能基准脚本（report-query / dashboard / task-pipeline）
├── scripts/smoke.sh        # docker-compose 冒烟脚本（B6-A.12）
└── .github/workflows/      # GitHub Actions CI 流水线
```

## 模块清单（M01 ~ M11）

| 模块 | 主要职责 |
|---|---|
| M01 Auth + Audit | 登录 / JWT 黑名单 / 用户管理；审计 append-only |
| M02 Project + RepositoryBinding | 项目 / 成员；仓库绑定（GitHub / GitLab / Gitee，token 加密） |
| M03 ReviewTask + Webhook | 评审任务状态机；Redis Stream 消费者；webhook 接入（签名 + 幂等） |
| M04 Diff | 拉取 PR 差异（unified diff 解析） |
| M05 Static Scanner | 4 种扫描器适配 + Severity 归一化 |
| M06 AI Review | OpenAI 兼容客户端；SensitiveFilter；JSON Schema；3 模式降级 |
| M07 Quality Gate + Waiver | 门禁规则 CRUD + 6 collector + 规则引擎 + 豁免审批 |
| M08 Issue + Report + Dashboard | 问题状态流转；报告聚合；项目趋势看板 |
| M09 Notification + Writeback | 站内通知 + commit status 回写 |
| M10 System Admin | 模型 / 扫描器 / 系统参数（敏感参数加密） |
| M11 Quality Metric Reports | TestCoverage（JaCoCo CSV） / DuplicateRate（PMD-CPD XML）真实接入 |

## 模块对应分支模型

> 本仓库已合并所有功能分支至 `main`，发布版本 `v1.0.0`。
> 重新展开二次开发时建议沿用以下命名约定：

- `main`：永远可构建、可部署。
- `develop`：每个 Integration Node（IT-{N}）通过后从对应批次合入。
- 功能分支：`feat/<module-id>-<short-name>`。
- DDL 命名 `V{seq}__{module}_{purpose}.sql`；序号区段：B0=V1~V9、B1=V10~V19、B2=V20~V29、B3=V30~V49、B4=V50~V69、M11=V13。

## 集成验证节点（IT-1 ~ IT-6）

| 节点 | 触发批次 | 必须通过 |
|---|---|---|
| IT-1 | B0 完成 | docker-compose 起来；`/health=UP`；JaCoCo ≥ 70% |
| IT-2 | B1 完成 | 登录鉴权 / 用户 / 项目 / 审计 / 系统管理 端到端；越权 6 类用例 |
| IT-3 | B2 完成 | 仓库绑定（含连通性 + 加密）+ 门禁规则 6×5×2 配置 |
| IT-4 | B3 完成 | SD-1 ~ SD-4 端到端；AI 三类降级；PBT P1/P3/P4/P5/P7 通过 |
| IT-5 | B4 完成 | 报告 / 看板 / 通知 / 回写 + 豁免；SD-5；PBT P2 通过 |
| IT-6 | B6 完成 | k6 报告 P95 ≤ 2s；越权矩阵；PBT 8 条全通过；JaCoCo ≥ 70% |

详见 [docs/it6-verification-report.md](./docs/it6-verification-report.md)。

## 快速开始

### 一键编排（推荐，开箱即用）

```bash
cp .env.example .env          # 编辑 JWT_SECRET / TOKEN_ENCRYPTION_KEY 等
docker compose up -d
bash scripts/smoke.sh         # 冒烟：health → /v3/api-docs → 受保护接口 401
```

种子管理员：`admin / admin`（初始密码哈希通过 `V1__init.sql` 注入，生产环境请立即修改）。

### 本地开发

```bash
# 后端
cd acrqg-platform
mvn -B verify                 # 包含 Flyway 迁移 + JaCoCo check

# 前端
cd acrqg-web
npm ci
npm run dev                   # vite 开发服务器，proxy /api -> http://localhost:8080
```

### 性能基准

```bash
# 启动后准备 100 个任务（通过 admin API 或种子 SQL）
TS=$(date +%Y%m%d_%H%M%S)
k6 run -e TIMESTAMP=$TS -e TASK_ID_LIST=101,102,...,200 perf/report-query.js
k6 run -e TIMESTAMP=$TS -e PROJECT_ID_LIST=1,2,3 perf/dashboard-trend.js
k6 run -e TIMESTAMP=$TS -e PROJECT_ID=1 -e WEBHOOK_SECRET=mock-secret perf/task-pipeline.js
```

## 测试

| 类别 | 命令 | 备注 |
|---|---|---|
| 单元 + 集成 | `mvn -pl acrqg-platform test` | 包含 IT 类（需 Docker for Testcontainers） |
| 属性测试（PBT） | `mvn -pl acrqg-platform test -Dtest='*PropertyTest'` | 8 条属性 P1 ~ P8 |
| 越权矩阵 | `mvn -pl acrqg-platform test -Dtest=AuthorizationMatrixIT` | 8 行 design §15.6 用例 |
| 端到端 Smoke | `mvn -pl acrqg-platform test -Dtest=EndToEndSmokeIT` | SD-1 ~ SD-6 |
| 前端 | `cd acrqg-web && npm run test:unit -- --run` | Vitest（仅当编写了对应测试） |
| 容器化冒烟 | `bash scripts/smoke.sh` | docker-compose 起 + 关键 endpoint |

## License

仅作为课程项目 / 毕业设计原型。
