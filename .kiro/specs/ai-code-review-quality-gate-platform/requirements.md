# 需求文档（Requirements Document）

## Introduction

本文档面向 **AI 辅助代码评审与质量门禁平台** 的需求澄清、开发拆分与测试验收。系统目标是在开发者提交代码或创建 PR/MR 后，自动拉取代码差异、执行静态扫描、调用 AI 辅助评审、汇总质量指标并执行质量门禁，最终生成结构化评审报告并将门禁结果回写代码平台。

平台覆盖以下 10 个业务模块：

- **M01** 用户与权限模块
- **M02** 项目与仓库模块
- **M03** 评审任务模块
- **M04** 代码差异解析模块
- **M05** 静态分析模块
- **M06** AI 辅助评审模块
- **M07** 质量门禁模块
- **M08** 报告与看板模块
- **M09** 通知与回写模块
- **M10** 系统管理模块

需求按模块分组，每个需求采用 User Story + EARS 验收条件的格式编写，可作为后续设计、任务拆分和测试验收的统一基线。

## 术语表（Glossary）

### 系统组件（System Components）

- **Platform**：AI 辅助代码评审与质量门禁平台整体（即"系统"），是所有子服务的总称。
- **Auth_Service**：用户认证服务，负责登录、令牌签发、令牌校验与刷新。
- **User_Service**：用户管理服务，负责用户增删改查、状态切换与角色绑定。
- **Project_Service**：项目服务，负责项目创建、查询、更新与成员管理。
- **Repository_Service**：仓库服务，负责仓库绑定、连通性测试与令牌加密存储。
- **Webhook_Service**：Webhook 接入服务，负责接收外部代码平台事件并校验签名。
- **Review_Task_Service**：评审任务服务，负责任务的创建、查询、重试与取消。
- **Task_Worker**：任务执行器，负责按状态机串行驱动一个评审任务的各个阶段。
- **Diff_Parser**：代码差异解析器，负责拉取并解析 Diff、统计变更规模与风险文件。
- **Static_Scanner**：静态扫描服务，负责调用 ESLint / Pylint / Checkstyle / Semgrep 等扫描器并将其结果转换为统一的 CodeIssue 模型。
- **AI_Review_Service**：AI 辅助评审客户端，负责构造上下文、调用大模型 API、校验返回结构。
- **Quality_Gate**：质量门禁服务，负责门禁规则的配置、校验、判定与豁免审批。
- **Metric_Collector**：指标采集器，负责为门禁规则收集 actualValue（如严重问题数、覆盖率、AI 风险评分）。
- **Report_Service**：评审报告服务，负责报告查询与多维度筛选。
- **Issue_Service**：问题服务，负责问题查询、状态流转与评论。
- **Dashboard_Service**：项目质量看板服务，负责趋势、分布、Top 列表的聚合查询。
- **Notification_Service**：通知服务，负责站内通知的产生、查询与已读标记。
- **Writeback_Service**：状态回写服务，负责将门禁结果回写到外部代码平台（GitLab / GitHub / Gitee）。
- **Admin_Service**：系统管理服务，负责模型配置、扫描器配置、系统参数与审计日志管理。
- **Audit_Service**：审计日志服务，负责关键操作的写入与查询。

### 业务实体（Domain Entities）

- **User**：平台用户，拥有用户名、邮箱、状态（启用 / 禁用）和一个或多个角色。
- **Role**：角色，取值范围为 `DEVELOPER`、`REVIEWER`、`PROJECT_ADMIN`、`SYSTEM_ADMIN`、`CI_CD`。
- **Project**：项目，包含名称、描述、默认分支、主要语言。
- **RepositoryBinding**：项目与外部代码仓库的绑定，包含 provider、仓库地址、加密访问令牌、Webhook Secret。
- **ReviewTask**：评审任务，唯一标识为 `taskNo`，关键字段包括 `(projectId, prId, commitSha)` 三元组以及当前状态。
- **CodeIssue**：评审问题，来源为 `SAST`、`AI` 或 `MANUAL`，包含严重等级、状态、文件、行号、规则码、描述、建议、置信度。
- **QualityGate**：项目级门禁配置，由若干 GateRule 组成，含版本号与启用开关。
- **GateRule**：单条门禁规则，包含 metric、operator、threshold、severity（`BLOCKER` / `WARN`）、enabled。
- **GateResult**：门禁判定结果，包含 status、score、failedRules、passedRules、evaluatedAt。
- **GateWaiver**：门禁豁免申请，包含申请人、原因、过期时间、审批状态。
- **Notification**：站内通知，包含类型、标题、内容、已读标记。
- **AuditLog**：审计日志，记录操作者、动作、资源、IP 与时间。
- **TaskLog**：任务执行日志，按阶段（stage）和级别（level）划分。

### 状态字典（Status Dictionaries）

- **ReviewTask.status**：`PENDING` → `FETCHING_DIFF` → `STATIC_SCANNING` → `AI_REVIEWING` → `GATE_EVALUATING` → `PASSED` / `FAILED_GATE` / `EXECUTION_FAILED`。
- **CodeIssue.status**：`NEW`、`CONFIRMED`、`FALSE_POSITIVE`、`PENDING_VERIFY`、`CLOSED`、`REOPENED`。
- **GateResult.status**：`PENDING`、`PASSED`、`FAILED`、`WAIVED`。
- **Severity**：`CRITICAL`、`HIGH`、`MEDIUM`、`LOW`、`INFO`。

### 关键概念（Concepts）

- **幂等键（Idempotency_Key）**：用于防止重复任务创建的标识，Webhook 使用 `(provider, repositoryId, eventId)`，手动接口使用请求头 `Idempotency-Key`，业务层使用 `(projectId, prId, commitSha)` 三元组唯一约束。
- **AI 降级（AI_Degradation）**：当 AI 服务超时或不可用时，任务不整体失败，而是仅跳过 AI 评审阶段并在报告中标记"AI 评审不可用"，门禁继续基于其他指标判定。
- **敏感数据过滤（Sensitive_Filter）**：在向 AI 服务发送内容前，过滤 `.env`、密钥文件、证书文件、配置密文以及命中 Token 正则的内容。

---

## Requirements

---

### Requirement 1 — 用户登录与令牌签发（M01 / FR-001）

**User Story:** 作为一名平台用户，我希望使用用户名和密码登录平台，以便获取访问令牌并进入对应角色的工作台。

#### Acceptance Criteria

1. WHEN 用户提交合法的用户名与密码且账号状态为启用，THE Auth_Service SHALL 签发 accessToken 和 refreshToken，并返回当前用户信息（id、username、roles）。
2. IF 用户提交的用户名或密码错误，THEN THE Auth_Service SHALL 返回错误码 `AUTH_INVALID_CREDENTIALS`，且响应体不得指明具体是用户名错误还是密码错误。
3. IF 用户提交的账号状态为禁用，THEN THE Auth_Service SHALL 返回错误码 `AUTH_ACCOUNT_DISABLED` 并拒绝签发令牌。
4. WHEN 用户使用过期的 accessToken 访问受保护接口，THE Auth_Service SHALL 返回错误码 `AUTH_INVALID_TOKEN` 并响应 HTTP 401。
5. WHEN 用户使用合法的 refreshToken 调用 `/auth/refresh`，THE Auth_Service SHALL 签发新的 accessToken 并返回 `expiresIn` 秒数。
6. THE Auth_Service SHALL 将密码以加盐哈希形式存储，且不得在任何接口响应或日志中输出密码原文或哈希值。

---

### Requirement 2 — 角色与权限控制（M01 / FR-002）

**User Story:** 作为系统管理员，我希望系统按开发者、评审者、项目管理员、系统管理员、CI/CD 五种角色区分菜单与接口权限，以便实现最小权限原则。

#### Acceptance Criteria

1. WHEN 任意已登录用户访问受保护接口，THE Platform SHALL 根据用户角色与项目成员关系校验授权，且越权请求返回 HTTP 403 与错误码 `PERMISSION_DENIED`。
2. WHERE 用户角色为 DEVELOPER，THE Platform SHALL 仅允许其创建 / 查看本人参与项目的评审任务、查看与处理本人任务下的问题。
3. WHERE 用户角色为 PROJECT_ADMIN，THE Platform SHALL 允许其创建项目、绑定仓库、配置质量门禁、管理项目成员。
4. WHERE 用户角色为 SYSTEM_ADMIN，THE Platform SHALL 允许其管理全局用户、模型配置、扫描器配置、系统参数与审计日志。
5. WHERE 用户角色为 CI_CD，THE Platform SHALL 允许其调用 Webhook 接口与手动创建评审任务接口，但禁止访问任何写入用户、项目配置、门禁规则的接口。
6. WHEN 受权限控制的请求被允许执行，THE Audit_Service SHALL 记录一条审计日志，包含 operatorId、action、resourceType、resourceId、ip 与时间戳。

---

### Requirement 3 — 用户管理（M01）

**User Story:** 作为系统管理员，我希望按关键字、状态、角色查询并启用 / 禁用用户，以便控制平台访问。

#### Acceptance Criteria

1. WHEN SYSTEM_ADMIN 调用 `GET /users` 并传入 keyword、status、role、page、pageSize，THE User_Service SHALL 返回符合条件的分页用户列表（PageResult<UserDTO>）。
2. WHEN SYSTEM_ADMIN 调用 `PATCH /users/{id}/status` 将用户状态切换为禁用，THE User_Service SHALL 立即将该用户后续接口请求拦截为 HTTP 401 并使其已签发的 accessToken 在 5 分钟内失效。
3. IF 非 SYSTEM_ADMIN 用户调用用户管理接口，THEN THE User_Service SHALL 返回错误码 `PERMISSION_DENIED`。
4. THE User_Service SHALL 保证 username 与 email 在全局范围内唯一。

---

### Requirement 4 — 项目创建与维护（M02 / FR-003）

**User Story:** 作为项目管理员，我希望创建并维护项目的基础信息，以便后续绑定仓库与配置门禁。

#### Acceptance Criteria

1. WHEN PROJECT_ADMIN 提交合法的项目创建请求（包含 name、description、defaultBranch、language），THE Project_Service SHALL 创建项目并返回 ProjectDTO。
2. IF 提交的 name 在当前组织内已存在，THEN THE Project_Service SHALL 返回错误码 `PROJECT_NAME_EXISTS` 并拒绝创建。
3. IF 创建项目时 name、defaultBranch 或 language 任一字段缺失或不在合法枚举内，THEN THE Project_Service SHALL 返回错误码 `VALIDATION_ERROR` 并在 details 中指出非法字段。
4. WHEN PROJECT_ADMIN 调用 `PUT /projects/{id}` 更新项目，THE Project_Service SHALL 校验调用者为该项目的 PROJECT_ADMIN，否则返回 `PERMISSION_DENIED`。
5. THE Project_Service SHALL 在每次创建或更新项目时，向 Audit_Service 写入一条审计日志。

---

### Requirement 5 — 代码仓库绑定（M02 / FR-004）

**User Story:** 作为项目管理员，我希望将项目绑定到 GitLab / GitHub / Gitee 等仓库，以便平台能拉取代码差异并接收 Webhook。

#### Acceptance Criteria

1. WHEN PROJECT_ADMIN 调用 `POST /projects/{id}/repository/test` 提交 provider、repoUrl、accessToken，THE Repository_Service SHALL 调用对应平台 API 进行连通性测试，并返回 `reachable` 布尔值与 message。
2. IF 仓库连通性测试失败，THEN THE Repository_Service SHALL 拒绝绑定请求并返回错误码 `REPOSITORY_UNREACHABLE`，message 中包含外部平台的简要原因。
3. WHEN 仓库绑定成功，THE Repository_Service SHALL 将 accessToken 使用对称加密算法加密后存储，且 webhook_secret 同样加密存储。
4. THE Repository_Service SHALL 在任何接口响应中不返回 accessToken 或 webhook_secret 的原文或可解密形式。
5. WHEN 仓库绑定保存成功，THE Repository_Service SHALL 生成形如 `https://{host}/api/v1/webhooks/git` 的 Webhook 地址并返回给前端，供 PROJECT_ADMIN 复制配置到代码平台。
6. THE Repository_Service SHALL 保证一个项目在同一时刻最多只有一条有效的仓库绑定记录（uk_project_id）。

---

### Requirement 6 — 项目成员管理（M02）

**User Story:** 作为项目管理员，我希望为项目添加和移除成员并指定其项目角色，以便控制项目级别的访问权限。

#### Acceptance Criteria

1. WHEN PROJECT_ADMIN 调用 `POST /projects/{id}/members` 添加成员（userId、role），THE Project_Service SHALL 创建 project_member 记录，且 `(projectId, userId)` 必须唯一。
2. IF 待添加的 userId 不存在或对应用户状态为禁用，THEN THE Project_Service SHALL 返回错误码 `VALIDATION_ERROR` 并拒绝添加。
3. WHEN PROJECT_ADMIN 调用 `DELETE /projects/{id}/members/{userId}`，THE Project_Service SHALL 移除该成员关联，且不删除全局用户记录。
4. WHEN 成员被移除，THE Platform SHALL 立即拒绝该用户对该项目的非公开数据访问。

---

### Requirement 7 — Webhook 接收与任务创建（M03 / FR-005，BR-001）

**User Story:** 作为 CI/CD 系统或代码平台，我希望在 PR/MR 事件发生时向平台推送 Webhook，以便自动触发评审任务。

#### Acceptance Criteria

1. WHEN Webhook_Service 接收到 `POST /webhooks/git` 请求，THE Webhook_Service SHALL 使用项目绑定的 webhook_secret 校验 `X-Hub-Signature-256` 头部签名。
2. IF Webhook 签名校验失败，THEN THE Webhook_Service SHALL 返回 HTTP 401 与错误码 `WEBHOOK_SIGNATURE_INVALID`，且不创建任何任务。
3. WHEN Webhook 通过签名校验且事件类型为 PR/MR 创建或代码推送，THE Review_Task_Service SHALL 解析 provider、repository、prId、commitSha、sourceBranch、targetBranch 并创建 ReviewTask，初始状态为 `PENDING`。
4. IF 已存在相同 `(projectId, prId, commitSha)` 三元组且状态非 `EXECUTION_FAILED` 的 ReviewTask，THEN THE Review_Task_Service SHALL 返回该已有任务的 taskId 而不重复创建（幂等）。
5. WHEN Webhook 接收到非 PR/MR 类事件（如 ping、issue），THE Webhook_Service SHALL 返回 `code=0` 且 `data` 中标识 `ignored=true`，不创建任务。
6. THE Webhook_Service SHALL 在 3 秒内对 Webhook 请求返回响应，任务的实际执行通过异步队列触发。

---

### Requirement 8 — 手动创建评审任务（M03 / FR-006）

**User Story:** 作为开发者或评审者，我希望手动指定源分支、目标分支与 commit 创建评审任务，以便在 Webhook 失败时仍能触发评审。

#### Acceptance Criteria

1. WHEN 项目成员调用 `POST /review-tasks` 提交 projectId、sourceBranch、targetBranch 以及 commitSha 或 prId 至少其一，THE Review_Task_Service SHALL 创建 ReviewTask 并返回 ReviewTaskDTO。
2. IF 请求中 commitSha 与 prId 同时缺失，THEN THE Review_Task_Service SHALL 返回错误码 `VALIDATION_ERROR`。
3. IF 请求未携带 `Idempotency-Key` 请求头且已存在相同 `(projectId, prId, commitSha)` 的活跃任务，THEN THE Review_Task_Service SHALL 返回错误码 `TASK_DUPLICATED` 并附带已存在任务的 taskId。
4. WHEN 请求携带 `Idempotency-Key` 请求头且该 key 在 24 小时内已被使用，THE Review_Task_Service SHALL 返回上一次该 key 对应的 ReviewTaskDTO。
5. WHERE 调用者角色为 CI_CD，THE Review_Task_Service SHALL 允许其代表项目创建任务，且在审计日志中记录 triggerType=`CI_CD`。

---

### Requirement 9 — 任务执行编排与重试（M03 / FR-007，BR-002）

**User Story:** 作为评审者或项目管理员，我希望任务按固定状态机自动执行，并能在失败时重试，以便保证评审流程可观测、可恢复。

#### Acceptance Criteria

1. THE Task_Worker SHALL 按以下顺序驱动 ReviewTask 状态流转：`PENDING` → `FETCHING_DIFF` → `STATIC_SCANNING` → `AI_REVIEWING` → `GATE_EVALUATING` → `PASSED` / `FAILED_GATE`。
2. IF 状态机的任一阶段抛出未捕获异常或超过该阶段超时阈值，THEN THE Task_Worker SHALL 将任务状态置为 `EXECUTION_FAILED` 并将异常摘要写入 task_log。
3. THE Review_Task_Service SHALL 拒绝任何不符合状态字典中合法迁移的状态变更请求（例如不允许从 `PASSED` 回到 `PENDING`）。
4. WHEN PROJECT_ADMIN 或 REVIEWER 对状态为 `EXECUTION_FAILED`、`FAILED_GATE` 或 `PASSED` 的任务调用 `POST /review-tasks/{id}/retry`，THE Review_Task_Service SHALL 创建一个新的执行批次并将状态重置为 `PENDING`。
5. IF 调用 retry 时任务当前状态为 `PENDING`、`FETCHING_DIFF`、`STATIC_SCANNING`、`AI_REVIEWING` 或 `GATE_EVALUATING` 之一（即正在执行），THEN THE Review_Task_Service SHALL 返回错误码 `TASK_NOT_RETRYABLE`。
6. WHEN PROJECT_ADMIN 对状态为 `PENDING` 的任务调用 `POST /review-tasks/{id}/cancel`，THE Review_Task_Service SHALL 将其状态置为 `EXECUTION_FAILED`，并在 task_log 中记录取消原因与操作者。
7. THE Task_Worker SHALL 为每个任务的每个阶段写入至少一条 task_log，包含 stage、level（`INFO` / `WARN` / `ERROR`）、message、createdAt。

---

### Requirement 10 — 代码差异解析（M04 / FR-008）

**User Story:** 作为系统，我希望解析每个评审任务的代码差异并统计变更规模，以便后续扫描与 AI 评审能够聚焦于变更内容。

#### Acceptance Criteria

1. WHEN ReviewTask 进入 `FETCHING_DIFF` 阶段，THE Diff_Parser SHALL 通过项目仓库绑定中的 accessToken 拉取 sourceBranch 与 targetBranch 之间的 Diff。
2. THE Diff_Parser SHALL 输出每个变更文件的：filePath、changeType（`ADDED` / `MODIFIED` / `DELETED`）、addedLines、deletedLines、totalChangedLines。
3. THE Diff_Parser SHALL 汇总并保存任务级统计：changedFileCount、totalAddedLines、totalDeletedLines。
4. IF 拉取 Diff 失败（例如仓库不可达、令牌失效、commitSha 不存在），THEN THE Diff_Parser SHALL 在 task_log 中写入文件级别或任务级别的失败原因，并将任务状态置为 `EXECUTION_FAILED`。
5. WHERE 单个文件的变更行数超过系统参数 `diff.maxLinesPerFile`（默认 5000），THE Diff_Parser SHALL 在该文件上标记 `oversized=true`，并在后续阶段对其跳过 AI 评审但保留静态扫描结果。

---

### Requirement 11 — 静态代码扫描（M05 / FR-009）

**User Story:** 作为评审者，我希望系统按项目语言调用对应的静态扫描器并将结果统一为 CodeIssue 模型，以便在报告中查看与处理。

#### Acceptance Criteria

1. WHEN ReviewTask 进入 `STATIC_SCANNING` 阶段，THE Static_Scanner SHALL 根据 project.language 选择对应扫描器（Java→Checkstyle、Python→Pylint、JavaScript→ESLint、通用安全→Semgrep）。
2. THE Static_Scanner SHALL 将每个扫描器的原始输出转换为 CodeIssue，且每个 CodeIssue 必须包含 filePath、lineNo、ruleCode、source=`SAST`、severity、status=`NEW`、description、suggestion。
3. WHERE 扫描结果包含未在 Severity 枚举中的等级，THE Static_Scanner SHALL 按映射表归一化为 `CRITICAL` / `HIGH` / `MEDIUM` / `LOW` / `INFO` 之一。
4. IF 某一扫描器调用失败，THEN THE Static_Scanner SHALL 在 task_log 中记录该扫描器的失败原因，但其他扫描器与后续阶段 SHALL 继续执行。
5. THE Static_Scanner SHALL 仅对 Diff_Parser 输出的变更文件执行扫描，不扫描未变更文件。

---

### Requirement 12 — AI 辅助评审与降级（M06 / FR-010，BR-005，BR-006）

**User Story:** 作为开发者，我希望 AI 评审服务对代码变更给出结构化的问题、原因、建议与置信度，以便我快速定位需要修改的地方。

#### Acceptance Criteria

1. WHEN ReviewTask 进入 `AI_REVIEWING` 阶段，THE AI_Review_Service SHALL 构造包含变更代码片段、文件路径、行号上下文、项目语言与门禁关注指标的请求并调用配置中的模型服务。
2. THE AI_Review_Service SHALL 在请求构造前调用 Sensitive_Filter 过滤 `.env`、密钥文件（如 `*.pem`、`*.key`）、证书文件（`*.crt`）、命中 Token 正则的内容；调用方 SHALL 对过滤前后的载荷做哈希比对（或长度 / 计数比对）以确认过滤确实发生，比对未发生变化但输入命中过滤规则时视为过滤失败，THE AI_Review_Service SHALL 中止本次 AI 调用并在 task_log 中记录 `ERROR`。
3. WHEN AI 服务返回 200 且响应体满足 JSON Schema（包含 issues 数组，每项含 filePath、lineNo、severity、description、suggestion、confidence），THE AI_Review_Service SHALL 将每条问题持久化为 source=`AI` 的 CodeIssue。
4. IF AI 返回的 JSON Schema 校验失败，THEN THE AI_Review_Service SHALL 在 task_log 中记录 `WARN` 级别异常，丢弃本次响应，且不影响 SAST 阶段产生的 CodeIssue。
5. IF AI 服务在 `AI_REVIEW_TIMEOUT_SECONDS`（默认 60 秒）内未返回或返回 5xx，THEN THE Task_Worker SHALL 跳过本阶段并在 GateResult.summary 中标记 `aiAvailable=false`，任务状态继续推进至 `GATE_EVALUATING` 而非 `EXECUTION_FAILED`。
6. THE AI_Review_Service SHALL 计算任务级 ai_risk_score（取 AI 问题严重等级与 confidence 的加权聚合，0~100）并写入 GateResult 计算输入。

---

### Requirement 13 — 质量门禁配置（M07 / FR-011）

**User Story:** 作为项目管理员，我希望按指标、运算符、阈值与阻断级别配置项目的质量门禁，并控制启用状态，以便实施差异化的合并策略。

#### Acceptance Criteria

1. WHEN PROJECT_ADMIN 调用 `POST /projects/{id}/quality-gate` 保存配置，THE Quality_Gate SHALL 校验每条 GateRule 的 metric 必须取自合法集合：`critical_issue_count`、`security_issue_count`、`test_coverage`、`duplicate_rate`、`ai_risk_score`、`new_issue_count`。
2. THE Quality_Gate SHALL 校验 GateRule 的 operator 取自 `<=`、`>=`、`<`、`>`、`==`、`!=`，且 severity 取自 `BLOCKER` 或 `WARN`。
3. IF 任一 GateRule 的 metric、operator、threshold 或 severity 非法，THEN THE Quality_Gate SHALL 返回错误码 `GATE_RULE_INVALID` 并在 details 中指出违规规则索引。
4. THE Quality_Gate SHALL 在每次保存时生成新的 `version` 并保留历史版本（不直接覆盖），同一项目同一时刻仅一个 version 处于 `enabled=true`。
5. WHERE PROJECT_ADMIN 选择"使用模板"，THE Quality_Gate SHALL 提供至少一套默认模板：`critical_issue_count<=0 (BLOCKER)`、`test_coverage>=70 (BLOCKER)`、`ai_risk_score<=80 (WARN)`。
6. THE Quality_Gate SHALL 允许将 GateRule 的 `enabled` 切换为 `false` 以临时停用单条规则，停用后该规则在判定时被跳过。

---

### Requirement 14 — 质量门禁判定与回写（M07 / FR-012，BR-004）

**User Story:** 作为项目管理员，我希望系统在 AI 评审结束后自动汇总指标并执行门禁判定，并将结果回写到代码平台，以便阻止不合格代码合并。

#### Acceptance Criteria

1. WHEN ReviewTask 进入 `GATE_EVALUATING` 阶段，THE Metric_Collector SHALL 为每条启用的 GateRule 收集 actualValue（如 critical_issue_count 取本任务下 severity ∈ {`CRITICAL`,`HIGH`} 且 status≠`FALSE_POSITIVE` 的 CodeIssue 数量）。
2. THE Quality_Gate SHALL 对每条启用规则按 operator 比较 actualValue 与 threshold，输出每条规则的 passed 标志与实际值。
3. IF 存在任意一条规则 `passed=false` 且 `severity=BLOCKER`，THEN THE Quality_Gate SHALL 将 GateResult.status 置为 `FAILED` 并将 ReviewTask.status 置为 `FAILED_GATE`。
4. WHEN 所有 BLOCKER 规则均通过（含 WARN 规则失败的情况），THE Quality_Gate SHALL 将 GateResult.status 置为 `PASSED` 并将 ReviewTask.status 置为 `PASSED`。
5. THE Quality_Gate SHALL 计算质量评分（0~100）并写入 GateResult.score，评分公式由系统参数控制并对前端可见。
6. WHEN GateResult 写入完成，THE Writeback_Service SHALL 通过项目绑定的 provider API 将状态（`success` / `failed`）回写到对应 PR/MR 的 commit status。
7. IF Writeback 调用失败，THEN THE Writeback_Service SHALL 按指数退避重试至多 3 次，且在 1 次到 3 次之间的每次重试期间 SHALL 持续保持 GateResult 已写入但 commit status 未确认的可恢复状态（即不修改 ReviewTask.status 的最终值），3 次仍失败则在 task_log 中写入 `ERROR` 并保留可手动重试的入口。
8. THE Report_Service SHALL 在 GateResult 中保存 failedRules 与 passedRules 明细，供报告页面按规则展示失败原因。

---

### Requirement 15 — 门禁豁免审批（M07 / BR-007）

**User Story:** 作为开发者，我希望对门禁失败的任务提交豁免申请并由有权限角色审批，以便特殊场景下能临时合并而不修改门禁规则。

#### Acceptance Criteria

1. WHERE ReviewTask.status 为 `FAILED_GATE`，THE Quality_Gate SHALL 允许 DEVELOPER 或 REVIEWER 调用 `POST /review-tasks/{id}/gate-waivers` 提交 GateWaiver，必须包含 reason 与 expireAt。
2. IF 申请提交时 reason 为空或长度小于 10 字符，THEN THE Quality_Gate SHALL 返回错误码 `VALIDATION_ERROR`。
3. WHEN PROJECT_ADMIN 或 REVIEWER 调用 `POST /gate-waivers/{id}/approve` 且 `approved=true`，THE Quality_Gate SHALL 将该任务的 GateResult.status 置为 `WAIVED`，但不修改原有 GateRule。
4. WHEN GateResult.status 转为 `WAIVED`，THE Writeback_Service SHALL 将代码平台的 commit status 回写为 `success` 并在描述中标注"已豁免"。
5. THE Quality_Gate SHALL 记录每次豁免的申请人、审批人、审批意见与 expireAt 至 audit_log。
6. IF 同一 ReviewTask 已存在状态为 `PENDING` 或 `APPROVED` 且 expireAt 晚于当前时间的 GateWaiver，THEN THE Quality_Gate SHALL 拒绝新申请并返回错误码 `WAIVER_DUPLICATED`；WHERE 已有 GateWaiver 状态为 `REJECTED`、`EXPIRED` 或 expireAt 已过，THE Quality_Gate SHALL 允许提交新的豁免申请。

---

### Requirement 16 — 评审报告展示（M08 / FR-013）

**User Story:** 作为开发者或评审者，我希望在评审报告页查看任务概览、问题列表、代码差异、AI 建议、门禁结果与执行日志，并支持多维度筛选，以便快速理解评审结论。

#### Acceptance Criteria

1. WHEN 项目成员调用 `GET /review-tasks/{id}/report`，THE Report_Service SHALL 返回包含 taskOverview（任务编号、PR、commit、status、score）、gateResultSummary、issueCounts（按 severity 与 source 聚合）、aiAvailability 的 ReviewReportDTO。
2. WHEN 项目成员调用 `GET /review-tasks/{id}/issues` 并传入 severity、status、source、filePath 任意组合的筛选条件，THE Issue_Service SHALL 返回 PageResult<CodeIssueDTO>，按 severity 降序、createdAt 升序排序。
3. THE Report_Service SHALL 支持筛选条件 `source ∈ {SAST, AI, MANUAL}`、`severity ∈ {CRITICAL, HIGH, MEDIUM, LOW, INFO}`、`status ∈ CodeIssue.status 枚举`。
4. WHEN 报告页查询代码差异 Tab，THE Report_Service SHALL 返回每个变更文件的 diff hunks，并标注每行是否存在关联问题。
5. WHEN 报告页查询执行日志 Tab，THE Report_Service SHALL 返回 PageResult<TaskLogDTO>，支持按 stage 与 level 筛选。
6. THE Report_Service SHALL 在 P95 ≤ 2 秒内响应单任务报告查询请求（基于 100 并发的基准测试条件）；WHERE 并发量超过 100 ，THE Report_Service SHALL 仍正确返回结果但允许性能降级（即 P95 不再受 2 秒约束）。

---

### Requirement 17 — 问题生命周期管理（M08 / FR-014，BR-003）

**User Story:** 作为开发者或评审者，我希望对评审问题进行状态流转并附加备注，以便记录处理过程并区分有效问题与误报。

#### Acceptance Criteria

1. THE Issue_Service SHALL 仅允许以下 CodeIssue.status 迁移：`NEW`→`CONFIRMED`/`FALSE_POSITIVE`、`CONFIRMED`→`PENDING_VERIFY`/`CLOSED`、`PENDING_VERIFY`→`CLOSED`/`REOPENED`、`CLOSED`→`REOPENED`、`REOPENED`→`CONFIRMED`/`FALSE_POSITIVE`。
2. IF 状态变更目标不在合法迁移集合内，THEN THE Issue_Service SHALL 返回错误码 `VALIDATION_ERROR`。
3. WHEN 用户将 CodeIssue.status 变更为 `FALSE_POSITIVE` 或 `CLOSED`，THE Issue_Service SHALL 强制要求 comment 字段非空且长度 ≥ 5 字符，否则返回错误码 `VALIDATION_ERROR`。
4. WHEN CodeIssue.status 发生变更，THE Issue_Service SHALL 写入一条 issue_history 记录，包含 from、to、operator、comment、changedAt。
5. WHERE 调用者为 DEVELOPER 角色且尝试变更不属于其参与任务的 CodeIssue 状态，THE Issue_Service SHALL 将该次状态变更视为非法迁移并返回错误码 `VALIDATION_ERROR`（拒绝转换而非返回授权错误）。
6. WHEN CodeIssue.status 变更为 `FALSE_POSITIVE`，THE Metric_Collector SHALL 在后续门禁判定中将该问题排除在 critical_issue_count 等指标之外。

---

### Requirement 18 — 项目质量看板（M08 / FR-015）

**User Story:** 作为项目管理员，我希望在质量看板上查看任务趋势、问题分布、门禁通过率与高风险文件，以便评估团队代码质量。

#### Acceptance Criteria

1. WHEN 项目成员调用 `GET /dashboard/projects/{id}/quality-trend` 并传入 startDate、endDate、可选 branch，THE Dashboard_Service SHALL 返回按日期聚合的 taskCount、passRate、avgScore、avgDurationSeconds 时间序列。
2. THE Dashboard_Service SHALL 拒绝跨度超过 365 天的查询请求并返回错误码 `VALIDATION_ERROR`。
3. WHEN 项目成员请求"高风险文件 TopN"（N 默认 10），THE Dashboard_Service SHALL 返回按问题数与严重等级加权排序的 filePath 列表。
4. THE Dashboard_Service SHALL 仅返回调用者作为 project_member 的项目数据，越权请求返回 `PERMISSION_DENIED`。
5. THE Dashboard_Service SHALL 在 P95 ≤ 2 秒内响应基于近 30 天数据的看板查询。

---

### Requirement 19 — 通知中心（M09 / FR-016）

**User Story:** 作为平台用户，我希望在任务完成、门禁失败、豁免审批、问题被指派等事件发生时收到站内通知，以便及时处理。

#### Acceptance Criteria

1. WHEN ReviewTask.status 转为 `PASSED`、`FAILED_GATE` 或 `EXECUTION_FAILED`，THE Notification_Service SHALL 向任务发起人与项目管理员各发送一条对应类型的 Notification。
2. WHEN GateWaiver 被提交，THE Notification_Service SHALL 向项目所有 PROJECT_ADMIN 与 REVIEWER 发送审批通知。
3. WHEN 用户调用 `GET /notifications` 并传入 `read`、`type`、page、pageSize，THE Notification_Service SHALL 返回 PageResult<NotificationDTO>。
4. WHEN 用户调用 `PATCH /notifications/{id}/read`，THE Notification_Service SHALL 校验该通知归属当前用户，若非则返回 `PERMISSION_DENIED`，否则将 read_flag 置为 true。
5. THE Notification_Service SHALL 保留通知至少 90 天，超过保留期可由管理员配置归档策略。

---

### Requirement 20 — 门禁结果回写代码平台（M09）

**User Story:** 作为代码平台，我希望接收平台回写的门禁结果，以便在 PR 页面阻止或允许合并。

#### Acceptance Criteria

1. WHEN GateResult.status 转为 `PASSED`、`FAILED` 或 `WAIVED`，THE Writeback_Service SHALL 调用对应 provider 的 commit status / check run API 回写状态：`PASSED`/`WAIVED`→`success`、`FAILED`→`failure`。
2. THE Writeback_Service SHALL 在回写描述中包含 taskNo、score、failedRules 数量，并附评审报告页 URL。
3. IF 回写时 provider API 返回 4xx，THEN THE Writeback_Service SHALL 不再重试并在 task_log 中写入 `ERROR` 详情。
4. IF 回写时 provider API 返回 5xx 或网络超时，THEN THE Writeback_Service SHALL 按 1s、5s、25s 间隔指数退避重试至多 3 次。
5. WHERE 项目绑定的 provider 不支持 commit status 回写，THE Writeback_Service SHALL 跳过回写并在 task_log 中记录 `INFO` 级别说明，不影响任务状态。

---

### Requirement 21 — 系统参数与扫描器 / 模型管理（M10 / FR-017）

**User Story:** 作为系统管理员，我希望配置 AI 模型、扫描器、任务并发与日志策略等系统参数，以便平台能在不同环境下灵活运行。

#### Acceptance Criteria

1. WHEN SYSTEM_ADMIN 调用 `POST /admin/model-configs` 新增模型配置（name、baseUrl、apiKey、timeout），THE Admin_Service SHALL 将 apiKey 加密存储并在响应中以掩码形式返回。
2. WHEN SYSTEM_ADMIN 调用 `GET /admin/model-configs`，THE Admin_Service SHALL 返回模型列表，但 apiKey 字段必须以 `****` 形式脱敏。
3. THE Admin_Service SHALL 支持配置扫描器：name、language、enabled、command、resultParserType。
4. THE Admin_Service SHALL 支持配置系统参数：`review.worker.concurrency`（1~32）、`ai.review.timeout.seconds`（10~300）、`diff.maxLinesPerFile`（100~50000）、`tokenEncryptionKey`，并对超出范围的值返回 `VALIDATION_ERROR`。
5. WHEN 任一系统参数被修改，THE Audit_Service SHALL 写入审计日志，记录变更前后的值（敏感字段以掩码记录）。
6. IF 非 SYSTEM_ADMIN 调用 `/admin/*` 接口，THEN THE Admin_Service SHALL 返回 `PERMISSION_DENIED`。

---

### Requirement 22 — 审计日志（M10）

**User Story:** 作为系统管理员，我希望记录关键操作并支持按操作者、动作、时间范围查询，以便进行安全审计与问题追溯。

#### Acceptance Criteria

1. THE Audit_Service SHALL 为以下操作写入审计日志：登录、登出、用户启用 / 禁用、项目创建 / 更新、仓库绑定 / 修改、门禁配置变更、门禁豁免审批、模型配置变更、系统参数变更。
2. WHEN SYSTEM_ADMIN 调用 `GET /admin/audit-logs` 并传入 operator、action、startDate、endDate、page、pageSize，THE Audit_Service SHALL 返回符合条件的 PageResult<AuditLogDTO>。
3. THE Audit_Service SHALL 在每条审计日志中保留 operatorId、operatorUsername、action、resourceType、resourceId、ip、detail、createdAt。
4. THE Audit_Service SHALL 同时保证两个约束：审计日志记录一经写入即不可被修改或删除（immutable，仅追加），且仅 SYSTEM_ADMIN 角色可调用查询接口；任一约束未满足则视为审计模块不可用。
5. THE Audit_Service SHALL 在 detail 字段中对密码、accessToken、apiKey、webhookSecret 等敏感字段以掩码记录（例如 `****`）。

---

### Requirement 23 — 安全性与隐私合规（NFR-SEC-001、NFR-PRI-001）

**User Story:** 作为合规负责人，我希望平台在身份验证、数据加密、敏感信息处理上符合安全规范，以便平台可在企业内部使用。

#### Acceptance Criteria

1. THE Platform SHALL 在所有非 `/auth/login`、`/webhooks/*`、`/health` 的接口上强制要求 `Authorization: Bearer <token>`，缺失则返回 HTTP 401。
2. THE Repository_Service、Admin_Service SHALL 对仓库 accessToken、webhook_secret、模型 apiKey 使用对称加密算法存储，且加密密钥来自系统参数 `tokenEncryptionKey`。
3. THE Platform SHALL 在所有接口响应、task_log、audit_log 中对密码、Token、apiKey 进行掩码处理。
4. WHEN AI_Review_Service 构造请求载荷时，THE Sensitive_Filter SHALL 跳过路径匹配 `.env`、`*.pem`、`*.key`、`*.crt`、`*.p12` 的文件，且对正文中命中常见 Token 正则（如 `AKIA[0-9A-Z]{16}`、`sk-[A-Za-z0-9]{32,}`）的内容进行替换为 `***REDACTED***`。
5. IF 任意接口尝试以明文返回敏感字段，THEN 该行为视为缺陷且必须在测试中被检出（参见 SECURITY_TEST_001）。
6. THE Platform SHALL 在生产环境强制启用 HTTPS，HTTP 请求 SHALL 被 308 重定向到 HTTPS。

---

### Requirement 24 — 性能、可靠性与可观测性（NFR-PER-001、NFR-REL-001、NFR-OBS-001）

**User Story:** 作为运维工程师，我希望平台具备可衡量的性能、可靠性与可观测性指标，以便在故障发生时快速定位并恢复。

#### Acceptance Criteria

1. WHEN 中小型 PR（变更 ≤ 500 行、文件 ≤ 30 个）触发评审任务，THE Task_Worker SHALL 在 3 分钟内完成全部阶段（不含 AI 服务排队等待时间）。
2. WHEN 报告查询接口在 100 并发下被压测，THE Report_Service SHALL 满足 P95 响应时间 ≤ 2 秒。
3. THE Task_Worker SHALL 支持通过系统参数 `review.worker.concurrency` 调整并发度，且变更后 60 秒内生效。
4. IF Task_Worker 进程在任务执行中被中断重启，THEN THE Task_Worker SHALL 在重启后将该任务置为 `EXECUTION_FAILED` 或者继续从断点执行（实现可二选一），且 SHALL 不允许该任务直接迁移到 `PASSED` 状态以跳过未完成的阶段；任何情况下任务都不得永久停留在中间状态。
5. THE Platform SHALL 为每个 ReviewTask 输出可按 taskNo 串联的执行链路，包含接口日志、阶段日志、模型调用日志、异常堆栈与各阶段耗时。
6. THE Platform SHALL 暴露 `/health` 与 `/metrics` 端点，分别用于健康检查与基础指标（任务队列长度、AI 调用成功率、回写成功率）输出。

---

### Requirement 25 — 可测试性与覆盖率（NFR-TST-001）

**User Story:** 作为测试工程师，我希望核心模块具备单元测试、接口模块具备集成测试、门禁规则具备独立用例，以便回归与持续集成。

#### Acceptance Criteria

1. THE Platform SHALL 为 Task_Worker、Diff_Parser、Quality_Gate、Sensitive_Filter 等核心服务提供单元测试，且语句覆盖率 ≥ 70%。
2. THE Platform SHALL 为认证、项目管理、Webhook、评审任务、报告、门禁、问题状态、通知接口提供集成测试。
3. THE Quality_Gate SHALL 提供独立的规则引擎测试集，覆盖至少 6 种 metric × 5 种 operator × `BLOCKER`/`WARN` 的组合场景。
4. THE AI_Review_Service SHALL 提供降级场景测试（超时、5xx、JSON Schema 校验失败）以验证 BR-005。
5. THE Repository_Service SHALL 提供越权访问测试用例集，覆盖：非项目成员调用任意项目接口（无论读写）SHALL 一律返回 `PERMISSION_DENIED`，不存在仅可读不可写的中间豁免（对应 SECURITY_TEST_001）。
