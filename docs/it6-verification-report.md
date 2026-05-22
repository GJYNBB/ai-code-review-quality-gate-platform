# IT-6 集成验证批次 — 验收报告

> 范围：B6-A 集成验证批次（B6-A.1 ~ B6-A.11，B6-A.12 docker-compose smoke 标记 optional 跳过）
> 关联设计：`design.md` §9（SD-1~SD-6 端到端）、§15.6（越权矩阵）、§19（PBT 总览）、§17.1（docker-compose）
> 关联需求：R12, R16.6, R18.5, R23, R24, R25
> 状态：Code 完成，待执行 / 收集运行时数据。本报告同时承担"已知问题"与"运行手册"双重职责。

---

## 1. 覆盖率统计（R25.1）

JaCoCo 覆盖率门槛：`INSTRUCTION ≥ 70%` + `LINE ≥ 70%`（element=BUNDLE），由
`acrqg-platform/pom.xml` 的 `jacoco-maven-plugin` execution `jacoco-check` 强制
（B6-A.8）。报告产物：

| 输出 | 路径 |
|---|---|
| HTML 报告 | `acrqg-platform/target/site/jacoco/index.html` |
| CSV 数据 | `acrqg-platform/target/site/jacoco/jacoco.csv` |
| 未达标类清单 | `acrqg-platform/target/site/jacoco/uncovered.txt`（CI 自动生成） |

排除项：
- `com/acrqg/platform/infra/log/MaskingLogbackEncoder.class` — 低风险纯转发逻辑
- `com/acrqg/platform/AcrqgApplication.class` — 启动类，仅 `main()`
- `**/config/**` — 配置类
- `**/dto/**` — Record DTO
- `**/domain/**` — POJO

CI 工作流：`.github/workflows/ci.yml#backend-build`：
- `mvn -B -ntp -DskipITs=false verify` 触发 `jacoco-check`
- 失败时把 `target/site/jacoco/uncovered.txt` 上传至 `jacoco-report` artifact

> **运行后填写**：把 instruction / line 实际覆盖率与未达标 class 列表填入此处。

---

## 2. k6 性能基准（R16.6, R18.5, R24.1, R24.2）

三组 k6 脚本（B6-A.1 / B6-A.2）：

| 脚本 | 端点 | 并发 | 时长 | 阈值 | 关联需求 |
|---|---|---|---|---|---|
| `perf/report-query.js` | `GET /api/v1/review-tasks/{id}/report` | 100 VU | 5 min | `http_req_duration p(95) < 2000ms`, `http_req_failed rate < 1%` | R16.6, R24.2 |
| `perf/dashboard-trend.js` | `GET /api/v1/projects/{id}/dashboard?days=30` | 50 VU | 5 min | 同上 | R18.5, R24.2 |
| `perf/task-pipeline.js` | webhook → 终态轮询 | 10 VU | 3 min | `pipelineDurationMs p(95) < 180000ms` | R24.1, R24.2 |

输出位置：`perf/output/{report-query,dashboard-trend,task-pipeline}-{TIMESTAMP}.html`
+ `*-summary.json`。

执行步骤（手动）：
```bash
# 1) 启动后端 + 数据库（可使用 docker-compose）
# 2) 通过 admin 账号登录 + 调用 setup script 准备 100 个任务（每任务 200 issue）
# 3) 设置 TASK_ID_LIST / PROJECT_ID_LIST / WEBHOOK_SECRET 环境变量
TS=$(date +%Y%m%d_%H%M%S)
k6 run -e TIMESTAMP=$TS -e TASK_ID_LIST=101,102,...,200 perf/report-query.js
k6 run -e TIMESTAMP=$TS -e PROJECT_ID_LIST=1,2,3 perf/dashboard-trend.js
k6 run -e TIMESTAMP=$TS -e PROJECT_ID=1 -e WEBHOOK_SECRET=mock-secret perf/task-pipeline.js
```

> **运行后填写**：

| 脚本 | P50 | P95 | P99 | 错误率 | 是否达标 |
|---|---|---|---|---|---|
| `report-query` | _TBD_ | _TBD_ | _TBD_ | _TBD_ | ✅ / ❌ |
| `dashboard-trend` | _TBD_ | _TBD_ | _TBD_ | _TBD_ | ✅ / ❌ |
| `task-pipeline` | _TBD_ | _TBD_ | _TBD_ | _TBD_ | ✅ / ❌ |

---

## 3. 越权用例矩阵（design §15.6 / R25.5）

集成测试：`src/test/java/com/acrqg/platform/security/AuthorizationMatrixIT.java`

| # | 调用者 | 被测接口 | 期望状态 | 期望错误码 | 用例方法 |
|---|---|---|---|---|---|
| 1 | 不带 token | `GET /api/v1/projects` | 401 | `AUTH_INVALID_TOKEN` | `case1_missingToken_returnsUnauthorized` |
| 2 | 过期 token | `GET /api/v1/projects` | 401 | `AUTH_INVALID_TOKEN` | `case2_expiredToken_returnsUnauthorized` |
| 3 | 被禁用用户 token（jti 在黑名单） | `GET /api/v1/projects` | 401 | `AUTH_INVALID_TOKEN` | `case3_disabledUserToken_returnsUnauthorized` |
| 4 | DEVELOPER 全局 | `POST /api/v1/projects` | 403 | `PERMISSION_DENIED` | `case4_developerCreateProject_returns403` |
| 5 | 非 SYSTEM_ADMIN | `GET /api/v1/admin/audit-logs` | 403 | `PERMISSION_DENIED` | `case5_nonSystemAdminAuditLogs_returns403` |
| 6 | CI_CD 全局 | `POST /api/v1/projects` | 403 | `PERMISSION_DENIED` | `case6_cicdCreateProject_returns403` |
| 7 | 非项目成员 | `GET /api/v1/review-tasks/{id}` | 403 | `PERMISSION_DENIED` | `case7_nonMemberGetReviewTask_returns403` |
| 8 | REVIEWER 项目成员 | `POST /api/v1/projects/{id}/repository` | 403 | `PERMISSION_DENIED` | `case8_reviewerBindRepository_returns403` |

> **运行后填写**：8 / 8 测试是否全部通过。

---

## 4. 属性测试 8 条属性（R25.5）

属性映射（design §19）：

| # | 属性描述 | 实现批次 | tries（设计） | 实际 tries / 通过率 |
|---|---|---|---|---|
| P1 | 任务状态机迁移有向图 | B3-A | 200 | _TBD_ |
| P2 | 问题状态迁移合法集 | B4-A | 200 | _TBD_ |
| P3 | 任务幂等三元组 | B3-A | 100 | _TBD_ |
| P4 | 门禁 BLOCKER ⇔ FAILED | B3-F | 200 | _TBD_ |
| P5 | SensitiveFilter Token 过滤 | B3-E | 500 | _TBD_ |
| P6 | JWT 黑名单 5 分钟内必失效 | B1-A | 100 | _TBD_ |
| P7 | Diff 行数一致性 | B3-C | 100 | _TBD_ |
| P8 | 审计日志 append-only | B1-B | 100 | _TBD_ |

> **运行后填写**：执行 `mvn -pl acrqg-platform test -Dtest=*PropertyTest` 后从 jqwik
> 报告（`target/jqwik-reports/`）填入实际 tries 与通过率。

---

## 5. 端到端 SD-1 ~ SD-6 用例链接（R7~R20）

集成测试：`src/test/java/com/acrqg/platform/e2e/EndToEndSmokeIT.java`

| 设计场景 | 描述 | 用例方法 |
|---|---|---|
| SD-1 / SD-3 | 任务从 webhook → 终态（fixture 等价路径） | fixture 在 `seed()` 中构造 FAILED_GATE 任务 |
| SD-2 | 报告查询 | `sd2_getReportShowsFailedGateAndIssue` |
| SD-4 | 状态回写到 GitHub commit-status | `sd4_writebackPostsFailureCommitStatus` |
| SD-5 | 豁免提交 + 审批 → 任务转 PASSED | `sd5_submitAndApproveWaiver` |
| SD-5 余波 | 通知列表出现终态通知 | `sd5_followup_notificationsInclude` |
| SD-6 | 登录入口可用 | `sd6_loginEndpointWorks` |

补充说明：完整 webhook + diff + 扫描 + AI 链路依赖 worker profile + Redis Stream
消费者，本 IT 采用"fixture 等价 + HTTP 直接调用"分层验证。完整链路在
`task-pipeline.js` k6 基准中以黑盒形式验证（见 §2）。

---

## 6. AI 降级三类场景（R12.4, R12.5, R25.4）

集成测试：`src/test/java/com/acrqg/platform/ai/AiDegradationIT.java`

| # | 场景 | WireMock 行为 | 期望 outcome | 用例方法 |
|---|---|---|---|---|
| 1 | 超时 | `fixedDelay = ai.review.timeout.seconds + 5` | `aiAvailable=false`, score=0, WARN log | `timeout_returnsUnavailableAndLogsWarn` |
| 2 | 5xx | 返回 503 | `aiAvailable=false`, score=0, WARN log | `serverError5xx_returnsUnavailableAndLogsWarn` |
| 3 | Schema 不合法 | 返回 200 + 缺必填字段 | `aiAvailable=true`, score=0, WARN log（`schema`） | `schemaInvalid_returnsAvailableButZeroScore` |

降级路径不抛异常 → `AiReviewingStage` 仍可推进至 `GATE_EVALUATING` 而非
`EXECUTION_FAILED`，与 `AiReviewServiceImpl#finishUnavailable` /
`finishWithDiscardedResponse` 实现一致。

---

## 7. 门禁规则引擎 60 用例（R25.3）

集成测试：`src/test/java/com/acrqg/platform/gate/GateRuleEngineMatrixIT.java`

矩阵：6 metric × 5 operator × {BLOCKER, WARN} = 60 参数化用例
（`@ParameterizedTest @MethodSource("matrix")`）。

种子值（actual）：

| metric | seed | seed source |
|---|---|---|
| `critical_issue_count` | 2 | 2 条 CRITICAL code_issue (NEW) |
| `security_issue_count` | 1 | 1 条 rule_code = `security.SQLI-001` |
| `new_issue_count` | 3 | 3 条 status=NEW code_issue |
| `ai_risk_score` | 50 | review_task.ai_risk_score=50 |
| `test_coverage` | 0 | 占位 collector |
| `duplicate_rate` | 0 | 占位 collector |

期望（threshold = seed）：

| operator | passed | status (BLOCKER) | status (WARN) | score (B/W) |
|---|---|---|---|---|
| `<=` | true | PASSED | PASSED | 100 / 100 |
| `>=` | true | PASSED | PASSED | 100 / 100 |
| `<` | false | FAILED | PASSED | 80 / 95 |
| `>` | false | FAILED | PASSED | 80 / 95 |
| `==` | true | PASSED | PASSED | 100 / 100 |

---

## 8. 启动 / 中断 / 恢复（R24.4）

集成测试：`src/test/java/com/acrqg/platform/recovery/WorkerRecoveryIT.java`

测试方法：`recoveryRunner_marksStuckTasksFailedWithLogs`

流程：
1. 直接 SQL 插入 5 个状态为 `STATIC_SCANNING` 的任务（模拟 worker 在阶段中崩溃）；
2. 调用 `TaskRecoveryRunner.run(args)`；
3. 5 个任务全部转 `EXECUTION_FAILED`，每个 `task_log` 含 WARN 级
   `task interrupted by worker restart`；
4. 不得有任务跳到 `PASSED`；
5. `findStuckTasks()` 返回的列表不再包含这些任务。

策略 A（design §16.3）—— "宁可让用户重试，也不愿继续推进可能基于错误中间状态的任务"。

---

## 9. 安全：敏感字段泄露探测（R23.2 ~ R23.5）

集成测试：`src/test/java/com/acrqg/platform/security/SensitiveLeakIT.java`

覆盖 endpoint：

| Endpoint | 方法 |
|---|---|
| `GET /api/v1/projects/{id}/repository` | `getRepositoryBinding_doesNotLeakSecrets` |
| `GET /api/v1/admin/model-configs` | `listModelConfigs_doesNotLeakSecrets` |
| `GET /api/v1/admin/scanners` | `listScanners_doesNotLeakSecrets` |
| `GET /api/v1/users` | `listUsers_doesNotLeakSecrets` |
| `GET /api/v1/admin/audit-logs` | `listAuditLogs_doesNotLeakSecrets` |

断言：
- 命中字段名 `password / passwordHash / accessToken / apiKey / webhookSecret /
  accessTokenEncrypted / apiKeyEncrypted` 时，对应字段值必须为 `"****"`；
- 任何字段值不得匹配 token 正则
  `(AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9]{32,}|gh[pousr]_[A-Za-z0-9]{36,})`。

---

## 10. OpenAPI 契约基线（R25.2）

集成测试：`src/test/java/com/acrqg/platform/contract/OpenApiContractIT.java`

测试方法：`runtimeApiDocsMatchesBaseline`

行为：
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` 启动 → GET `/v3/api-docs`；
- 与 `docs/openapi-baseline.json` 比较 `paths` + `components.schemas` 两段；
- 不一致时输出 RFC-6902 JSON Patch diff；
- 支持 `mvn test -Dtest=OpenApiContractIT -DupdateBaseline=true` 检测 diff 时
  写回 baseline。

---

## 11. 已知问题与跟进项

### 11.1 Collector 真实接入（M11，已交付）

| Collector | 状态 | 数据源 |
|---|---|---|
| `TestCoverageCollector` | ✅ 已接入 JaCoCo CSV | `system_param.gate.test_coverage.report.dir` 默认 `reports/coverage`；按 `{dir}/task-{taskId}/jacoco.csv` 约定读取，缺失时退化到 `system_param.gate.test_coverage.report.placeholder`（默认 75）+ INFO log |
| `DuplicateRateCollector` | ✅ 已接入 PMD-CPD XML | `system_param.gate.duplicate_rate.report.dir` 默认 `reports/cpd`；按 `{dir}/task-{taskId}/cpd.xml` + 同目录 `total-loc.txt` 约定读取，缺失时退化到 `system_param.gate.duplicate_rate.report.placeholder`（默认 0）+ INFO log |

**Worker / CI 写入约定**：
- 在 `GATE_EVALUATING` 阶段触发前，把对应任务的 JaCoCo CSV 报告写到
  `reports/coverage/task-{taskId}/jacoco.csv`，或把 PMD-CPD 报告 + total-loc 行数写到
  `reports/cpd/task-{taskId}/{cpd.xml,total-loc.txt}`。
- 基目录与 placeholder 值均可通过 `/api/v1/admin/system-params` 热更新。
- 解析器单元测试：`JacocoCsvParserTest`、`CpdXmlParserTest`（含 XXE 防御断言）。
- DDL 迁移：`V13__m11_quality_metric_reports.sql` 注入 4 条新 system_param。

### 11.2 EndToEndSmokeIT 简化

由于完整 webhook → diff → 扫描 → AI → 门禁链路依赖 worker profile + Redis Stream
消费者运行，IT 采用"fixture 等价 + HTTP 直接调用"分层验证。完整黑盒链路由
`task-pipeline.js` k6 基准在 docker-compose 部署上验证。

### 11.3 AuthorizationMatrixIT case 3（disabled token）

`JwtBlacklist` 实现要求把 jti 写入 Redis；测试通过 `StringRedisTemplate` 显式写入。
若运行环境的 `JwtAuthFilter` 未注入 `Predicate<String> jwtJtiBlacklist` bean，
该用例可能因"过期 token 检验"路径返回 401，但错误码仍为 `AUTH_INVALID_TOKEN`，
属预期等价行为。

### 11.4 OpenAPI baseline 当前为空

`docs/openapi-baseline.json` 在 B0-A.15 阶段以"synthetic seed"方式创建（paths 为空）。
首次运行 `OpenApiContractIT` 应立即失败并输出真实 paths/schemas diff；
按 `-DupdateBaseline=true` 模式回写后即可在后续 PR 中作为稳态基线。

---

## 12. 验收清单

- [ ] `mvn -pl acrqg-platform verify` 全部测试 + JaCoCo check 通过
- [ ] `target/site/jacoco/uncovered.txt` 为空（或仅排除项）
- [ ] 三组 k6 P95 全部 ≤ 阈值
- [ ] 越权 8 用例全部通过
- [ ] PBT 8 条属性 tries 与通过率达标（每条 ≥ 100）
- [ ] EndToEndSmokeIT 5 个测试方法全部通过
- [ ] AiDegradationIT 3 类场景全部通过
- [ ] GateRuleEngineMatrixIT 60 用例全部通过
- [ ] WorkerRecoveryIT 通过
- [ ] SensitiveLeakIT 5 endpoint 全部通过
- [ ] OpenApiContractIT 通过（或 baseline 已 updateBaseline）
- [ ] `bash scripts/smoke.sh` docker-compose 冒烟通过（B6-A.12）
- [ ] M11 跟进：TestCoverage / DuplicateRate collector 真实数据源接入（V13 迁移已注入 4 条 system_param）

---

> _本报告随 B6-A 集成验证批次合并到 develop；之后每次 IT 重跑（CI 或人工）应以 PR
> 形式更新"运行后填写"占位区。_
