# 架构总览（团队入口）

> 本文档是新成员加入项目时的第一站。它给出系统全景、目录结构、本地上手路径与常见排查清单。需求 / 设计的细节追踪以代码注释 + Javadoc + 本目录其它文档为准。

---

## 1. 文档导航（Documents）

| 文档 | 作用 |
|---|---|
| [README.md](../README.md) | 项目入口、快速开始、模块清单 |
| [docs/frontend.md](./frontend.md) | 前端架构（状态切片 / 组件契约 / 错误码 / 路由权限） |
| [docs/openapi-baseline.json](./openapi-baseline.json) | OpenAPI 契约基线，由 `OpenApiContractIT` 校验 |
| [docs/it6-verification-report.md](./it6-verification-report.md) | IT-6 集成验证手册（覆盖率 / k6 / 越权 / PBT / SD / 安全） |
| [CHANGELOG.md](../CHANGELOG.md) | 变更记录（按批次） |

> 模块设计与状态机的源代码注释（Javadoc / 中文注释）是真实的"设计权威"；本文档仅给出路径与入口。

---

## 2. 系统全景（System at a Glance）

```mermaid
flowchart LR
  subgraph External["外部系统"]
    CodePlat["Code Platform<br/>GitHub / GitLab / Gitee"]
    AIService["AI Review Service<br/>大模型 HTTP API"]
    Browser["Web Browser"]
  end

  subgraph Platform["AI 代码评审与质量门禁平台"]
    Web["Frontend SPA<br/>Vue 3 + Vite + Element Plus"]
    API["Backend API<br/>Spring Boot 3.x"]
    Worker["Task Worker<br/>同 jar，profile=worker"]
    DB[("PostgreSQL 15+")]
    Cache[("Redis 7<br/>Stream / 黑名单 / 幂等")]
  end

  Browser -->|HTTPS| Web
  Web -->|REST /api/v1| API
  CodePlat -->|Webhook| API
  API -->|JDBC| DB
  API -->|XADD| Cache
  Worker -->|XREADGROUP| Cache
  Worker -->|JDBC| DB
  Worker -->|HTTP| AIService
  Worker -->|commit status| CodePlat
```

要点：

- **单 jar、双 profile**：`api` 提供 HTTP，`worker` 监听 Redis Stream。两者共享同一数据源与加密密钥。
- **Worker 水平扩展**：消费组 `review-worker-group` 支持多实例负载均衡，`XCLAIM` 实现中断恢复（R24.4）。
- **单向依赖**：`common` / `infra` 是基础层；M01~M11 业务包按"上层依赖下层"原则组织，禁止反向依赖。

---

## 3. 仓库布局（Repository Layout）

```
ai-code-review-quality-gate-platform/
├── .github/workflows/         # CI 流水线
├── acrqg-platform/            # 后端 Maven 单模块（Spring Boot 3）
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/acrqg/platform/
│       │   ├── AcrqgApplication.java
│       │   ├── common/        # 响应 / 错误码 / 异常 / 工具
│       │   ├── infra/         # JWT / 加密 / Redis / 切面 / Web 配置
│       │   ├── auth/ user/ audit/ project/ admin/ repository/
│       │   ├── webhook/ task/ diff/ scanner/ ai/ gate/
│       │   ├── code_issue/ report/ dashboard/ notification/ writeback/
│       └── main/resources/
│           ├── application*.yml
│           ├── db/migration/  # Flyway V{seq}__{module}_{purpose}.sql
│           └── mapper/        # MyBatis-Plus XML
├── acrqg-web/                 # 前端 Vue 3 + Vite + TS
├── docs/                      # 团队文档（本目录）
├── perf/                      # k6 性能基准脚本
├── scripts/smoke.sh           # docker-compose 冒烟脚本
├── docker-compose.yml         # 一键拉起 postgres / redis / backend / worker / nginx
├── .env.example
├── CHANGELOG.md
└── README.md
```

> Migration 文件命名约定：`V{seq}__{module}_{purpose}.sql`。已分配的序号区段：B0=V1~V9、B1=V10~V19、B2=V20~V29、B3=V30~V49、B4=V50~V69、M11=V13。

---

## 4. 分支与集成策略（Branch & Integration Policy）

> 本仓库已合并所有功能分支至 `main` 并发布 `v1.0.0`。下述规则适用于二次开发。

- **批次内并行**：同批次的顶层任务由不同开发者在各自 `feat/*` / `chore/*` 分支并行开发。
- **批次间串行**：进入下一批次前，本批次必须全部合入 `develop` 并通过 Integration Node IT-x 验证（端到端冒烟 + 越权用例集 + JaCoCo ≥ 70% + jqwik 属性测试）。
- **共享文件冲突治理**：`pom.xml` 顶层依赖段、`application.yml` 全局段、`router/index.ts`、`stores/index.ts`、Flyway 序号在批次集成 PR `chore/wire-batch-{N}` 中由 reviewer 人工合并。
- **保护分支**：`main` 仅接受来自 `develop` 的 release PR；`develop` 只接受批次集成 PR；功能分支永远基于最新 `develop` 切出。
- **属性测试映射**：8 条 PBT 属性已分配到 B1-A / B1-B / B3-A / B3-C / B3-E / B3-F / B4-A 各自的 `property/` 测试包，禁止在其它分支重复实现。

---

## 5. 本地快速上手（Local Quickstart）

### 5.1 前置依赖

- JDK 17（推荐 Eclipse Temurin）
- Maven 3.9+
- Node.js 20 LTS
- Docker Desktop / Docker Engine + docker compose v2

### 5.2 一键启动整套依赖

```bash
cp .env.example .env
docker compose up -d postgres redis
```

这会启动 `postgres:15` 与 `redis:7`，端口分别映射到 `5432` / `6379`。

### 5.3 后端开发循环

```bash
cd acrqg-platform
mvn -B verify                        # 运行单元 + 集成测试 + JaCoCo 覆盖率检查
mvn -pl . spring-boot:run            # 默认 dev + api profile，监听 8080
```

健康检查：

```bash
curl -s http://localhost:8080/health        # 期望 {"status":"UP"}
curl -s http://localhost:8080/v3/api-docs   # OpenAPI 当前 schema
```

切换到 worker profile：

```bash
SPRING_PROFILES_ACTIVE=worker,dev mvn -pl . spring-boot:run
```

### 5.4 前端开发循环

```bash
cd acrqg-web
npm ci
npm run dev                          # Vite 默认 5173，/api 反代到 :8080
npm run test:unit -- --run
npm run lint
npm run build
```

### 5.5 整套服务

```bash
docker compose up -d
docker compose logs -f backend worker
bash scripts/smoke.sh                # 冒烟：health → /v3/api-docs → 受保护接口 401
```

停止与清理：

```bash
docker compose down                  # 保留数据卷
docker compose down -v               # 清空 postgres / redis 数据
```

### 5.6 OpenAPI 基线刷新仪式

每次合入新增 `@RestController` 的批次后，由集成 PR 执行：

```bash
docker compose up -d backend
curl -s http://localhost:8080/v3/api-docs | python -m json.tool > docs/openapi-baseline.json
git add docs/openapi-baseline.json
git commit -m "docs(openapi): refresh baseline after B{N} 集成"
```

`OpenApiContractIT` 会以 `docs/openapi-baseline.json` 为 oracle 与运行期 `/v3/api-docs` 进行 diff 校验。

---

## 6. 常见问题速查

| 现象 | 排查首选 |
|---|---|
| 启动报 `Flyway` 校验失败 | 检查 `db/migration/` 序号段是否冲突；分支合并到 `develop` 前应连续无重号 |
| 响应中出现明文 `apiKey` / `accessToken` | `infra.log.ResponseMaskingAspect` 字段名匹配规则未覆盖；按 R23.3 在切面字段集合中补充 |
| `/auth/me` 返回 401 | JWT 黑名单命中；查 `jwt:bl:jti:{jti}` 是否存在；禁用用户会触发即时拉黑（R3.2） |
| Worker 不消费消息 | `XLEN review-task-stream` 是否为 0；`XINFO GROUPS review-task-stream` 检查消费组与 pending |
| CI 覆盖率门槛失败 | 本地 `mvn -B verify` 后查看 `target/site/jacoco/index.html`；R25.1 要求语句覆盖率 ≥ 70% |
| `test_coverage` / `duplicate_rate` 为 placeholder | Worker / CI 需把 `jacoco.csv` 写到 `reports/coverage/task-{taskId}/`，把 `cpd.xml + total-loc.txt` 写到 `reports/cpd/task-{taskId}/`；基目录可由 `system_param.gate.*.report.dir` 热更新（V13） |

---

## 7. 文档维护

- 任何文档变更请同步更新 [CHANGELOG.md](../CHANGELOG.md) 中的 `[Unreleased]` 段。
- 本文档与代码注释发生冲突时，以代码注释为准。
