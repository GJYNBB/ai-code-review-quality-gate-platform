# AI 辅助代码评审与质量门禁平台

> Software 名称：**ai-code-review-quality-gate-platform**
> 一站式的 AI 辅助代码评审 + 质量门禁平台：在开发者提交代码或创建 PR/MR 后，自动拉取代码差异，执行静态扫描、AI 辅助评审、质量指标汇总和质量门禁判定，并生成结构化评审报告，将门禁结果回写到代码平台。

## 文档导航

本仓库采用 **Spec-Driven Development**：所有开发活动以 `.kiro/specs/ai-code-review-quality-gate-platform/` 下的三份基线文档为依据，禁止脱离规约直接编码。

| 文档 | 作用 |
|---|---|
| [requirements.md](./.kiro/specs/ai-code-review-quality-gate-platform/requirements.md) | 25 条 EARS 需求，覆盖 M01 ~ M10 全部业务模块及非功能性约束 |
| [design.md](./.kiro/specs/ai-code-review-quality-gate-platform/design.md) | 20 节技术设计：架构 / DDL / 接口 / 序列图 / 安全 / 测试 / 部署 / 分支拆分 |
| [tasks.md](./.kiro/specs/ai-code-review-quality-gate-platform/tasks.md) | 6 批次 / 22 分支 / ~145 子任务的可执行实现计划，含 PBT 属性映射 |

工作区另保留 4 份原始设计稿（位于工作区根目录的 `.docx` 与 `页面原型_prototype.html`）作为输入参考；它们已通过 spec 文档完整翻译为工程基线，开发时优先以 spec 为准。

## 技术栈（设计基线）

- **后端**：Spring Boot 3.x（Java 17） + MyBatis-Plus + Spring Security + Redis Stream（任务队列）
- **前端**：Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router
- **数据库**：PostgreSQL 15
- **缓存 / 队列 / 黑名单 / 幂等**：Redis 7
- **静态扫描器**：Checkstyle / ESLint / Pylint / Semgrep（适配层 `StaticScannerAdapter`）
- **AI 客户端**：兼容 OpenAI Chat Completions + JSON Schema 校验 + SensitiveFilter（路径白名单 + Token 正则 + 哈希前后比对）
- **测试**：JUnit 5 + Mockito + Testcontainers + jqwik（PBT）+ Vitest + k6
- **容器**：Docker + docker-compose（dev / 默认 prod 共用）

## 仓库结构（实施阶段填充）

```
ai-code-review-quality-gate-platform/
├── .kiro/specs/ai-code-review-quality-gate-platform/   # Spec 基线（requirements / design / tasks）
├── acrqg-platform/                                      # 后端 Maven 工程（B0-A 起注入）
├── acrqg-web/                                           # 前端 Vue 工程（B0-B 起注入）
├── docker-compose.yml                                   # 本地一键编排（B0-A 注入）
├── docs/                                                # 架构文档、契约 baseline、IT-6 验收报告
├── perf/                                                # k6 性能基准脚本（B6 注入）
└── .github/workflows/                                   # GitHub Actions CI（B0-A 注入）
```

## 分支模型

- `main`：永远可构建、可部署。
- `develop`：每个 Integration Node（IT-{N}）通过后从对应批次合入。
- 功能分支：`feat/<module-id>-<short-name>`，每个 subagent 一条；具体清单见 tasks.md §批次总览。
- 共享文件（pom.xml 顶层依赖、application.yml 全局段、router/index.ts、db/migration 序号）通过批次末尾 `chore/wire-batch-{N}` 集成 PR 合并。
- DDL 命名 `V{seq}__{module}_{purpose}.sql`；序号区段：B0=V1~V9、B1=V10~V19、B2=V20~V29、B3=V30~V49、B4=V50~V69。

## 集成节点（IT-1 ~ IT-6）

| 节点 | 触发批次 | 必须通过 |
|---|---|---|
| IT-1 | B0 完成 | docker-compose 起来；`/health=UP`；JaCoCo ≥ 70% |
| IT-2 | B1 完成 | 登录鉴权 / 用户 / 项目 / 审计 / 系统管理 端到端；越权 6 类用例 |
| IT-3 | B2 完成 | 仓库绑定（含连通性 + 加密）+ 门禁规则 6×5×2 配置 |
| IT-4 | B3 完成 | SD-1 ~ SD-4 端到端；AI 三类降级；PBT P1/P3/P4/P5/P7 通过 |
| IT-5 | B4 完成 | 报告 / 看板 / 通知 / 回写 + 豁免；SD-5；PBT P2 通过 |
| IT-6 | B6 完成 | k6 报告 P95 ≤ 2s；越权矩阵；PBT 8 条全通过；JaCoCo ≥ 70% |

## 快速开始（实施阶段后）

```powershell
# 后端
cd acrqg-platform
mvn -B verify

# 前端
cd acrqg-web
npm ci
npm run dev

# 一键编排
docker compose up -d
```

## License

仅作为课程项目 / 毕业设计原型。
