# 实现计划（Implementation Plan）

- 项目：AI 辅助代码评审与质量门禁平台
- Feature 名称：`ai-code-review-quality-gate-platform`
- 关联文档：
  - 需求基线：`requirements.md`（R1~R25，共 25 条 EARS 需求）
  - 技术设计：`design.md`（§1~§20；含 DDL、Mermaid、Java 接口签名、§19 的 8 条 PBT 属性、§20 的 4 批次分支拆分）
- 技术栈锁定：Spring Boot 3.x（Java 17）+ MyBatis-Plus + PostgreSQL 15 + Redis 7 + Vue 3 + Vite + TypeScript + Element Plus + Pinia
- 工作流类型：requirements-first
- 文档总数：6 个批次（B0 ~ B6）/ 22 条顶层任务（git 分支）/ 约 140 条最叶子任务

> 本计划严格遵循 design.md §20 的批次拆分：B0 基础设施 → B1 独立模块 → B2 依赖批次 1 → B3 评审主链路 → B4 报告外围 → B5 前端持续集成 → B6 性能 / 安全 / 端到端验证。每条顶层任务对应一条独立 `feat/*` 或 `chore/*` git 分支，可由独立 subagent 并行开发。

---

## 并行执行规则（Parallel Execution Rules）

1. **批次内并行**：同一批次（B{N}）下的不同顶层任务相互独立，可由不同 subagent 在各自的 git 分支上并行开发与测试。
2. **批次间串行**：下一批次（B{N+1}）的任何顶层任务必须在本批次的集成测试节点 `IT-{N+1}` 通过后才能开始；上一批次的代码必须先合并到 `develop`，新分支必须基于该 `develop` 的最新提交切出。
3. **共享文件冲突治理**：
   - `pom.xml` 顶层 `<dependencies>` 段、`application.yml` 全局段、`router/index.ts`、`stores/index.ts`、Flyway `db/migration/` 目录的序号，由各分支末尾合并到批次集成 PR `chore/wire-batch-{N}` 时进行人工合并；分支内不得修改其它分支正在编辑的同名片段。
   - DDL Migration 文件名严格使用 `V{seq}__{module}_{purpose}.sql` 命名；序号区段：B0 占用 V1~V9，B1 占用 V10~V19，B2 占用 V20~V29，B3 占用 V30~V49，B4 占用 V50~V69；同一分支内允许多个相邻序号。
4. **跨批次的前端分支**：`feat/web-pages`（B5-A）持续追踪后端，按 IT-2 / IT-3 / IT-4 / IT-5 四个里程碑分阶段拼装页面；其每个子任务的依赖批次在 `_Depends:` 中标注。
5. **属性测试映射（design.md §19，共 8 条）**：
   - Property 1 任务状态机迁移有向图 → B3-A
   - Property 2 问题状态迁移合法集 → B4-A
   - Property 3 任务幂等三元组 → B3-A
   - Property 4 门禁 BLOCKER ⇔ FAILED → B3-F
   - Property 5 SensitiveFilter Token 过滤 → B3-E
   - Property 6 JWT 黑名单 5 分钟内必失效 → B1-A
   - Property 7 Diff 行数一致性 → B3-C
   - Property 8 审计日志 append-only → B1-B
6. **覆盖率门槛（R25.1）**：每条顶层任务合并前其语句覆盖率 ≥ 70%，由本批次集成节点的 JaCoCo 报告校验。
7. **可选子任务标记**：测试相关子任务（单元测试 / 集成测试 / 属性测试）以 `*` 后缀标记为可选；核心实现子任务永不标记可选。

---

## 批次总览（Batch Overview）

| 批次 | 集成节点 | 顶层任务（git 分支） | 主要交付物 | 依赖批次 | 关联需求范围 |
|---|---|---|---|---|---|
| B0 | IT-1 | B0-A `chore/infra-bootstrap`、B0-B `chore/web-bootstrap` | Maven/Vite 骨架、common/infra、Flyway V1__init、docker-compose、CI 模板、前端布局/守卫 | — | R23 / R24 基础部分；为全部 R 提供运行底座 |
| B1 | IT-2 | B1-A `feat/m01-auth`、B1-B `feat/m01-audit`、B1-C `feat/m02-project`、B1-D `feat/m10-admin` | 登录鉴权 + 黑名单 + 用户管理 + 审计写入 + 项目 / 成员 + 系统管理 | B0 | R1~R6、R21、R22、R23 |
| B2 | IT-3 | B2-A `feat/m02-repository`、B2-B `feat/m07-gate-config` | 仓库绑定（含加密 / 连通性 / Webhook URL）+ 门禁规则 CRUD（不含执行引擎） | B1 | R5、R13、R23.2 |
| B3 | IT-4 | B3-A `feat/m03-task-core`、B3-B `feat/m03-webhook`、B3-C `feat/m04-diff`、B3-D `feat/m05-scanner`、B3-E `feat/m06-ai`、B3-F `feat/m07-gate-engine` | 评审任务状态机 + Webhook + Diff 解析 + 静态扫描 + AI 评审（含降级）+ 门禁判定引擎 | B2 | R7~R14、R23.4、R24 |
| B4 | IT-5 | B4-A `feat/m08-issue`、B4-B `feat/m08-report`、B4-C `feat/m08-dashboard`、B4-D `feat/m09-notification`、B4-E `feat/m09-writeback` | 问题状态流转 + 报告聚合 + 项目看板 + 站内通知 + 状态回写 + 豁免审批 | B3 | R15~R20 |
| B5 | 跨批次 | B5-A `feat/web-pages` | UI-001 ~ UI-010 全部页面，按里程碑追踪后端落地 | B1 / B2 / B3 / B4 | R1~R21（前端覆盖部分） |
| B6 | IT-6 | B6-A `chore/integration-verification` | k6 性能基准、越权用例集、端到端 Smoke、JaCoCo 覆盖率门槛、契约基线 | B0~B5 | R16.6、R18.5、R23、R24.2、R25 |

> 任务编号约定：`B{批次号}-{字母}`（顶层）/ `B{批次号}-{字母}.{序号}`（子任务）/ 必要时使用 `B{批次号}-{字母}.{序号}.{子序号}`（最叶子）。每条最叶子任务 1~4 小时可完成。

---

## Tasks

### B0 — 基础设施 Bootstrap（Integration Node IT-1）

- [x] B0-A 后端基础设施 Bootstrap
  - _Branch: chore/infra-bootstrap_
  - _Depends: —_
  - _Integration Node: IT-1_

  - [x] B0-A.1 创建 Maven 单模块骨架与 `pom.xml`
    - 在 `acrqg-platform/` 下生成 Maven 单模块骨架，`groupId=com.acrqg`、`artifactId=acrqg-platform`、`java.version=17`、`spring-boot.version=3.x`
    - 在 `<dependencies>` 中加入 `spring-boot-starter-web`、`spring-boot-starter-validation`、`spring-boot-starter-security`、`spring-boot-starter-data-redis`、`spring-boot-starter-actuator`、`mybatis-plus-spring-boot3-starter`、`postgresql`、`flyway-core`、`flyway-database-postgresql`、`jjwt-api/impl/jackson`、`logstash-logback-encoder`、`micrometer-registry-prometheus`、`springdoc-openapi-starter-webmvc-ui`
    - 在 `<dependencyManagement>` 锁定版本；插件 `spring-boot-maven-plugin`、`jacoco-maven-plugin`（执行点 `prepare-agent` + `report`）
    - 输出物：`acrqg-platform/pom.xml`
    - _Requirements: R24.1, R25.1_

  - [x] B0-A.2 创建启动类与 Profile 划分
    - 创建 `com.acrqg.platform.AcrqgApplication` 启动类，开启 `@SpringBootApplication(scanBasePackages="com.acrqg.platform")`、`@EnableScheduling`、`@EnableAspectJAutoProxy`
    - 配置 `application.yml`（公共段：日志、Actuator、`management.endpoints.web.exposure.include=health,metrics`）；`application-dev.yml`、`application-test.yml`、`application-prod.yml`、`application-api.yml`、`application-worker.yml`
    - `prod` profile 关闭 swagger UI 默认对外；`worker` profile 不注册 web 控制器（仅 health/metrics）
    - _Requirements: R17.3（语义已对齐 design §17.3）, R24.6_

  - [x] B0-A.3 实现 `common` 包：响应包装与错误码
    - `common/api/ApiResponse.java`（record，code/message/data/details/requestId，含 `success`/`failure` 静态工厂）
    - `common/api/PageResult.java`（含 `of(items,page,pageSize,total)` 静态工厂）
    - `common/api/FieldError.java`
    - `common/api/ErrorCode.java`（枚举：SUCCESS、AUTH_INVALID_CREDENTIALS、AUTH_INVALID_TOKEN、AUTH_ACCOUNT_DISABLED、PERMISSION_DENIED、VALIDATION_ERROR、PROJECT_NAME_EXISTS、REPOSITORY_UNREACHABLE、WEBHOOK_SIGNATURE_INVALID、TASK_DUPLICATED、TASK_NOT_RETRYABLE、TASK_NOT_FOUND、AI_SERVICE_UNAVAILABLE、GATE_RULE_INVALID、WAIVER_DUPLICATED、INTERNAL_ERROR）
    - `common/exception/BusinessException.java`、`common/exception/GlobalExceptionHandler.java`（处理 BusinessException / MethodArgumentNotValidException / AccessDeniedException / Exception，映射到对应 HTTP 状态）
    - `common/util/{IdGenerator,MaskUtils,JsonUtils}.java`
    - _Requirements: R23.3（掩码工具）, 全局错误码（R1~R22 引用）_

  - [x] B0-A.4 实现 `infra/security`：JWT 与 Spring Security 配置
    - `infra/security/JwtTokenProvider.java`：`issue(userId,username,roles)`/`parse(token)`/`toAuthentication(claims)`；HS256，`jti` 使用 UUID
    - `infra/security/JwtAuthFilter.java`：`OncePerRequestFilter`；白名单 `/api/v1/auth/login`、`/api/v1/auth/refresh`、`/api/v1/webhooks/**`、`/health`、`/metrics`、`/v3/api-docs/**`、`/swagger-ui/**`；缺失 / 无效 token 返回 401 ApiResponse
    - `infra/security/SecurityConfig.java`：禁用 CSRF（API 服务）、关闭默认表单登录、注册 `JwtAuthFilter` 在 `UsernamePasswordAuthenticationFilter` 之前
    - `infra/security/CurrentUserHolder.java`（ThreadLocal，便于切面取当前用户）
    - 在 `application.yml` 暴露 `jwt.secret`、`jwt.access.ttl`、`jwt.refresh.ttl` 占位
    - _Requirements: R1.4, R23.1_

  - [x] B0-A.5 实现 `infra/crypto`：AES-GCM 加密器
    - `infra/crypto/AesGcmCipher.java`：12 字节 IV + 128bit Tag，PBKDF2 派生 256bit 密钥，base64(IV||CipherText) 输出
    - `infra/crypto/TokenEncryptor.java`：薄包装，提供 `encrypt(String)`/`decrypt(String)` 接口，`tokenEncryptionKey` 从 `application.yml` 读取
    - 输出物：上述类 + 必要单元测试占位（在 B1 各模块按需补全）
    - _Requirements: R5.3, R5.4, R21.1, R23.2_

  - [x] B0-A.6 实现 `infra/redis`：Redis 配置、Stream Publisher、幂等 Store、JWT 黑名单（占位）
    - `infra/config/RedisConfig.java`：`RedisTemplate<String,String>`、`StringRedisTemplate`、`RedissonClient`（可选）
    - `infra/redis/RedisStreamPublisher.java`：`enqueue(streamKey, fields)`，封装 `XADD`
    - `infra/redis/IdempotencyStore.java`：`putIfAbsent(key, ttl)`/`get(key)`，TTL 默认 24h
    - `infra/redis/JwtBlacklist.java`：`add(userId, jti, ttl)`/`contains(jti)`/`removeUser(userId)`；底层使用 `SET jwt:bl:jti:{jti} 1 EX {ttl}`，并在 `Set jwt:bl:user:{userId}` 中记录该用户的所有 jti（用于禁用用户时一次性清空）
    - _Requirements: R3.2, R7.4, R8.4_

  - [x] B0-A.7 实现 `infra/permission`：权限注解与 AOP 切面
    - `infra/permission/RequirePermission.java`（注解：role[], projectMember, projectIdParam, projectRole[]）
    - `infra/permission/PermissionAspect.java`：`@Before("@annotation(rp)")` 校验全局角色 / 项目成员 / 项目角色，未通过抛 `AccessDeniedException`
    - `infra/permission/PermissionEvaluator.java`：声明 `hasAnyRole`、`isProjectMember`、`hasProjectRole`，实现暂占位（B1-C 实现 isProjectMember / hasProjectRole）
    - `infra/permission/ParamResolver.java`：基于 `JoinPoint` + `@PathVariable` / `@RequestParam` 解析 Long 入参
    - _Requirements: R2.1, R2.2, R2.3, R2.4, R2.5_

  - [x] B0-A.8 实现 `infra/log`：TraceId 与 JSON 日志
    - `infra/log/TraceIdFilter.java`：`OncePerRequestFilter`，从 `X-Request-Id` 取或生成 UUID，写入 MDC（`traceId`），并写入 `ApiResponse.requestId`
    - `infra/log/MaskingLogbackEncoder.java`：扩展 `LogstashEncoder`，对 message 中匹配 `password|token|api[_-]?key|secret` 的 key/value 与 Token 正则替换为 `****`
    - `logback-spring.xml`：JSON 输出，包含 traceId、taskNo、userId、level、logger、stack_trace
    - _Requirements: R23.3, R24.5_

  - [x] B0-A.9 实现 `infra/web`：CORS / 响应掩码切面 / OpenAPI / MyBatis-Plus
    - `infra/config/WebMvcConfig.java`：CORS 白名单（dev 全放开，prod 来源由 `app.cors.allowed-origins` 控制）
    - `infra/log/ResponseMaskingAspect.java`：`@AfterReturning("execution(* com.acrqg.platform..controller..*(..))")`，对返回对象递归掩码 `password / passwordHash / accessToken / apiKey / webhookSecret / accessTokenEncrypted / apiKeyEncrypted` 字段
    - `infra/config/OpenApiConfig.java`：springdoc Bearer 安全方案；`io.swagger.v3.oas.models.info.Info` 设置版本与标题
    - `infra/config/MyBatisPlusConfig.java`：分页插件 + 乐观锁插件 + 字段填充 `created_at` / `updated_at`
    - _Requirements: R23.3_

  - [x] B0-A.10 编写 Flyway `V1__init.sql`（初始化 user/role/user_role/audit_log + 触发器）
    - 严格按 design.md §7.2 的 DDL 创建：`"user"`、`role`、`user_role`、`audit_log`、`reject_audit_modify` 函数 + `trg_audit_no_update` / `trg_audit_no_delete` 触发器、`touch_updated_at` 函数 + `"user"` 的 updated_at 触发器
    - 在 `role` 表中插入 5 条种子角色：`DEVELOPER`、`REVIEWER`、`PROJECT_ADMIN`、`SYSTEM_ADMIN`、`CI_CD`
    - 创建初始 SYSTEM_ADMIN 用户（`admin` / `admin@local`，`password_hash` 通过 BCrypt 离线生成的字面量），并写入 user_role 关联
    - 输出物：`src/main/resources/db/migration/V1__init.sql`
    - _Requirements: R1.6, R2, R22.4, R23.3_

  - [x] B0-A.11 编写 Dockerfile 与 docker-compose.yml
    - `acrqg-platform/Dockerfile`：基于 `eclipse-temurin:17-jre`，多阶段构建（builder 用 `eclipse-temurin:17-jdk` + `mvn package`），暴露 8080，入口 `java -jar` 通过 `SPRING_PROFILES_ACTIVE` 切换 api / worker
    - `docker-compose.yml`：4 个服务（postgres:15、redis:7、backend、worker）+ 1 个 frontend 占位（nginx）；含 healthcheck、volumes、env vars（按 design.md §17.1）
    - 输出物：`acrqg-platform/Dockerfile`、`docker-compose.yml`、`.env.example`
    - _Requirements: R24.6_

  - [x] B0-A.12 编写 GitHub Actions CI 流水线模板
    - `.github/workflows/ci.yml`：3 个 job
      - `backend-build`：checkout → setup-java 17 → `mvn -B -DskipITs=false verify`（启动 postgres/redis service container）→ 上传 JaCoCo 报告 → 校验语句覆盖率 ≥ 70%（`jacoco-maven-plugin` 的 `check` goal）
      - `frontend-build`：setup-node 20 → `npm ci` → `npm run lint` → `npm run build` → `npm run test:unit -- --run`
      - `e2e`：`docker compose up -d` → 等待健康 → 运行 k6 / 集成 smoke（在 B6 实现具体脚本，此处仅占位 echo）
    - _Requirements: R25.1, R25.2_

  - [x] B0-A.13 实现 `/health` 与 `/metrics` 自定义指标骨架
    - 启用 Spring Actuator 的 `/health`、`/metrics`（Prometheus）
    - 添加 `RedisStreamHealthIndicator`（`PING` + `XLEN review-task-stream`）、`AiServiceHealthIndicator`（占位，返回 UNKNOWN，待 B3-E 实现）
    - 注册 Micrometer 指标占位：`acrqg_jwt_blacklist_size` Gauge（值由 B1-A 注入）
    - _Requirements: R24.6_

  - [ ]* B0-A.14 编写基础设施单元测试（AesGcmCipher、IdempotencyStore、JwtBlacklist 占位）
    - `AesGcmCipherTest`：随机明文 → encrypt → decrypt 还原；同一明文每次密文不同（IV 随机）
    - `IdempotencyStoreTest`（基于 Embedded Redis 或 Testcontainers）：putIfAbsent 第二次返回 false；TTL 到期后再次返回 true
    - `JwtBlacklistTest`：add → contains 命中；TTL 内一致；TTL 过期不命中
    - _Requirements: R23.2, R23.3_

  - [x] B0-A.15 撰写 OpenAPI 规约 baseline 与 CHANGELOG
    - 启动应用一次，导出 `/v3/api-docs` 到 `docs/openapi-baseline.json` 作为基线
    - 创建 `CHANGELOG.md`，记录 B0-A 交付内容
    - 创建 `docs/architecture.md`（链接到 `design.md`）作为团队入口
    - _Requirements: R25.2_

- [x] B0-B 前端基础设施 Bootstrap
  - _Branch: chore/web-bootstrap_
  - _Depends: —_
  - _Integration Node: IT-1_

  - [x] B0-B.1 创建 Vue 3 + Vite + TypeScript 项目骨架
    - 在 `acrqg-web/` 下使用 `npm create vite@latest -- --template vue-ts` 生成骨架；锁定 `vue@3.4+`、`vite@5+`、`typescript@5+`
    - 安装依赖：`element-plus`、`@element-plus/icons-vue`、`pinia`、`vue-router@4`、`axios`、`dayjs`、`echarts`、`vue-echarts`
    - 安装开发依赖：`vitest`、`@vue/test-utils`、`jsdom`、`eslint`、`@typescript-eslint/*`、`prettier`
    - 配置 `tsconfig.json`、`vite.config.ts`（`server.proxy` 把 `/api` 反代到 `http://localhost:8080`）
    - _Requirements: R23.6（前端无业务，需要前端骨架支持后续 R1~R21）_

  - [x] B0-B.2 实现 `src/api/http.ts` axios 拦截器
    - `request` 拦截器：从 Pinia auth store 注入 `Authorization: Bearer <accessToken>` + `X-Request-Id`
    - `response` 拦截器：成功（`code===0`）解包返回 `data`；失败按错误码统一弹 `ElMessage`；遇到 `AUTH_INVALID_TOKEN` 自动调用 `/auth/refresh` 一次重放（refresh 失败则跳登录）
    - 错误码到提示的中文映射表
    - _Requirements: R1.4, R1.5, R23.3_

  - [x] B0-B.3 创建布局组件 `DefaultLayout.vue` 与 `BlankLayout.vue`
    - `DefaultLayout`：`el-container` 顶部 header（项目切换器 + 用户菜单 + 未读通知红点）+ 左侧 menu（按角色显示）+ 主内容区
    - `BlankLayout`：仅居中卡片（用于登录页）
    - 输出物：两个组件 + 全局样式（`src/styles/index.scss`）
    - _Requirements: R2_

  - [x] B0-B.4 配置 Pinia 与全局 stores 占位
    - `src/stores/index.ts`：`createPinia()` 注册到 `main.ts`
    - `src/stores/auth.ts`：state（user、accessToken、refreshToken、expiresAt、roles）；actions（login、logout、refresh、setUser）；持久化到 `localStorage`
    - `src/stores/notification.ts`：仅 state 占位
    - _Requirements: R1, R19_

  - [x] B0-B.5 配置 Vue Router 路由表与守卫
    - `src/router/index.ts`：注册 design.md §5.2 全部 15 条路由（部分 page 在 B5 实现，此处先占位 `defineAsyncComponent`），每条路由 `meta.requiredRoles` 与 `meta.public`
    - `src/router/guards.ts`：`beforeEach`（未登录跳 `/login`，角色不足跳 `/forbidden`）
    - `src/pages/forbidden/ForbiddenPage.vue`、`src/pages/notfound/NotFoundPage.vue`
    - _Requirements: R2.1, R2.2, R2.3, R2.4, R2.5_

  - [x] B0-B.6 创建前端 Dockerfile 与 nginx 配置
    - 多阶段构建：`node:20` 构建 → `nginx:1.25` serve `/usr/share/nginx/html`
    - `nginx.conf`：HTTPS 占位（生产由外层 nginx 终结），`/api/` 反代到 `http://backend:8080/`
    - _Requirements: R23.6_

  - [ ]* B0-B.7 编写前端骨架 Vitest 单元测试
    - `tests/unit/http.spec.ts`：mock axios，验证 token 注入与 401 触发 refresh 重放
    - `tests/unit/router-guard.spec.ts`：未登录访问受保护路由跳转 `/login`
    - _Requirements: R1.4, R23.1_

  - [x] B0-B.8 撰写前端 CHANGELOG 与 README
    - `acrqg-web/README.md`：开发命令、环境变量、目录说明
    - `acrqg-web/CHANGELOG.md`：B0-B 交付内容
    - _Requirements: —（项目工程要求）_

#### Integration Node IT-1

- 端到端冒烟（自动化）：`docker compose up -d` 后访问 `http://localhost:8080/health` 返回 `UP`、`http://localhost:80` 渲染登录页空壳。
- 越权用例：未携带 token 访问 `/api/v1/projects` 返回 401（即使无业务接口，应由 JwtAuthFilter 接管）。
- 覆盖率门槛：JaCoCo 报告语句覆盖率 ≥ 70%（B0 仅基础设施类，重点验收 AesGcmCipher / JwtBlacklist / IdempotencyStore；R25.1）。
- 文档：OpenAPI baseline 已存档；docker-compose 启动文档可执行。


---

### B1 — 独立模块并行实现（Integration Node IT-2）

- [x] B1-A 用户认证与用户管理（M01）
  - _Branch: feat/m01-auth_
  - _Depends: B0-A_
  - _Integration Node: IT-2_

  - [x] B1-A.1 实现 Auth Domain / DTO / Mapper
    - `auth/domain/{User,UserRole,Role}.java`（DO，对应 V1 表）
    - `auth/dto/{LoginRequest,LoginResultDTO,RefreshResultDTO,UserDTO}.java`（按 design §8.4 字段与 Bean Validation 注解）
    - `auth/repository/{UserMapper,RoleMapper,UserRoleMapper}.java`（MyBatis-Plus）
    - `auth/repository/mapper/UserMapper.xml`：`selectWithRolesByUsername`、`selectWithRolesById`
    - _Requirements: R1.1, R1.6, R3.4_

  - [x] B1-A.2 实现 `AuthServiceImpl`：login / logout / refresh / me
    - `login(LoginRequest)`：BCrypt 校验密码；status=DISABLED → 抛 `AUTH_ACCOUNT_DISABLED`；用户名/密码错 → 抛 `AUTH_INVALID_CREDENTIALS`（响应文案不区分两者）；签发 access + refresh，返回 `LoginResultDTO`
    - `logout(String accessToken)`：解析 jti → 加入 Redis 黑名单（TTL = token 剩余有效期）
    - `refresh(String refreshToken)`：校验 refreshToken 未撤销 → 签发新 accessToken
    - `me()`：从 SecurityContext 取当前 userId，查询并返回 `UserDTO`
    - 写审计日志（`登录成功`、`登出`），通过 `ApplicationEventPublisher` 发布 `AuditEvent`
    - _Requirements: R1.1, R1.2, R1.3, R1.4, R1.5, R1.6_

  - [x] B1-A.3 实现 `AuthController`
    - `POST /api/v1/auth/login`（公开）、`POST /api/v1/auth/logout`（已登录）、`POST /api/v1/auth/refresh`（公开）、`GET /api/v1/auth/me`（已登录）
    - 全部使用 `ApiResponse<T>` 包装，登录失败按 `ErrorCode.httpStatus()` 返回 401
    - _Requirements: R1.1, R1.2, R1.3, R1.4, R1.5, R23.1_

  - [x] B1-A.4 实现 `UserService` 与 `UserController`
    - `UserService.page(UserQuery)`：keyword 模糊匹配 username/email；status / role 精确过滤；分页排序
    - `UserService.changeStatus(id, status)`：当 status 切到 DISABLED 时，立即将该用户所有 jti 加入黑名单，撤销其 refreshToken；写审计
    - `UserService.create(UserCreateRequest)`：username / email 唯一校验；BCrypt 哈希
    - `UserController`：`GET /api/v1/users`、`POST /api/v1/users`、`PATCH /api/v1/users/{id}/status`，全部 `@RequirePermission(role=SYSTEM_ADMIN)`
    - _Requirements: R3.1, R3.2, R3.3, R3.4_

  - [x] B1-A.5 实现 `PermissionEvaluator.hasAnyRole` 完整逻辑
    - 在 B0-A 占位基础上，从当前用户的 roles 列表与传入 Role[] 求交集；缺失 jwt claims 中的 roles 时回库查询并缓存到 ThreadLocal
    - _Requirements: R2.1_

  - [ ]* B1-A.6 编写 AuthService / UserService 单元测试
    - `AuthServiceImplTest`：登录正常 / 错密码 / 账号禁用 / token 过期 / refresh 成功
    - `UserServiceImplTest`：分页过滤；禁用用户即时拉黑；用户名重复抛冲突
    - 覆盖率门槛 ≥ 80%
    - _Requirements: R1.1, R1.2, R1.3, R1.4, R1.5, R3.1, R3.2, R3.4_

  - [ ]* B1-A.7 编写 Auth 集成测试（Spring Boot Test + Testcontainers）
    - `AuthIntegrationTest`：postgres + redis 容器；初始化 admin 用户；登录 200 → 携带 token 访问 `/auth/me` 200 → 注销后再次访问 401（黑名单生效）
    - `UserAdminIntegrationTest`：SYSTEM_ADMIN 列表分页；非 SYSTEM_ADMIN 调用返回 403
    - _Requirements: R1, R3, R23.1_

  - [ ]* B1-A.8 编写 Property 6 属性测试：JWT 黑名单 5 分钟内必失效
    - 文件：`src/test/java/com/acrqg/platform/property/JwtBlacklistTtlPropertyTest.java`
    - `@Property(tries = 100)`：随机 userId、jti、ttlSeconds∈[60,300]、probeSeconds∈[0,300]；可变 Clock 推进时间；断言 `bl.contains(jti) == (probeSeconds < ttlSeconds)`
    - _Property: P6_
    - _Requirements: R3.2_

- [x] B1-B 审计日志（M01）
  - _Branch: feat/m01-audit_
  - _Depends: B0-A_
  - _Integration Node: IT-2_

  - [x] B1-B.1 实现 Audit Domain / DTO / Event
    - `audit/domain/AuditLog.java`、`audit/dto/{AuditLogDTO,AuditQuery}.java`
    - `audit/event/AuditEvent.java`（含 operatorId、operatorUsername、action、resourceType、resourceId、ip、detail）
    - `audit/repository/AuditLogMapper.java`（仅 INSERT 与 SELECT；不暴露 update/delete 方法）
    - _Requirements: R22.1, R22.3_

  - [x] B1-B.2 实现 `AuditService` 与 ApplicationListener
    - `AuditService.record(AuditEvent)`：异步写入 audit_log（`@Async` + 自定义线程池）；写入前对 detail 进行掩码（password、accessToken、apiKey、webhookSecret 替换 `****`）
    - `AuditService.page(AuditQuery)`：按 operator、action、起止时间分页
    - `audit/event/AuditEventListener.java`：`@EventListener` 接收 AuditEvent → record
    - _Requirements: R22.1, R22.3, R22.5_

  - [x] B1-B.3 实现 `AuditController`（仅 SYSTEM_ADMIN）
    - `GET /api/v1/admin/audit-logs`（实际归属 admin URL 域，控制器位于 audit 模块）
    - `@RequirePermission(role=SYSTEM_ADMIN)`
    - _Requirements: R22.2, R22.4（仅 SYSTEM_ADMIN 可查询）_

  - [x] B1-B.4 编写 Flyway Migration `V10__m01_audit_seed.sql`（如需）
    - 仅插入种子数据（无新增表，因 V1 已建 audit_log）；记录 audit_log 的种子操作（`PLATFORM_INIT`）；如无种子需求则该文件可跳过并空提交注释
    - _Requirements: R22.3_

  - [ ]* B1-B.5 编写 Audit 单元测试
    - `AuditServiceImplTest`：含密码 / token 字段的 detail 经过掩码后落库；分页查询正确
    - _Requirements: R22.5_

  - [ ]* B1-B.6 编写 Audit 集成测试
    - 模拟登录事件触发 AuditEvent → 数据库可查到对应记录；非 SYSTEM_ADMIN 调用 `/admin/audit-logs` 返回 403
    - _Requirements: R22.1, R22.2, R22.4_

  - [ ]* B1-B.7 编写 Property 8 属性测试：审计日志 append-only
    - 文件：`src/test/java/com/acrqg/platform/property/AuditLogAppendOnlyPropertyTest.java`
    - `@Property(tries = 50)`：随机生成 AuditLog；插入并取得 id；尝试 `UPDATE audit_log SET action='X' WHERE id=?` 与 `DELETE FROM audit_log WHERE id=?` 必须抛 `DataAccessException` 且 message 含 `append-only`
    - _Property: P8_
    - _Requirements: R22.4_

- [x] B1-C 项目与项目成员（M02）
  - _Branch: feat/m02-project_
  - _Depends: B0-A, B1-A（仅依赖 user 表，可与 B1-A 并行；合并顺序在 B1-A 后）_
  - _Integration Node: IT-2_

  - [x] B1-C.1 编写 Flyway Migration `V11__m02_project.sql`
    - `project`、`project_member` 表完整 DDL + 索引（按 design §7.2）
    - `project` 的 `updated_at` 触发器
    - _Requirements: R4.1, R4.2, R6.1_

  - [x] B1-C.2 实现 Project Domain / DTO / Mapper
    - `project/domain/{Project,ProjectMember}.java`、`project/dto/{ProjectCreateRequest,ProjectUpdateRequest,ProjectDTO,ProjectQuery,AddMemberRequest}.java`
    - `project/repository/{ProjectMapper,ProjectMemberMapper}.java`，自定义 SQL：`countMembers(projectId)`、`isMember(projectId,userId)`、`roleOf(projectId,userId)`
    - _Requirements: R4.1, R4.3, R6.1_

  - [x] B1-C.3 实现 `ProjectServiceImpl`
    - `create`：name 唯一检查（捕获唯一约束冲突 → `PROJECT_NAME_EXISTS`）；language 校验枚举；创建后自动把创建者加入 project_member（PROJECT_ADMIN 角色）
    - `update`：校验调用者为该项目 PROJECT_ADMIN
    - `page` / `get`：支持关键字模糊；返回 memberCount
    - 通过 `ApplicationEventPublisher` 发布 AuditEvent（`PROJECT_CREATED`、`PROJECT_UPDATED`）
    - _Requirements: R4.1, R4.2, R4.3, R4.4, R4.5_

  - [x] B1-C.4 实现项目成员管理（addMember / removeMember / list）
    - `addMember`：校验 userId 存在且 status=ENABLED，否则 `VALIDATION_ERROR`；唯一约束冲突按 `VALIDATION_ERROR` 返回
    - `removeMember`：仅删除 project_member 行，不删全局用户
    - `listMembers`
    - 移除后立即由 `PermissionAspect` 拒绝该用户对该项目的非公开访问（无需额外缓存清理，因 Aspect 每次请求实时查 isMember）
    - _Requirements: R6.1, R6.2, R6.3, R6.4_

  - [x] B1-C.5 实现 `ProjectController`
    - `POST /api/v1/projects`：`@RequirePermission(role={PROJECT_ADMIN,SYSTEM_ADMIN})`
    - `GET /api/v1/projects`、`GET /api/v1/projects/{id}`、`PUT /api/v1/projects/{id}`：按权限注解配置
    - `POST /api/v1/projects/{id}/members`、`DELETE /api/v1/projects/{id}/members/{userId}`、`GET /api/v1/projects/{id}/members`
    - 全部启用 Bean Validation
    - _Requirements: R4, R6, R23.1_

  - [x] B1-C.6 完善 `PermissionEvaluator.isProjectMember` 与 `hasProjectRole`
    - 通过 `ProjectMemberMapper` 实现实时查询；为减少 DB 压力，使用 Caffeine 缓存（key=`{userId}:{projectId}`，TTL=60s）
    - 当 `removeMember` 时清理对应缓存项
    - _Requirements: R2, R6.4_

  - [ ]* B1-C.7 编写 ProjectService 单元测试
    - 重名抛 `PROJECT_NAME_EXISTS`；非 PROJECT_ADMIN 更新返回 403；addMember 不存在 userId 返回 `VALIDATION_ERROR`
    - _Requirements: R4.2, R4.4, R6.2_

  - [ ]* B1-C.8 编写 Project 集成测试
    - 完整流程：SYSTEM_ADMIN 创建项目 → 自动成为 PROJECT_ADMIN → 添加 DEVELOPER → 该 DEVELOPER 可查项目详情 → 移除后再访问返回 403
    - _Requirements: R4, R6_

- [x] B1-D 系统管理（M10：模型 / 扫描器 / 系统参数）
  - _Branch: feat/m10-admin_
  - _Depends: B0-A, B1-B（审计 event 监听，可与 B1-B 并行；合并顺序在 B1-B 后）_
  - _Integration Node: IT-2_

  - [x] B1-D.1 编写 Flyway Migration `V12__m10_admin.sql`
    - `model_config`、`scanner_config`、`system_param` 表（按 design §7.2）
    - 种子数据：4 条 scanner_config（checkstyle / eslint / pylint / semgrep，含 command 模板与 result_parser_type）；`system_param` 默认值：`review.worker.concurrency=4`、`ai.review.timeout.seconds=60`、`diff.maxLinesPerFile=5000`、`gate.score.formula.weight.default=1.0`、`tokenEncryptionKey`（占位，从环境变量装载，DB 不存明文）
    - _Requirements: R21.1, R21.3, R21.4_

  - [x] B1-D.2 实现 Admin Domain / DTO / Mapper
    - `admin/domain/{ModelConfig,ScannerConfig,SystemParam}.java`
    - `admin/dto/{ModelConfigCreateRequest,ModelConfigDTO,ScannerConfigRequest,ScannerConfigDTO,SystemParamDTO,SystemParamUpdateRequest}.java`
    - `admin/repository/{ModelConfigMapper,ScannerConfigMapper,SystemParamMapper}.java`
    - _Requirements: R21.1, R21.3, R21.4_

  - [x] B1-D.3 实现 `AdminService`：模型管理
    - `createModel`：apiKey 经 `TokenEncryptor.encrypt` 后存储；返回 DTO 中 `apiKeyMasked="****"`
    - `listModels` / `getModel`：apiKey 始终掩码
    - `updateModel` / `enableDisableModel`
    - 写审计：`MODEL_CONFIG_CREATED`、`MODEL_CONFIG_UPDATED`（detail 中 apiKey 掩码）
    - 提供内部接口 `decryptModelApiKey(modelId)`（仅供 B3-E AI 客户端使用）
    - _Requirements: R21.1, R21.2, R21.5, R23.2_

  - [x] B1-D.4 实现 `AdminService`：扫描器管理
    - `upsertScanner(name, language, enabled, command, resultParserType)`：command 为模板字符串，含 `{workdir}`、`{file}` 占位；name 唯一
    - `listScanners` / `getScanner`
    - 写审计：`SCANNER_CONFIG_UPSERTED`
    - _Requirements: R21.3_

  - [x] B1-D.5 实现 `AdminService`：系统参数管理
    - `getParam(key)` / `listParams(prefix)`
    - `updateParam(key, value)`：按 key 类型校验：`review.worker.concurrency` ∈ [1,32]、`ai.review.timeout.seconds` ∈ [10,300]、`diff.maxLinesPerFile` ∈ [100,50000]；越界抛 `VALIDATION_ERROR`
    - 敏感参数（sensitive=true）存储前加密、查询时掩码
    - 写审计（detail 中变更前后值，敏感参数掩码）
    - 通过 Redis pub/sub 发布 `param-changed:{key}` 事件，供 Worker 在 60s 内热更新（R24.3）
    - _Requirements: R21.4, R21.5, R22.1, R24.3_

  - [x] B1-D.6 实现 `AdminController`
    - `GET/POST /api/v1/admin/model-configs`、`PATCH /api/v1/admin/model-configs/{id}`
    - `GET /api/v1/admin/scanners`、`POST /api/v1/admin/scanners`
    - `GET /api/v1/admin/system-params`、`PATCH /api/v1/admin/system-params/{key}`
    - 全部 `@RequirePermission(role=SYSTEM_ADMIN)`，否则返回 `PERMISSION_DENIED`
    - _Requirements: R21.6_

  - [ ]* B1-D.7 编写 AdminService 单元测试
    - createModel apiKey 加密 + 响应掩码；越界 system param 抛 VALIDATION_ERROR；非 SYSTEM_ADMIN 调用 controller 抛 AccessDenied（用 @WithMockUser 模拟）
    - _Requirements: R21.1, R21.2, R21.4, R21.6_

  - [ ]* B1-D.8 编写 Admin 集成测试
    - 完整 CRUD 闭环：创建模型 → 列表中 apiKey 掩码 → 修改 system_param 触发 Redis pub/sub
    - 越权测试：DEVELOPER 调用 `/admin/*` 全部返回 403
    - _Requirements: R21, R23.3_

#### Integration Node IT-2

- 端到端场景（对应 design.md §9 SD-6 登录鉴权链路）：
  - SD-6.1：用户名 / 密码登录返回双 token；携带 access 访问 `/auth/me` 200。
  - SD-6.2：SYSTEM_ADMIN 禁用某用户后，原 access token 在 30s 内被 401 拒绝（R3.2）。
  - SD-6.3：SYSTEM_ADMIN 创建项目 → 添加成员 → 成员可见项目；非成员 403。
  - SD-6.4：SYSTEM_ADMIN 创建模型配置；列表响应中 apiKey 字段为 `****`。
- 越权用例（design §15.6）：
  - 不带 token 访问 `/projects` → 401 `AUTH_INVALID_TOKEN`。
  - 过期 token 访问 → 401。
  - 被禁用用户 token → 401。
  - DEVELOPER 调用 `POST /projects` → 403 `PERMISSION_DENIED`。
  - 任意非 SYSTEM_ADMIN 调用 `GET /admin/audit-logs` → 403。
  - CI_CD 角色调用 `POST /projects` → 403。
- 覆盖率门槛：B1 各分支语句覆盖率 ≥ 70%（R25.1）；jqwik P6 与 P8 至少各 100 / 50 次迭代通过。


---

### B2 — 仓库绑定与门禁配置（Integration Node IT-3）

- [x] B2-A 代码仓库绑定（M02）
  - _Branch: feat/m02-repository_
  - _Depends: B1-C, B1-D_
  - _Integration Node: IT-3_

  - [x] B2-A.1 编写 Flyway Migration `V20__m02_repository.sql`
    - `repository_binding` 表完整 DDL + uk_repository_binding_project + updated_at 触发器
    - _Requirements: R5.6_

  - [x] B2-A.2 实现 Repository Domain / DTO / Mapper
    - `repository/domain/RepositoryBinding.java`
    - `repository/dto/{RepositoryTestRequest,RepositoryBindRequest,RepositoryBindingDTO,ConnectivityResultDTO}.java`（按 design §8.4，accessToken / webhookSecret 标 `@NotBlank @Size`）
    - `repository/repository/RepositoryBindingMapper.java`
    - _Requirements: R5.1, R5.2, R5.3, R5.4_

  - [x] B2-A.3 实现 ProviderClient 抽象与三实现（GITHUB / GITLAB / GITEE）
    - `repository/client/ProviderClient.java`：`name()`、`ping(RepositoryTestRequest)`、`fetchDiff(DiffFetchRequest)`、`postCommitStatus(CommitStatusRequest)` 接口
    - 三实现：`GithubClient`、`GitlabClient`、`GiteeClient`，使用 Spring `RestClient`，超时 10s
    - `repository/client/ProviderClientFactory.java`：根据 provider 字符串返回对应 client
    - 在 B2-A 阶段仅实现 `ping`；`fetchDiff` 与 `postCommitStatus` 留接口，供 B3-C 与 B4-E 实现
    - _Requirements: R5.1, R5.2, R10.1, R20.1_

  - [x] B2-A.4 实现 `RepositoryServiceImpl`
    - `test`：调用对应 ProviderClient.ping，返回 reachable + message；不持久化
    - `bind`：先 ping 通 → 加密 accessToken / webhookSecret → 生成 webhookUrl（`https://{host}/api/v1/webhooks/git`）→ 写库；ping 失败抛 `REPOSITORY_UNREACHABLE`
    - `get`：返回 RepositoryBindingDTO（不含密文字段）
    - `decryptAccessToken(projectId)` / `decryptWebhookSecret(projectId)`：内部接口，供 B3-B / B3-C / B4-E 使用
    - 写审计：`REPOSITORY_BOUND`、`REPOSITORY_UPDATED`（accessToken 字段掩码）
    - _Requirements: R5.1, R5.2, R5.3, R5.4, R5.5, R5.6, R23.2, R23.3_

  - [x] B2-A.5 实现 `RepositoryController`
    - `POST /api/v1/projects/{id}/repository/test`、`POST /api/v1/projects/{id}/repository`、`GET /api/v1/projects/{id}/repository`
    - 写接口：`@RequirePermission(projectMember=true, projectRole=PROJECT_ADMIN)`
    - 读接口：`@RequirePermission(projectMember=true)`
    - _Requirements: R5, R23.1_

  - [ ]* B2-A.6 编写 RepositoryService 单元测试
    - 加密往返一致；ping 失败 → REPOSITORY_UNREACHABLE；响应 DTO 中无 access_token 字段
    - _Requirements: R5.2, R5.3, R5.4_

  - [ ]* B2-A.7 编写 Repository 集成测试
    - 使用 WireMock 模拟 GitHub `/repos/{owner}/{repo}` API；`bind` 成功后 webhookUrl 包含期望前缀
    - 越权：REVIEWER 调用 `/projects/{id}/repository` 返回 403（design §15.6）
    - _Requirements: R5, R23.1_

- [x] B2-B 质量门禁配置（M07，仅 CRUD，不含执行引擎）
  - _Branch: feat/m07-gate-config_
  - _Depends: B1-C_
  - _Integration Node: IT-3_

  - [x] B2-B.1 编写 Flyway Migration `V21__m07_quality_gate.sql`
    - `quality_gate`、`gate_rule` 表 + 索引 + `uk_quality_gate_one_enabled` 部分唯一索引（按 design §7.2）
    - 种子模板：在 `quality_gate` 中插入一条系统模板（`enabled=false`、project_id=NULL 不可，因外键，故跳过种子；模板由 service 层 `getDefaultTemplate()` 返回内存对象）
    - _Requirements: R13.1, R13.2, R13.4, R13.5_

  - [x] B2-B.2 实现 Gate Domain / DTO / Mapper
    - `gate/domain/{QualityGate,GateRule}.java`
    - `gate/dto/{GateRuleDTO,QualityGateSaveRequest,QualityGateDTO}.java`，按 design §8.4 配 Bean Validation
    - `gate/repository/{QualityGateMapper,GateRuleMapper}.java`
    - _Requirements: R13.1, R13.2_

  - [x] B2-B.3 实现 `QualityGateService`：保存、查询、模板
    - `save(projectId, request)`：校验每条 rule 的 metric / operator / severity 取自合法集合，否则抛 `GATE_RULE_INVALID` 并在 details 指出索引；事务内将旧版本 `enabled=false`，插入新版本 version+1, enabled=true；批量插 gate_rule
    - `getEnabled(projectId)`：返回 enabled=true 的版本与其规则
    - `listVersions(projectId)`、`getVersion(gateId)`
    - `getDefaultTemplate()`：返回 design §13.5 三条默认规则
    - 写审计：`QUALITY_GATE_SAVED`，detail 中含 version
    - _Requirements: R13.1, R13.2, R13.3, R13.4, R13.5, R13.6_

  - [x] B2-B.4 实现 `QualityGateController`
    - `POST /api/v1/projects/{id}/quality-gate`：`@RequirePermission(projectRole=PROJECT_ADMIN)`
    - `GET /api/v1/projects/{id}/quality-gate`：`@RequirePermission(projectMember=true)`
    - `GET /api/v1/quality-gates/templates`：已登录可见
    - _Requirements: R13_

  - [ ]* B2-B.5 编写 QualityGateService 单元测试
    - 非法 metric / operator → GATE_RULE_INVALID；保存时旧版本被禁用、新版本 enabled=true
    - 部分唯一索引违反时（构造同 project_id 两个 enabled=true）失败
    - _Requirements: R13.1, R13.3, R13.4_

  - [ ]* B2-B.6 编写 QualityGate 集成测试
    - 6 个 metric 和 6 种 operator 排列组合保存成功；非 PROJECT_ADMIN 保存返回 403
    - _Requirements: R13_

#### Integration Node IT-3

- 端到端场景：
  - 仓库绑定全流程：PROJECT_ADMIN 提交连通性测试通过 → 绑定 → DB 中 access_token_encrypted / webhook_secret_encrypted 为密文 → GET 返回的 DTO 不含 token；webhookUrl 满足 `^https?://.+/api/v1/webhooks/git$`。
  - 门禁规则保存：6 metric × 5 operator × {BLOCKER,WARN} 全组合保存成功；非法 metric 返回 `GATE_RULE_INVALID`；同时仅一个版本 `enabled=true`。
- 越权用例：
  - REVIEWER 项目成员调用 `POST /projects/{id}/repository` → 403（design §15.6）。
  - DEVELOPER 调用 `POST /projects/{id}/quality-gate` → 403。
- 覆盖率门槛：B2-A、B2-B 语句覆盖率 ≥ 70%（R25.1）。


---

### B3 — 评审主链路（Integration Node IT-4）

- [x] B3-A 评审任务核心（M03 任务模型 + 状态机 + Worker 入队）
  - _Branch: feat/m03-task-core_
  - _Depends: B2-A_
  - _Integration Node: IT-4_

  - [x] B3-A.1 编写 Flyway Migration `V30__m03_review_task.sql`
    - `review_task`（含 uk_review_task_no、uk_review_task_triple、idx_review_task_status / project_ts / finished_at）、`task_log` 表（按 design §7.2）
    - `review_task` 的 updated_at 触发器
    - _Requirements: R7.4, R8.3, R9.1, R9.7_

  - [x] B3-A.2 实现 ReviewTask Domain / DTO / Mapper
    - `task/domain/{ReviewTask,TaskLog}.java`、`task/dto/{ReviewTaskCreateRequest,ReviewTaskDTO,ReviewTaskQuery,RetryRequest,CancelRequest,TaskLogDTO,TaskLogQuery}.java`
    - `task/repository/{ReviewTaskMapper,TaskLogMapper}.java`，自定义 SQL：`findActiveByTriple`、`updateStatusOnlyIfFrom`（CAS 更新，避免并发覆盖）
    - _Requirements: R7.4, R8.1, R8.2, R8.3, R9.1_

  - [x] B3-A.3 实现 `ReviewTaskStatus` 枚举与状态机定义
    - `task/domain/ReviewTaskStatus.java`：8 个状态枚举
    - `task/domain/StateMachine.java`：`ALLOWED_EDGES` 集合（按 design §6.3.1 状态图：PENDING→FETCHING_DIFF / EXECUTION_FAILED；FETCHING_DIFF→STATIC_SCANNING / EXECUTION_FAILED；…；PASSED / FAILED_GATE / EXECUTION_FAILED → PENDING via retry）
    - `tryTransit(from, to)`：不在 ALLOWED_EDGES 抛 `BusinessException(VALIDATION_ERROR, "illegal transition")`
    - _Requirements: R9.1, R9.3_

  - [x] B3-A.4 实现 `ReviewTaskServiceImpl`
    - `create(req, idempotencyKey, trigger)`：
      - 若携带 `Idempotency-Key`，先查 Redis `idem:task:{key}` → 命中则返回缓存的 ReviewTaskDTO（R8.4）
      - 否则按 `(projectId, prId, commitSha)` 三元组查 active task → 命中且非 EXECUTION_FAILED 则按调用入口决定：webhook 直接返回（R7.4）/ 手动接口缺 idempotencyKey 抛 `TASK_DUPLICATED`（R8.3）
      - 校验 commitSha 与 prId 至少一项（R8.2）
      - 生成 task_no（`RT{yyyyMMdd}{seq}`），插入 review_task；DB 唯一冲突时再次幂等返回
      - 调用 `RedisStreamPublisher.enqueue("review-task-stream", {taskId, attempt:1})`
      - 写审计（trigger=CI_CD 时记录 triggerType）
    - `page` / `get`：列表按项目成员过滤；非成员请求 `get` 返回 403
    - `retry(id, reason)`：仅当 status ∈ {PASSED, FAILED_GATE, EXECUTION_FAILED} 允许；将 status 置 PENDING，attempt+1，重新入队；否则抛 `TASK_NOT_RETRYABLE`（R9.5）
    - `cancel(id, reason)`：仅 status=PENDING 允许；置 EXECUTION_FAILED 并写 task_log（R9.6）
    - `transitTo(id, target)`：StateMachine 校验 + CAS 更新（防止并发跳跃）；落库前后同步阶段日志
    - _Requirements: R7.3, R7.4, R7.6, R8.1, R8.2, R8.3, R8.4, R8.5, R9.2, R9.3, R9.4, R9.5, R9.6, R9.7_

  - [x] B3-A.5 实现 TaskStage 接口与抽象骨架
    - `task/worker/TaskStage.java`：`stage()`、`next(StageContext)`、`timeoutSeconds()`
    - `task/worker/StageContext.java`（含 taskId、projectId、attempt、worker 元数据）
    - `task/worker/TaskOrchestrator.java`：从 PENDING 起逐阶段执行，每阶段抛异常 / 超时 → `transitTo(EXECUTION_FAILED)` 并写 task_log（R9.2）；终态写 finishedAt 与 score（score 由 B3-F GateEngine 写入，Orchestrator 读取后回填）
    - 阶段实现类（FetchingDiff / StaticScanning / AiReviewing / GateEvaluating）暂用占位 NOOP 实现，由 B3-C / B3-D / B3-E / B3-F 替换
    - _Requirements: R9.1, R9.2, R9.7, R24.5_

  - [x] B3-A.6 实现 Redis Stream 消费者 `ReviewTaskConsumer`
    - 创建 consumer group `review-worker-group`（启动期 idempotent 创建）
    - `pollAndDispatch`：`XREADGROUP` 阻塞拉取（BLOCK 5000ms，COUNT 等于 `review.worker.concurrency`），分发给线程池
    - `review.worker.concurrency` 通过 Redis pub/sub 监听 `param-changed:review.worker.concurrency` 在 60s 内热更新（R24.3）
    - 每条消息执行 `taskOrchestrator.run(taskId)` → `XACK`；异常 → 不 ACK 由 XCLAIM 转移
    - 仅在 `worker` profile 下注册 Bean（`@Profile("worker")`）
    - _Requirements: R7.6, R9, R24.3, R24.4_

  - [x] B3-A.7 实现 `TaskRecoveryRunner` 启动期断点恢复
    - `ApplicationRunner`，仅 worker profile 下生效
    - 扫描所有 status ∈ {FETCHING_DIFF, STATIC_SCANNING, AI_REVIEWING, GATE_EVALUATING} 的任务 → 调 `transitTo(EXECUTION_FAILED)`，写 task_log（design §16.3 策略 A）
    - _Requirements: R24.4_

  - [x] B3-A.8 实现 `ReviewTaskController`
    - `POST /api/v1/review-tasks`（手动创建，已登录 + 项目成员；CI_CD 角色按 design §8.7 允许）
    - `GET /api/v1/review-tasks`、`GET /api/v1/review-tasks/{id}`、`GET /api/v1/review-tasks/{id}/logs`（`@RequirePermission(projectMember=true)`，service 内按任务 projectId 解析）
    - `POST /api/v1/review-tasks/{id}/retry`、`POST /api/v1/review-tasks/{id}/cancel`
    - 接口需读取 `Idempotency-Key` 头（R8.4）
    - _Requirements: R8, R9, R16.5_

  - [x] B3-A.9 提供 `TaskLogger` 工具类
    - `task/log/TaskLogger.java`：`info(taskId, stage, message)` / `warn(...)` / `error(taskId, stage, message, throwable)`，落 `task_log` 表，并 MDC 自动注入 `taskNo`
    - 暴露 Bean 给 B3-C / B3-D / B3-E / B3-F / B4-* 使用
    - _Requirements: R9.7, R24.5_

  - [ ]* B3-A.10 编写 ReviewTaskService 单元测试
    - 重复三元组返回同一 taskId；retry 在中间态抛 TASK_NOT_RETRYABLE；cancel 在非 PENDING 抛 VALIDATION_ERROR；状态机非法迁移抛异常
    - _Requirements: R7.4, R8.3, R9.3, R9.5_

  - [ ]* B3-A.11 编写 ReviewTask 集成测试
    - `@SpringBootTest` + Testcontainers：手动创建任务 → 入队 → 模拟 worker 处理（mock 各 stage）→ 状态终态可查
    - 越权：非项目成员 GET 返回 403
    - _Requirements: R8, R9_

  - [ ]* B3-A.12 编写 Property 1 属性测试：任务状态机迁移有向图
    - 文件：`src/test/java/com/acrqg/platform/property/TaskStateMachinePropertyTest.java`
    - `@Property(tries = 200)` `@ForAll ReviewTaskStatus from, @ForAll ReviewTaskStatus to`：断言 `StateMachine.tryTransit(from,to)` 成功 ⇔ `ALLOWED_EDGES.contains(Edge(from,to))`
    - _Property: P1_
    - _Requirements: R9.1, R9.3_

  - [ ]* B3-A.13 编写 Property 3 属性测试：任务幂等三元组
    - 文件：`src/test/java/com/acrqg/platform/property/TaskTripleIdempotentPropertyTest.java`
    - `@Property(tries = 50)`：随机三元组 (projectId, prId, commitSha) + 并发数 n∈[2,20]；通过 `Executors.newFixedThreadPool(n)` 并发提交 `reviewTaskService.create(...)`；断言：返回的 taskId 集合 size==1，DB 中 active 任务数 ≤ 1
    - 使用 Testcontainers 提供真实 PostgreSQL（依赖唯一约束）
    - _Property: P3_
    - _Requirements: R7.4, R8.3_

- [ ] B3-B Webhook 接入（M03）
  - _Branch: feat/m03-webhook_
  - _Depends: B3-A_
  - _Integration Node: IT-4_

  - [~] B3-B.1 实现 Webhook 签名校验器（GitHub / GitLab / Gitee）
    - `webhook/verifier/SignatureVerifier.java` 接口：`verify(secret, body, headers)`
    - `GithubSignatureVerifier`：`X-Hub-Signature-256`，HMAC-SHA256，使用 `MessageDigest.isEqual` 恒定时间比较
    - `GitlabSignatureVerifier`：`X-Gitlab-Token`（直接相等比较，因 GitLab 用明文 token）
    - `GiteeSignatureVerifier`：`X-Gitee-Token`
    - `webhook/verifier/SignatureVerifierFactory.java`
    - _Requirements: R7.1, R7.2, R23.3_

  - [~] B3-B.2 实现 `WebhookEventParser`
    - 解析 GitHub / GitLab / Gitee 三种 payload，统一抽出：provider、repositoryId、repoUrl、eventId、prId、commitSha、sourceBranch、targetBranch、eventType（`PR_OPENED`、`PR_SYNC`、`PUSH`、`PING`、`OTHER`）
    - 不支持的事件类型返回 `eventType=OTHER` 标记
    - _Requirements: R7.3, R7.5_

  - [~] B3-B.3 实现 `WebhookServiceImpl`
    - `handle(provider, headers, rawBody)`：
      - 解析 repoUrl → 查 RepositoryBinding → 取 webhookSecret 解密 → 调 SignatureVerifier；失败抛 `WEBHOOK_SIGNATURE_INVALID`（R7.2）
      - eventType ∈ {PING, OTHER} 返回 `{ignored:true}`，不创建任务（R7.5）
      - 计算幂等键 `idem:webhook:{provider}:{repositoryId}:{eventId}` → IdempotencyStore.putIfAbsent TTL=24h
      - 命中已存在事件 → 查询并返回已有任务
      - 新事件 → 调 `ReviewTaskService.create(parsedReq, null, WEBHOOK)` → 返回任务 DTO
    - 整体流程必须在 3s 内返回（R7.6）；耗时操作（实际任务执行）由 Stream 异步消费
    - _Requirements: R7.1, R7.2, R7.3, R7.4, R7.5, R7.6_

  - [~] B3-B.4 实现 `WebhookController`
    - `POST /api/v1/webhooks/git`：白名单不需 Bearer，但需要校验签名；接收原始 body（`@RequestBody String rawBody`）
    - 通过 `request.getHeader` 取 `X-Hub-Signature-256` 等头；通过查询参数或 path 推断 provider，或在 body 中识别（GitHub 有 `repository.html_url`、GitLab 有 `object_kind` 等）
    - _Requirements: R7.1, R7.6, R23.1_

  - [ ]* B3-B.5 编写 WebhookService 单元测试
    - 签名错误抛 `WEBHOOK_SIGNATURE_INVALID`；ping 事件 ignored=true；同一 eventId 第二次返回已有 taskId
    - _Requirements: R7.1, R7.2, R7.4, R7.5_

  - [ ]* B3-B.6 编写 Webhook 集成测试
    - 使用 GitHub 实际 payload fixture（`tests/resources/webhook/github-pr.json`）+ 计算签名头 → POST 接口创建任务；重复 POST 返回同一 taskId
    - _Requirements: R7_

- [ ] B3-C 代码差异解析（M04）
  - _Branch: feat/m04-diff_
  - _Depends: B3-A, B2-A_
  - _Integration Node: IT-4_

  - [~] B3-C.1 编写 Flyway Migration `V31__m04_diff_file.sql`
    - `diff_file` 表（按 design §7.2，含 uk_diff_file_task_path、idx_diff_file_task）
    - _Requirements: R10.2, R10.3_

  - [~] B3-C.2 实现 Diff Domain / DTO / Mapper
    - `diff/domain/{DiffFile,DiffHunk,ChangeType}.java`
    - `diff/dto/{DiffParseResult,ChangedFile,DiffViewDTO,DiffFetchRequest,DiffPayload}.java`
    - `diff/repository/DiffFileMapper.java`，自定义 SQL：`sumByTask(taskId)`、`changedFilesOf(taskId)`
    - _Requirements: R10.2, R10.3_

  - [~] B3-C.3 实现 ProviderClient.fetchDiff（在 B2-A.3 留白接口上完成）
    - GithubClient：`GET /repos/{owner}/{repo}/pulls/{prId}/files`（携带 Bearer token，分页拉取，使用 RestClient）
    - GitlabClient：`GET /projects/{id}/merge_requests/{iid}/changes`
    - GiteeClient：`GET /repos/{owner}/{repo}/pulls/{prId}/files`
    - 统一返回 `DiffPayload`（每文件 path、status、patch 文本）；网络错误 → `DiffFetchException`（包含 commitSha、provider）
    - _Requirements: R10.1, R10.4_

  - [~] B3-C.4 实现 `DiffParserImpl`
    - `parse(taskId)`：
      - 取 task → 取 RepositoryBinding（解密 accessToken）→ 调 `ProviderClient.fetchDiff`
      - 解析 unified diff patch 字符串：每文件抽出 hunks（`@@ -a,b +c,d @@`）→ 计算 addedLines / deletedLines；changeType 由 GitHub `status` 或 patch 头判定
      - `oversized = totalChangedLines > diff.maxLinesPerFile`（默认 5000）
      - 批量写 diff_file；任务级统计 changedFileCount / totalAddedLines / totalDeletedLines（如需独立表可写到 task 元数据 JSONB；本文件计算后由 ReviewTask 读取 sum）
      - 失败：调 `taskLogger.error` 写 task_log，抛 `DiffFetchException` 由 Orchestrator 转 EXECUTION_FAILED（R10.4）
    - 返回 `DiffParseResult`
    - _Requirements: R10.1, R10.2, R10.3, R10.4, R10.5_

  - [~] B3-C.5 实现 `FetchingDiffStage`（替换 B3-A.5 的占位）
    - `stage()=FETCHING_DIFF`，`next()`：调 `DiffParserImpl.parse(taskId)`；成功返回 STATIC_SCANNING；失败抛异常（由 Orchestrator 处理）
    - `timeoutSeconds`：默认 120，可读 `system_param.diff.fetch.timeout.seconds`
    - _Requirements: R9.1, R10_

  - [~] B3-C.6 提供 `DiffViewService.diffView(taskId)`（供 B4-B Report 使用）
    - 返回每个变更文件的 hunks（含 oldStart、newStart、行级 diff）；预留 `markIssueLines` 钩子（在 B4-B 接入）
    - _Requirements: R16.4_

  - [ ]* B3-C.7 编写 DiffParser 单元测试
    - mock ProviderClient 返回固定 patch 字符串；断言 addedLines/deletedLines 与 hunk 计算正确；oversized 命中时标记 true
    - 拉取失败抛 `DiffFetchException`；调用 taskLogger.error
    - _Requirements: R10.2, R10.3, R10.4, R10.5_

  - [ ]* B3-C.8 编写 Diff 集成测试
    - WireMock GitHub Files API → 真实 patch fixture → `parse(taskId)` 落库 N 条 diff_file，统计字段一致
    - _Requirements: R10_

  - [ ]* B3-C.9 编写 Property 7 属性测试：Diff 行数一致性
    - 文件：`src/test/java/com/acrqg/platform/property/DiffLineCountPropertyTest.java`
    - `@Property(tries = 200)`：自定义 `@Provide("randomDiffPayload")` 生成随机 hunk 列表（每 hunk 随机 + / - 行数）；调用 `diffParser.parseFromPayload(payload)`；断言：每 ChangedFile 满足 `addedLines + deletedLines == totalChangedLines`，任务级 `Σaddedlines==totalAddedLines`、`Σdeletedlines==totalDeletedLines`
    - _Property: P7_
    - _Requirements: R10.2, R10.3_

- [ ] B3-D 静态扫描适配（M05）
  - _Branch: feat/m05-scanner_
  - _Depends: B3-C, B1-D_
  - _Integration Node: IT-4_

  - [~] B3-D.1 实现 `StaticScannerAdapter` 接口与 `SeverityMapper`
    - `scanner/adapter/StaticScannerAdapter.java`：`name()`、`supportedLanguages()`、`isAvailable()`、`scan(ScanContext)`
    - `scanner/adapter/ScanContext.java`（task、changedFiles、workdir、scannerConfig）
    - `scanner/SeverityMapper.java`：按 design §11.2 的映射表把工具原始 severity 归一化为 `CRITICAL/HIGH/MEDIUM/LOW/INFO`；未知 → `INFO` + 打 WARN 日志
    - _Requirements: R11.2, R11.3_

  - [~] B3-D.2 实现 `ScannerProcessRunner` 与四个扫描器实现
    - `scanner/process/ScannerProcessRunner.java`：基于 `ProcessBuilder` 执行命令，超时 60s，捕获 stdout/stderr；支持 `{workdir}`/`{file}` 占位符替换；可选 docker exec 模式（系统参数 `scanner.runner.mode=docker` 时启用）
    - 四个 Adapter（`scanner/adapter/{CheckstyleScanner,EsLintScanner,PylintScanner,SemgrepScanner}.java`）：从 `scanner_config` 读取 command 模板；调用 ScannerProcessRunner；调用对应 ResultParser 转为 List<CodeIssue>
    - 四个 ResultParser（`scanner/parser/{CheckstyleXmlParser,EsLintJsonParser,PylintJsonParser,SemgrepJsonParser}.java`）：将原始 XML/JSON 反序列化并按 SeverityMapper 归一化
    - `isAvailable`：执行 `--version` 探测；若失败返回 false 并写 INFO 日志（不阻塞其他扫描器）
    - _Requirements: R11.1, R11.2, R11.3, R11.4_

  - [~] B3-D.3 实现 `ScannerOrchestrator`
    - 取 task → project.language → 选择 supportedLanguages 包含 language 的 adapters + Semgrep（通用安全）
    - 仅传入 `diff_file` 中 oversized=false 的变更文件路径（R11.5）
    - `parallelStream()` 调每个 adapter；任一异常 → `taskLogger.warn` 记录后 `Stream.empty()`，不影响其他（R11.4）
    - 批量持久化 CodeIssue（source=SAST、status=NEW），事务一次提交
    - _Requirements: R11.1, R11.2, R11.4, R11.5_

  - [~] B3-D.4 实现 `code_issue` 表 Migration `V32__m05_code_issue.sql`
    - `code_issue`、`issue_history`、`issue_comment` 表 + 全部索引（design §7.2）
    - 注意：与 B4-A 的 issue 模块共享此表；在 B3-D 的 V32 中创建一次，B4-A 不再重复创建（仅追加 service / controller）
    - _Requirements: R11.2, R16.2, R17_

  - [~] B3-D.5 实现 CodeIssue Domain / DTO / Mapper（共用）
    - `issue/domain/{CodeIssue,IssueHistory,IssueComment}.java`、`issue/dto/{CodeIssueDTO,IssueQuery,IssueStatusChangeRequest,IssueCommentDTO}.java`
    - `issue/repository/{CodeIssueMapper,IssueHistoryMapper,IssueCommentMapper}.java`
    - 提供给 B3-D（写入 SAST 问题）、B3-E（写入 AI 问题）、B3-F（聚合查询）、B4-A（状态流转）共用
    - _Requirements: R11.2, R12.3, R16.2, R17_

  - [~] B3-D.6 实现 `StaticScanningStage`（替换占位）
    - `stage()=STATIC_SCANNING`，`next()`：调 ScannerOrchestrator.scan；返回 AI_REVIEWING
    - _Requirements: R9.1, R11_

  - [ ]* B3-D.7 编写扫描器单元测试
    - 使用真实 fixture 文件（`tests/resources/scanner/checkstyle.xml` 等）→ Parser 单测；SeverityMapper 全枚举映射
    - mock ScannerProcessRunner 返回 fixture，验证 Adapter 输出 CodeIssue 字段完整
    - _Requirements: R11.2, R11.3_

  - [ ]* B3-D.8 编写 Scanner 集成测试
    - 使用真实 ESLint（CI 环境预装 `npm i -g eslint`）扫描一个示例 JS 文件；断言至少产生 1 条 CodeIssue 且 source=SAST
    - 单扫描器失败：故意把 command 改为不存在的二进制，断言其他扫描器仍执行
    - _Requirements: R11.1, R11.4, R11.5_

- [ ] B3-E AI 辅助评审（M06）
  - _Branch: feat/m06-ai_
  - _Depends: B3-C, B1-D_
  - _Integration Node: IT-4_

  - [~] B3-E.1 实现 `SensitiveFilter`
    - `ai/filter/SensitiveFilter.java` 接口：`filter(AiReviewPayload raw)` → `FilteredPayload`
    - `ai/filter/DefaultSensitiveFilter.java`：
      - 路径白名单：`.env`、`*.env.*`、`*.pem`、`*.key`、`*.crt`、`*.p12`、`*.jks`、`secrets/**`、`config/secret-*` 整文件跳过
      - Token 正则替换 `***REDACTED***`：`AKIA[0-9A-Z]{16}`、`sk-[A-Za-z0-9]{32,}`、`gh[pousr]_[A-Za-z0-9]{36,}`、`(?i)(password|secret|token|api[_-]?key)\\s*[:=]\\s*['\"][^'\"]+['\"]`
      - 哈希前后比对：原始与过滤后 SHA-256 一致且原始命中过滤规则 → 抛 `SensitiveFilterFailureException`
    - 工具：`anyHit(raw)`、`hash(payload)`
    - _Requirements: R12.2, R23.4_

  - [~] B3-E.2 实现 `AiReviewClient` 与 JSON Schema 校验
    - `ai/client/AiReviewClient.java`、`ai/client/HttpAiReviewClient.java`：`RestClient` 调用 OpenAI 兼容 `/v1/chat/completions`，超时由 `ai.review.timeout.seconds`（默认 60）控制；4xx 抛 `BusinessException`，5xx / 超时抛 `AiServiceUnavailableException`
    - apiKey 通过 `AdminService.decryptModelApiKey(modelId)` 取得
    - `ai/schema/AiReviewSchemaValidator.java`：使用 `networknt/json-schema-validator`，校验响应符合 design §12.2 schema；不通过 → 抛 `SchemaValidationException`
    - JSON Schema 文件：`src/main/resources/ai/review-response.schema.json`
    - _Requirements: R12.1, R12.3, R12.4, R12.5_

  - [~] B3-E.3 实现 Prompt 模板与上下文构造
    - `ai/prompt/PromptBuilder.java`：按 design §12.1 模板构造 system + user 两段；注入 language、metrics 列表、fileList、filteredDiff
    - 限制单次 payload ≤ 模型 max_tokens（按系统参数 `ai.review.maxInputChars` 截断）
    - _Requirements: R12.1_

  - [~] B3-E.4 实现 `AiReviewService.execute(taskId)`（含降级）
    - 流程：取 task + diff → 取 enabled model_config → SensitiveFilter.filter → PromptBuilder.build → AiReviewClient.review
    - 异常处理：
      - `SensitiveFilterFailureException` → 中止 AI 调用，写 task_log(ERROR)，gate_result.summary.aiAvailable=false（R12.2）
      - 超时 / 5xx (`AiServiceUnavailableException`) → 写 task_log(WARN)，aiAvailable=false（R12.5）
      - Schema 校验失败 → 写 task_log(WARN)，丢弃响应（R12.4）
    - 成功：批量持久化 CodeIssue（source=AI、status=NEW、含 confidence）
    - 计算 ai_risk_score（design §12.5 公式）并写入 `gate_result_summary` 缓存（在 B3-F 实现 gate_result 表，本任务先暂存到 task 元数据 JSONB 或新增 `ai_risk_score` 字段，由 B3-F 在 evaluate 时一并消费；推荐：在 task_log 中写 `score=ai_risk_score` 的结构化条目，B3-F 直接读取最新的）
    - _Requirements: R12.1, R12.2, R12.3, R12.4, R12.5, R12.6_

  - [~] B3-E.5 实现 `AiReviewingStage`（替换占位）
    - `stage()=AI_REVIEWING`，`next()`：调 `aiReviewService.execute(taskId)`；任一降级路径都返回 `GATE_EVALUATING`（不抛异常）；仅在不可恢复内部错误时抛异常（R12.5）
    - _Requirements: R9.1, R12.5_

  - [~] B3-E.6 实现 `AiServiceHealthIndicator`（替换 B0-A.13 占位）
    - 周期性（30s）调用 enabled model 的轻量探活 endpoint；缓存结果；`/health` 中暴露 `aiService` 子组件
    - _Requirements: R24.6_

  - [ ]* B3-E.7 编写 AI 单元测试
    - SensitiveFilter：路径命中跳过；token 正则替换；哈希一致抛异常
    - AiReviewClient：4xx / 5xx / 超时分支
    - AiReviewSchemaValidator：合法 / 缺字段 / 类型错误三类
    - ai_risk_score 计算公式边界（issues 为空 = 0；max+avg 加权 0.6/0.4）
    - _Requirements: R12.2, R12.3, R12.4, R12.5, R12.6_

  - [ ]* B3-E.8 编写 AI 集成测试（含降级三类用例）
    - WireMock：返回 200 + 合法 JSON、200 + Schema 不合法、5xx、超时
    - 期望：合法时落库 N 条 source=AI 的 CodeIssue；其他三类不阻塞任务，aiAvailable=false 标记到 summary
    - _Requirements: R12.1, R12.3, R12.4, R12.5_

  - [ ]* B3-E.9 编写 Property 5 属性测试：SensitiveFilter Token 过滤
    - 文件：`src/test/java/com/acrqg/platform/property/SensitiveFilterTokenPropertyTest.java`
    - 自定义 `@Provide("payloadWithRandomTokens")` 生成在文本中嵌入随机 AKIA / sk- / gh*_ token 的 payload
    - `@Property(tries = 300)`：调用 `sensitiveFilter.filter(raw)`；若抛 `SensitiveFilterFailureException` → 视为合法失败；否则断言 `TOKEN_PATTERNS` 中任一正则在过滤后正文中均不匹配
    - _Property: P5_
    - _Requirements: R12.2, R23.3, R23.4_

- [ ] B3-F 质量门禁规则引擎与判定（M07）
  - _Branch: feat/m07-gate-engine_
  - _Depends: B3-D, B3-E, B2-B_
  - _Integration Node: IT-4_

  - [~] B3-F.1 编写 Flyway Migration `V33__m07_gate_result.sql`
    - `gate_result` 表（含 uk_gate_result_task、idx_gate_result_status，按 design §7.2）
    - _Requirements: R14.3, R14.4, R14.5, R14.8_

  - [~] B3-F.2 实现 GateResult Domain / DTO / Mapper
    - `gate/domain/{GateResult,GateResultStatus,RuleEval}.java`
    - `gate/dto/{GateResultDTO,RuleEvalDTO}.java`
    - `gate/repository/GateResultMapper.java`
    - _Requirements: R14.3, R14.4, R14.8_

  - [~] B3-F.3 实现 6 个 MetricCollector
    - `gate/collector/CriticalIssueCountCollector.java`：按 design §10.2 SQL 统计 severity ∈ (CRITICAL,HIGH) 且 status≠FALSE_POSITIVE 的 CodeIssue 数（R14.1, R17.6）
    - `SecurityIssueCountCollector`：rule_code 前缀或正则匹配安全规则（如 Semgrep `security.*`）
    - `TestCoverageCollector`：从 task 元数据或外部 coverage report 读取（占位实现：固定取 75；接受未来扩展）
    - `DuplicateRateCollector`：占位实现（固定取 0），并在日志中标记 `not implemented yet`
    - `AiRiskScoreCollector`：从 B3-E 写入的 task 元数据 / task_log 取最新 ai_risk_score；aiAvailable=false 时返回 0
    - `NewIssueCountCollector`：当前任务 status=NEW 的 CodeIssue 数
    - 全部 `@Component`，由 Spring 注入到 `MetricCollectorRegistry`
    - _Requirements: R14.1, R12.6, R17.6_

  - [~] B3-F.4 实现 `OperatorEvaluator` 与 `GateRuleEngine`
    - `gate/engine/DefaultOperatorEvaluator.java`：按 design §10.3 用 BigDecimal 比较 6 种 operator
    - `gate/engine/GateRuleEngine.java`：
      - 取 enabled QualityGate；遍历每条 enabled rule → MetricCollector.collect → OperatorEvaluator.compare
      - 任一 BLOCKER 失败 → status=FAILED；否则 PASSED（R14.3, R14.4）
      - 计算 score（design §10.4 公式：100 - Σ weight × penalty，权重从 system_param 读取）
      - 持久化 GateResult（含 summary：failedRules、passedRules、aiAvailable）
    - _Requirements: R13.6, R14.1, R14.2, R14.3, R14.4, R14.5, R14.8_

  - [~] B3-F.5 实现 `GateEvaluatingStage`（替换占位）
    - `stage()=GATE_EVALUATING`，`next()`：调 `gateRuleEngine.evaluate(taskId)`；GateResultStatus.FAILED → 转 FAILED_GATE；PASSED → 转 PASSED；同时回填 review_task.score / ai_available
    - _Requirements: R9.1, R14.3, R14.4_

  - [~] B3-F.6 实现 GateResult 查询接口
    - `GET /api/v1/review-tasks/{id}/gate-result`：`@RequirePermission(projectMember=true)`，返回 GateResultDTO（含 failedRules / passedRules 明细）
    - _Requirements: R14.8, R16.1_

  - [ ]* B3-F.7 编写 GateRuleEngine 单元测试矩阵
    - 6 metric × 5 operator × {BLOCKER,WARN} 全组合（仅以 metric=critical_issue_count 为代表展开）
    - score 公式边界：所有规则通过 → 100；多 BLOCKER 失败夹紧 0；权重生效
    - _Requirements: R14.2, R14.3, R14.4, R14.5_

  - [ ]* B3-F.8 编写 GateEngine 集成测试
    - 端到端：种子任务 + 种子 CodeIssue → 触发 GateEvaluatingStage → 落库 GateResult；review_task.status=PASSED|FAILED_GATE
    - _Requirements: R14_

  - [ ]* B3-F.9 编写 Property 4 属性测试：BLOCKER ⇔ FAILED
    - 文件：`src/test/java/com/acrqg/platform/property/GateBlockerPropertyTest.java`
    - 自定义 `@Provide("randomGateRules")`、`@Provide("randomMetricValues")` 生成随机规则集与指标值
    - `@Property(tries = 500)`：用 stub 的 MetricCollectorRegistry 注入随机指标 → engine.evaluate(taskId) → 断言 `result.status == FAILED ⇔ ∃ enabled rule r: !compare(actual,r.op,r.threshold) ∧ r.severity==BLOCKER`
    - _Property: P4_
    - _Requirements: R13.3, R14.2, R14.3, R14.4_

#### Integration Node IT-4

- 端到端场景（design §9）：
  - **SD-1**：模拟 GitHub PR webhook → 签名校验 → IdempotencyStore 去重 → ReviewTaskService.create 入队 → 同一 eventId 第二次返回相同 taskId（R7.1, R7.4）。3 秒内 Webhook 响应（R7.6）。
  - **SD-2**：完整链路 PENDING → FETCHING_DIFF → STATIC_SCANNING → AI_REVIEWING → GATE_EVALUATING → PASSED/FAILED_GATE，每阶段有 task_log（R9.1, R9.7）。
  - **SD-3**：AI 三类降级（超时 / 5xx / Schema 错），任务推进至 GATE_EVALUATING 而非 EXECUTION_FAILED（R12.5）。
  - **SD-4**：BLOCKER 失败 → review_task.status=FAILED_GATE 与 GateResult.status=FAILED 一致；GateResult.summary 含 failedRules / passedRules 明细。
- 越权用例（design §15.6）：
  - DEVELOPER 非项目成员调用 `GET /review-tasks/{id}` → 403。
  - 非项目成员调用 `POST /review-tasks` → 403。
  - 非项目成员调用 `GET /review-tasks/{id}/gate-result` → 403。
- 覆盖率门槛：B3 全部分支语句覆盖率 ≥ 70%；jqwik P1 (200 次)、P3 (50 次)、P4 (500 次)、P5 (300 次)、P7 (200 次) 全部通过。


---

### B4 — 报告 / 看板 / 通知 / 回写（Integration Node IT-5）

- [ ] B4-A 问题状态流转（M08 Issue）
  - _Branch: feat/m08-issue_
  - _Depends: B3-F_
  - _Integration Node: IT-5_

  - [~] B4-A.1 实现 `CodeIssueStatus` 枚举与 `IssueStateMachine`
    - `issue/domain/CodeIssueStatus.java`：6 个状态
    - `issue/domain/IssueStateMachine.java`：`ALLOWED_ISSUE_EDGES` 集合（按 R17.1：NEW→CONFIRMED/FALSE_POSITIVE；CONFIRMED→PENDING_VERIFY/CLOSED；PENDING_VERIFY→CLOSED/REOPENED；CLOSED→REOPENED；REOPENED→CONFIRMED/FALSE_POSITIVE）
    - 提供 `tryTransit(from, to)` 与边集合查询方法（供属性测试使用）
    - _Requirements: R17.1_

  - [~] B4-A.2 实现 `IssueServiceImpl`
    - `page(IssueQuery)`：按 severity / status / source / filePath 过滤；按 severity 降序、createdAt 升序（R16.2, R16.3）
    - `get(id)`：返回 CodeIssueDTO（包含历史与评论可选 join）
    - `changeStatus(id, request)`：
      - 校验当前用户是任务所属项目的成员；DEVELOPER 角色尝试操作不属于其参与任务的 issue → 抛 `VALIDATION_ERROR`（R17.5）
      - 校验目标状态在 ALLOWED_ISSUE_EDGES 内 → 否则 `VALIDATION_ERROR`（R17.2）
      - 当目标 ∈ {FALSE_POSITIVE, CLOSED} 强制 comment 长度 ≥ 5 → 否则 `VALIDATION_ERROR`（R17.3）
      - 写入 issue_history（from / to / operator / comment / changedAt）（R17.4）
    - `addComment(id, content)`：写 issue_comment
    - _Requirements: R17.1, R17.2, R17.3, R17.4, R17.5, R17.6（后者在 B3-F MetricCollector 已落实，本任务保证 status 改为 FALSE_POSITIVE 后立即对下次 evaluate 生效）_

  - [~] B4-A.3 实现 `IssueController`
    - `GET /api/v1/review-tasks/{id}/issues`、`GET /api/v1/issues/{id}`、`PATCH /api/v1/issues/{id}/status`、`POST /api/v1/issues/{id}/comments`
    - 全部 `@RequirePermission(projectMember=true)`（projectId 通过 task_id 关联解析）
    - _Requirements: R16.2, R17_

  - [ ]* B4-A.4 编写 IssueService 单元测试
    - 合法 / 非法迁移；FALSE_POSITIVE 缺少 comment 抛 VALIDATION_ERROR；DEVELOPER 操作非自身任务问题抛 VALIDATION_ERROR
    - _Requirements: R17_

  - [ ]* B4-A.5 编写 Issue 集成测试
    - 完整流转：NEW → CONFIRMED → PENDING_VERIFY → CLOSED → REOPENED；issue_history 记录全过程；下一次 GateEngine.evaluate 时 FALSE_POSITIVE 不计入 critical_issue_count
    - _Requirements: R17.4, R17.6_

  - [ ]* B4-A.6 编写 Property 2 属性测试：问题状态迁移合法集
    - 文件：`src/test/java/com/acrqg/platform/property/IssueStateMachinePropertyTest.java`
    - `@Property(tries = 200)` `@ForAll CodeIssueStatus from, to, @ForAll @StringLength(min=5,max=200) String comment`：从持久化的 issue 起步；断言 `expectOk == ALLOWED_ISSUE_EDGES.contains(Edge(from,to)) ∧ (to∉{FP,CLOSED} ∨ comment.trim.length≥5)`；与实际行为一致
    - _Property: P2_
    - _Requirements: R17.1, R17.2, R17.3_

- [ ] B4-B 评审报告聚合（M08 Report）
  - _Branch: feat/m08-report_
  - _Depends: B4-A_
  - _Integration Node: IT-5_

  - [~] B4-B.1 实现 Report DTO
    - `report/dto/{ReviewReportDTO,TaskOverviewDTO,IssueCountAggDTO,GateResultSummaryDTO}.java`
    - 字段对齐 R16.1：taskOverview（taskNo / PR / commit / status / score）、gateResultSummary、issueCounts（按 severity 与 source 聚合）、aiAvailability
    - _Requirements: R16.1_

  - [~] B4-B.2 实现 `ReportServiceImpl`
    - `report(taskId)`：聚合 review_task + gate_result + code_issue 分组计数（按 severity / source）→ 一次 SQL 或多次小查询；缓存到 Caffeine（key=taskId, TTL=10s）
    - `diffView(taskId)`：调 `DiffViewService.diffView`（B3-C.6 提供），并叠加每行关联问题数（通过 code_issue.line_no 与 hunks 行匹配）
    - `logs(taskId, query)`：分页查询 task_log，支持 stage / level 过滤
    - 全部接口在 service 层校验项目成员
    - _Requirements: R16.1, R16.2, R16.4, R16.5, R16.6_

  - [~] B4-B.3 实现 `ReportController`
    - `GET /api/v1/review-tasks/{id}/report`、`GET /api/v1/review-tasks/{id}/diff`、`GET /api/v1/review-tasks/{id}/logs`
    - 全部 `@RequirePermission(projectMember=true)`
    - _Requirements: R16_

  - [ ]* B4-B.4 编写 ReportService 单元测试
    - mock Mapper 返回固定数据，断言聚合后的 IssueCountAggDTO 字段；diffView 行级问题标注正确
    - _Requirements: R16.1, R16.2, R16.4_

  - [ ]* B4-B.5 编写 Report 集成测试
    - 种子 1 个任务 + 200 个 CodeIssue + 多文件 diff + task_log；调用 `/report` 返回正确聚合；`/logs` 按 stage 过滤
    - _Requirements: R16_

- [ ] B4-C 项目质量看板（M08 Dashboard）
  - _Branch: feat/m08-dashboard_
  - _Depends: B3-F_
  - _Integration Node: IT-5_

  - [~] B4-C.1 实现 Dashboard DTO
    - `dashboard/dto/{DashboardQuery,QualityTrendDTO,TrendPointDTO,RiskFileDTO}.java`
    - 字段：taskCount、passRate、avgScore、avgDurationSeconds（每日聚合）；RiskFileDTO 含 filePath、issueCount、weightedScore
    - _Requirements: R18.1, R18.3_

  - [~] B4-C.2 实现 `DashboardServiceImpl`
    - `trend(projectId, query)`：startDate 与 endDate 跨度 ≤ 365 天，否则 `VALIDATION_ERROR`（R18.2）；按日 GROUP BY 聚合 review_task + gate_result，返回时间序列；optional `branch` 过滤
    - `topRiskFiles(projectId, topN)`：按 file_path 聚合 code_issue（severity 加权：CRITICAL=5, HIGH=3, MEDIUM=2, LOW=1, INFO=0.5）→ 取 TopN
    - `@RequirePermission(projectMember=true)` 由 controller 统一校验
    - 性能：基于近 30 天数据 P95 ≤ 2s；为 review_task(project_id, created_at) 已建索引（V30）
    - _Requirements: R18.1, R18.2, R18.3, R18.4, R18.5_

  - [~] B4-C.3 实现 `DashboardController`
    - `GET /api/v1/dashboard/projects/{id}/quality-trend`、`GET /api/v1/dashboard/projects/{id}/top-risk-files`
    - `@RequirePermission(projectMember=true)`
    - _Requirements: R18.4_

  - [ ]* B4-C.4 编写 Dashboard 单元测试
    - 跨度 366 天 → VALIDATION_ERROR；无数据时返回空时间序列
    - _Requirements: R18.2_

  - [ ]* B4-C.5 编写 Dashboard 集成测试
    - 种子 30 天数据；调用 trend 返回 30 个数据点；TopN 按加权分数排序正确
    - 越权：非项目成员调用返回 403
    - _Requirements: R18_

- [ ] B4-D 通知中心（M09 Notification）
  - _Branch: feat/m09-notification_
  - _Depends: B3-A, B3-F_
  - _Integration Node: IT-5_

  - [~] B4-D.1 编写 Flyway Migration `V50__m09_notification.sql`
    - `notification` 表 + `idx_notification_user_read`（按 design §7.2）
    - _Requirements: R19.3, R19.5_

  - [~] B4-D.2 实现 Notification Domain / DTO / Mapper
    - `notification/domain/Notification.java`、`notification/dto/{NotificationDTO,NotificationQuery}.java`
    - `notification/repository/NotificationMapper.java`，自定义 SQL：`countUnreadByUser`、`pageByUser(query)`、`markRead(id, userId)`
    - 通知类型枚举：`TASK_PASSED`、`TASK_FAILED_GATE`、`TASK_EXEC_FAILED`、`WAIVER_SUBMITTED`、`WAIVER_APPROVED`、`WAIVER_REJECTED`、`ISSUE_ASSIGNED`
    - _Requirements: R19_

  - [~] B4-D.3 实现 `NotificationServiceImpl`
    - `publishTaskStatusChanged(task)`：当 task.status 转为 PASSED / FAILED_GATE / EXECUTION_FAILED → 给任务发起人 + 项目所有 PROJECT_ADMIN 各发一条通知（R19.1）
    - `publishGateWaiverSubmitted(waiver)`：给项目所有 PROJECT_ADMIN 与 REVIEWER 发审批通知（R19.2）；预留 `publishGateWaiverApproved/Rejected`
    - `page(query)`：按 read / type / 分页（R19.3）
    - `markRead(id)`：先校验归属 currentUser；非本人 → 403；否则置 read_flag=true（R19.4）
    - `archiveOldNotifications`：定时任务（@Scheduled）90 天前未读通知归档（R19.5）
    - _Requirements: R19.1, R19.2, R19.3, R19.4, R19.5_

  - [~] B4-D.4 接入领域事件
    - 在 `task` 模块发布 `TaskStatusChangedEvent`（B3-A 已发布或本任务补 publisher）；在 `gate` 模块发布 `GateWaiverSubmittedEvent`（B4-E 完成）
    - `notification/event/NotificationEventListener.java`：`@EventListener` 异步消费，调对应 publish 方法
    - _Requirements: R19.1, R19.2_

  - [~] B4-D.5 实现 `NotificationController`
    - `GET /api/v1/notifications`、`PATCH /api/v1/notifications/{id}/read`、`GET /api/v1/notifications/unread-count`
    - 已登录可见，service 内按当前用户隔离
    - _Requirements: R19.3, R19.4_

  - [ ]* B4-D.6 编写 Notification 单元测试
    - 状态变更触发对应 publish；非本人 markRead 返回 403；归属校验
    - _Requirements: R19.1, R19.4_

  - [ ]* B4-D.7 编写 Notification 集成测试
    - 模拟 task 终态 → 通知列表出现一条 TASK_PASSED；非本人 markRead 返回 403
    - _Requirements: R19_

- [ ] B4-E 状态回写与门禁豁免（M09 Writeback + M07 Waiver）
  - _Branch: feat/m09-writeback_
  - _Depends: B3-F, B2-A_
  - _Integration Node: IT-5_

  - [~] B4-E.1 编写 Flyway Migration `V51__m07_gate_waiver.sql`
    - `gate_waiver` 表 + `idx_gate_waiver_task` + `uk_gate_waiver_active`（按 design §7.2）
    - _Requirements: R15.1, R15.6_

  - [~] B4-E.2 实现 GateWaiver Domain / DTO / Mapper
    - `gate/waiver/domain/GateWaiver.java`、`gate/waiver/dto/{GateWaiverSubmitRequest,GateWaiverApproveRequest,GateWaiverDTO}.java`
    - `gate/waiver/repository/GateWaiverMapper.java`，自定义 SQL：`findActiveByTask(taskId)`、`expireOverdue()`
    - _Requirements: R15.1, R15.6_

  - [~] B4-E.3 实现 `GateWaiverServiceImpl`
    - `submit(taskId, req)`：
      - 校验 task.status=FAILED_GATE（R15.1）；reason ≥ 10 字符（R15.2）；expireAt 必须 future
      - 检查活跃 waiver（PENDING / 未过期 APPROVED）→ 抛 `WAIVER_DUPLICATED`（R15.6）
      - 插入 status=PENDING；发布 `GateWaiverSubmittedEvent`（R19.2 联动）
    - `approve(waiverId, req)`：
      - 调用者 `@RequirePermission(projectRole={PROJECT_ADMIN,REVIEWER})`
      - approved=true → status=APPROVED + 把 GateResult.status 置 WAIVED（R15.3）→ 触发 Writeback（state=success，描述含"已豁免"，R15.4）
      - approved=false → status=REJECTED
      - 写审计：申请人、审批人、审批意见、expireAt（R15.5）
    - `expireOverdueJob`：定时任务（每 5 分钟），把 expireAt 过期的 PENDING / APPROVED 置 EXPIRED
    - _Requirements: R15.1, R15.2, R15.3, R15.4, R15.5, R15.6_

  - [~] B4-E.4 实现 ProviderClient.postCommitStatus 三实现
    - 在 B2-A.3 留白接口上完成：GitHub `POST /repos/{owner}/{repo}/statuses/{sha}`、GitLab `POST /projects/{id}/statuses/{sha}`、Gitee `POST /repos/{owner}/{repo}/statuses/{sha}`
    - 状态映射：PASSED/WAIVED → success；FAILED → failure
    - 描述包含 taskNo、score、failedRules 数量、报告 URL（R20.2）
    - _Requirements: R20.1, R20.2_

  - [~] B4-E.5 实现 `WritebackServiceImpl`
    - `writeback(taskId, gateResult)`：
      - 取 RepositoryBinding（解密 accessToken）→ 取 ProviderClient
      - provider 不支持 commit status → 跳过并写 task_log(INFO)（R20.5）
      - 调 postCommitStatus；4xx → 不重试，写 task_log(ERROR)（R20.3）；5xx / 超时 → 重试 1s/5s/25s 至多 3 次（R20.4 + R14.7）
      - 重试期间不修改 review_task.status 终态；3 次失败后写 task_log(ERROR) + 标记 `manualRetryAvailable`
    - 通过 Spring Retry `@Retryable(value=AiServiceUnavailableException.class, maxAttempts=3, backoff=@Backoff(delay=1000, multiplier=5))`
    - _Requirements: R14.6, R14.7, R20.1, R20.3, R20.4, R20.5_

  - [~] B4-E.6 实现 `GateWaiverController`
    - `POST /api/v1/review-tasks/{id}/gate-waivers`：`@RequirePermission(projectMember=true)`
    - `POST /api/v1/gate-waivers/{id}/approve`：`@RequirePermission(projectRole={PROJECT_ADMIN,REVIEWER})`
    - `GET /api/v1/review-tasks/{id}/gate-waivers`：列表
    - _Requirements: R15_

  - [~] B4-E.7 在 GateEvaluatingStage 之后挂接 Writeback
    - 修改 B3-F.5 的 `GateEvaluatingStage.next()`：在 transit 终态后 publish `GateEvaluatedEvent`（包含 taskId、status）
    - `WritebackEventListener`（writeback 模块）：`@EventListener @Async` 接收事件 → 调用 `WritebackService.writeback`
    - _Requirements: R14.6, R20_

  - [~] B4-E.8 提供手动重试回写的接口
    - `POST /api/v1/review-tasks/{id}/writeback/retry`：`@RequirePermission(projectRole={PROJECT_ADMIN,REVIEWER})`
    - 仅当上次回写状态为失败时允许（通过 task_log 中最近一条 WRITEBACK ERROR 标记位判断）
    - _Requirements: R14.7_

  - [ ]* B4-E.9 编写 GateWaiver 单元测试
    - reason 不足 10 字符抛 VALIDATION_ERROR；存在活跃 waiver 抛 WAIVER_DUPLICATED；非 FAILED_GATE 状态提交抛 VALIDATION_ERROR；approved=true 后 GateResult 置 WAIVED
    - _Requirements: R15.1, R15.2, R15.3, R15.6_

  - [ ]* B4-E.10 编写 Writeback 单元测试
    - WireMock 模拟 4xx → 不重试；5xx → 重试 3 次按 1/5/25s 间隔；不支持平台跳过
    - _Requirements: R20.3, R20.4, R20.5_

  - [ ]* B4-E.11 编写豁免 + 回写集成测试（覆盖 SD-5）
    - 任务 FAILED_GATE → DEVELOPER 提交 waiver → PROJECT_ADMIN approve → GateResult.status=WAIVED → commit status 回写 success（描述含"已豁免"）→ 通知申请人
    - _Requirements: R15, R20_

#### Integration Node IT-5

- 端到端场景（design §9）：
  - **SD-5**：豁免审批闭环（提交 → 审批 → GateResult.WAIVED → Writeback success（含"已豁免"）→ 通知申请人）。
  - **SD-4 续段**：BLOCKER 失败任务自动触发 Writeback；4xx 不重试；5xx 重试 1/5/25s 三次。
  - 报告页：`GET /review-tasks/{id}/report` 在 100 并发下 P95 ≤ 2s（R16.6，本节点先用 30 并发烟测，B6 完整压测）。
  - 看板：trend 30 天数据 P95 ≤ 2s（R18.5）。
  - 通知：状态终态 → 申请人与项目管理员收到通知；markRead 仅本人有效。
- 越权用例（design §15.6）：
  - 非本人 markRead → 403。
  - 非项目成员查询 issue / report / dashboard → 403。
  - 非 PROJECT_ADMIN/REVIEWER 调用 `gate-waivers/{id}/approve` → 403。
- 覆盖率门槛：B4 全部分支语句覆盖率 ≥ 70%（R25.1）；jqwik P2（200 次）通过。


---

### B5 — 前端页面拼装（跨批次追踪后端）

> 单一顶层任务 `feat/web-pages` 由 1~2 名 subagent 持续追踪后端落地的里程碑（IT-2 / IT-3 / IT-4 / IT-5），按需拼装 UI-001 ~ UI-010。每个子任务标注其依赖的后端批次。

- [ ] B5-A 前端页面持续集成
  - _Branch: feat/web-pages_
  - _Depends: B0-B（同时持续追踪 B1 / B2 / B3 / B4）_
  - _Integration Node: IT-2 / IT-3 / IT-4 / IT-5（分阶段验收）_

  - [~] B5-A.1 实现 `src/api/*.ts` 全部 axios 客户端
    - 按 design.md §5.1 与 §8.7 接口清单实现：`auth.ts`、`user.ts`、`project.ts`、`repository.ts`、`reviewTask.ts`、`issue.ts`、`report.ts`、`gate.ts`、`gateWaiver.ts`、`dashboard.ts`、`notification.ts`、`admin.ts`
    - 与后端 DTO 类型对齐 `src/types/api.d.ts`
    - _Requirements: R1, R3~R22（前端访问层覆盖）_

  - [~] B5-A.2 实现 LoginPage（UI-001）
    - 表单（用户名 / 密码）+ 调用 `auth.login`；成功后跳转 `redirect` 或 `/dashboard`
    - 错误码 `AUTH_INVALID_CREDENTIALS` / `AUTH_ACCOUNT_DISABLED` 显示对应中文提示
    - _Requirements: R1.1, R1.2, R1.3_

  - [~] B5-A.3 实现 DashboardPage（UI-002）+ 全局头部
    - 调用 `dashboard.trend` + `dashboard.topRiskFiles`（依赖 B4-C）
    - 全局头部：项目切换器、未读通知红点（轮询 `/notifications/unread-count`，30s）、用户菜单（登出）
    - _Requirements: R18, R19_

  - [~] B5-A.4 实现项目相关页面（UI-003 / UI-004）
    - `ProjectListPage`：列表 + 关键字搜索 + 创建按钮（仅 PROJECT_ADMIN/SYSTEM_ADMIN）
    - `ProjectDetailPage`：项目信息 + 成员列表 + 仓库绑定卡片
    - 依赖 B1-C / B2-A
    - _Requirements: R4, R5, R6_

  - [~] B5-A.5 实现仓库绑定页面（UI-005）
    - `RepositoryBindingPage`：provider 选择 + 仓库 URL + accessToken（密码框）+ webhookSecret + "测试连通性" 按钮 + "保存绑定" 按钮 + 显示生成的 webhookUrl 复制按钮
    - 依赖 B2-A
    - _Requirements: R5_

  - [~] B5-A.6 实现成员管理页面（UI-004 内的成员标签页）
    - `MemberManagePage`：成员列表 + 添加成员对话框（用户搜索 + 角色选择）+ 移除按钮
    - 依赖 B1-C
    - _Requirements: R6_

  - [~] B5-A.7 实现质量门禁配置页面（UI-009）
    - `QualityGatePage`：动态规则表格（metric / operator / threshold / severity / enabled），支持新增 / 删除 / 排序；保存调用 `gate.saveQualityGate`；"使用模板"按钮加载默认 3 条规则
    - 失败规则的 details 数组在 UI 内联高亮
    - 依赖 B2-B
    - _Requirements: R13_

  - [~] B5-A.8 实现评审任务列表页（UI-006）
    - `ReviewTaskListPage`：筛选项目 / 状态 / 触发类型 / 时间范围；分页表格；"创建任务"按钮（CreateTaskDialog）
    - `CreateTaskDialog`：源分支 / 目标分支 / commitSha / prId（至少一项）→ 调 `reviewTask.create`，请求头携带 `Idempotency-Key`（前端 UUID 生成）
    - 依赖 B3-A
    - _Requirements: R8.1, R8.2, R8.4_

  - [~] B5-A.9 实现评审报告页（UI-007）
    - `ReviewReportPage`：4 个 Tab（概览 / 问题列表 / 代码差异 / 执行日志）
    - 概览：调 `report.report`，展示 status / score / 门禁结果 / aiAvailable；可视化 issueCounts 饼图
    - 问题列表：调 `issue.page`，支持 severity / status / source / filePath 筛选；点击进入 `IssueDetailDrawer`
    - 代码差异：调 `report.diff`，渲染 `DiffViewer.vue`（行级问题标注）
    - 执行日志：调 `report.logs`，stage / level 过滤
    - "重试"按钮（角色 PROJECT_ADMIN / REVIEWER）/ "取消"按钮（PROJECT_ADMIN，PENDING 时） / "申请豁免"按钮（FAILED_GATE 时）
    - 依赖 B3 全部、B4-A、B4-B、B4-E
    - _Requirements: R9.4, R9.6, R15, R16_

  - [~] B5-A.10 实现问题详情抽屉（UI-008）
    - `IssueDetailDrawer.vue`：CodeIssue 详情 + 状态切换下拉 + comment 编辑（FALSE_POSITIVE / CLOSED 校验长度 ≥ 5）+ 评论时间线 + 历史记录
    - 状态切换调 `issue.changeStatus`；DEVELOPER 操作非自身任务时显示后端返回的 `VALIDATION_ERROR`
    - 依赖 B4-A
    - _Requirements: R17_

  - [~] B5-A.11 实现通知中心页面与红点
    - `NotificationListPage`：分页列表 + read/type 筛选 + 一键已读
    - 头部红点：30s 轮询 unread-count；点击跳通知页
    - 依赖 B4-D
    - _Requirements: R19_

  - [~] B5-A.12 实现系统管理 4 个页面（UI-010）
    - `UserManagePage`（B1-A）、`ModelConfigPage`（B1-D）、`ScannerConfigPage`（B1-D）、`AuditLogPage`（B1-B + B1-D）
    - 全部仅 SYSTEM_ADMIN 可见，路由守卫拦截
    - apiKey / webhookSecret 字段始终展示为 `****`
    - _Requirements: R3, R21, R22, R23.3_

  - [~] B5-A.13 实现门禁豁免审批页面（嵌入 ReviewReportPage 或独立 `WaiverApprovalPage`）
    - 申请表单：reason（≥ 10 字符前端校验）+ expireAt（DatePicker，要求 future）
    - 审批入口：通知点击进入审批页面，approve / reject + 评论
    - 依赖 B4-E
    - _Requirements: R15_

  - [~] B5-A.14 维护 `src/router/index.ts` 路由元信息
    - 完成 design.md §5.2 全部 15 条路由的 `meta.requiredRoles` 配置
    - 路由懒加载 + 错误页（403 forbidden、404 not-found）
    - _Requirements: R2_

  - [ ]* B5-A.15 编写关键页面 Vitest 单元 / 交互测试
    - `tests/unit/LoginPage.spec.ts`：表单验证 + 错误码提示
    - `tests/unit/ReviewReportPage.spec.ts`：mock api，4 个 Tab 切换；按钮按角色显示
    - `tests/unit/QualityGatePage.spec.ts`：动态表格新增 / 删除 / 保存
    - `tests/unit/IssueDetailDrawer.spec.ts`：FALSE_POSITIVE 时 comment < 5 禁用提交
    - `tests/unit/NotificationListPage.spec.ts`：未读筛选；markRead
    - _Requirements: R1, R13, R15, R16, R17, R19_

  - [~] B5-A.16 撰写前端 CHANGELOG 与文档
    - `acrqg-web/CHANGELOG.md` 增量记录每个 IT 节点交付内容
    - `docs/frontend.md`：状态管理切片说明、组件契约
    - _Requirements: —_


---

### B6 — 集成验证：性能 / 安全 / 端到端（Integration Node IT-6）

- [ ] B6-A 集成验证与发布前测试
  - _Branch: chore/integration-verification_
  - _Depends: B0-A, B0-B, B1-A, B1-B, B1-C, B1-D, B2-A, B2-B, B3-A, B3-B, B3-C, B3-D, B3-E, B3-F, B4-A, B4-B, B4-C, B4-D, B4-E, B5-A_
  - _Integration Node: IT-6_

  - [~] B6-A.1 编写 k6 性能基准脚本：报告查询
    - 文件：`perf/report-query.js`
    - 场景：100 并发持续 5 分钟 GET `/api/v1/review-tasks/{id}/report`，预先种子化 100 个任务，每任务 200 个 issue
    - `thresholds`: `http_req_duration: ['p(95)<2000']`、`http_req_failed: ['rate<0.01']`
    - 输出 HTML 报告到 `perf/output/report-query-{timestamp}.html`
    - _Requirements: R16.6, R24.2_

  - [~] B6-A.2 编写 k6 性能基准脚本：看板查询与任务执行时延
    - `perf/dashboard-trend.js`：50 并发查 30 天看板 P95 ≤ 2s（R18.5）
    - `perf/task-pipeline.js`：模拟 10 并发提交中小型 PR（mock provider + mock AI）→ 测量 PENDING → 终态全链路时长 P95 ≤ 180s（R24.1）
    - _Requirements: R18.5, R24.1, R24.2_

  - [~] B6-A.3 编写自动化越权用例集（design §15.6 表格）
    - 文件：`src/test/java/com/acrqg/platform/security/AuthorizationMatrixIT.java`
    - `@SpringBootTest` + Testcontainers + 8 行 design 表格全部用例
    - 断言：每行的 expected HTTP 状态与 ApiResponse.code 完全匹配
    - _Requirements: R23.1, R25.5_

  - [~] B6-A.4 编写敏感字段泄露探测测试
    - 文件：`src/test/java/com/acrqg/platform/security/SensitiveLeakIT.java`
    - 遍历所有控制器响应（通过 mvc + AOP 拦截器）；断言响应 JSON 中不出现 `password / accessToken / apiKey / webhookSecret / accessTokenEncrypted / apiKeyEncrypted` 字段名（或值不为 `****` 之外的形式）
    - _Requirements: R23.2, R23.3, R23.5_

  - [~] B6-A.5 编写端到端 Smoke 测试（覆盖 SD-1 ~ SD-6）
    - 文件：`src/test/java/com/acrqg/platform/e2e/EndToEndSmokeIT.java`
    - 流程：登录 → 创建项目 → 绑定仓库（mock provider）→ 配置门禁 → 模拟 webhook → 等待任务终态 → 报告可查询 → 状态回写到 mock provider 的 statuses endpoint → 通知列表出现 → 提交豁免 → 审批通过 → 回写 success（描述含"已豁免"）
    - 通过 WireMock 提供 GitHub / OpenAI 兼容 endpoints；通过 Testcontainers 提供 postgres / redis
    - _Requirements: R7, R9, R10, R11, R12, R14, R15, R19, R20_

  - [~] B6-A.6 编写门禁规则引擎独立用例集（R25.3）
    - 文件：`src/test/java/com/acrqg/platform/gate/GateRuleEngineMatrixIT.java`
    - 6 metric × 5 operator × {BLOCKER, WARN} = 60 用例（覆盖率门槛要求）
    - 每条用例独立断言 actualValue / passed / score 值
    - _Requirements: R25.3_

  - [~] B6-A.7 编写 AI 降级场景测试（R25.4）
    - 文件：`src/test/java/com/acrqg/platform/ai/AiDegradationIT.java`
    - 三类用例：超时（WireMock fixed delay > timeout）、5xx、200 + Schema 校验失败
    - 期望：每类下任务推进到 GATE_EVALUATING（不抛 EXECUTION_FAILED），aiAvailable=false 落 task_log + summary
    - _Requirements: R12.4, R12.5, R25.4_

  - [~] B6-A.8 配置 JaCoCo 覆盖率门槛
    - 修改 `pom.xml` `jacoco-maven-plugin`：`<rule>` 设置 `INSTRUCTION` 与 `LINE` 覆盖率最低 0.70；`mvn verify` 时执行 `check`
    - 排除：`infra/log/MaskingLogbackEncoder.java`（低风险纯转发逻辑）、生成代码、配置类
    - 在 CI 中失败时输出未覆盖的 class 列表到 `target/site/jacoco/uncovered.txt`
    - _Requirements: R25.1_

  - [~] B6-A.9 维护 OpenAPI 契约基线
    - 启动 backend → 抓取 `/v3/api-docs` → 与 `docs/openapi-baseline.json` 对比
    - 文件：`src/test/java/com/acrqg/platform/contract/OpenApiContractIT.java`
    - 不一致时输出 diff，开发需手动审阅并更新 baseline + CHANGELOG
    - _Requirements: R25.2_

  - [~] B6-A.10 启动 / 中断 / 恢复测试
    - 文件：`src/test/java/com/acrqg/platform/recovery/WorkerRecoveryIT.java`
    - 流程：启动 worker → 提交 5 个任务 → 在 STATIC_SCANNING 阶段 kill -9 worker → 启动 worker → 5 个任务被 TaskRecoveryRunner 置 EXECUTION_FAILED 且 task_log 含 `task interrupted by worker restart`；不得有任务直接跳到 PASSED
    - _Requirements: R24.4_

  - [~] B6-A.11 整理 IT-6 验收报告
    - 输出物：`docs/it6-verification-report.md`
    - 内容：覆盖率统计（≥ 70%）、k6 基准结果（P95 数据）、越权用例矩阵、PBT 8 条属性的 tries 数与通过率、SD-1~SD-6 用例链接
    - _Requirements: R25_

  - [ ]* B6-A.12 在 docker-compose 上做最终冒烟
    - 文件：`scripts/smoke.sh`
    - `docker compose up -d` → 等待 health → curl `/health` → 调用 e2e 关键 endpoint 链路 → `docker compose down`
    - 仅验证容器化部署正常，不依赖外部服务
    - _Requirements: R24.6_

#### Integration Node IT-6

- 端到端场景（design §9 全部）：SD-1 / SD-2 / SD-3 / SD-4 / SD-5 / SD-6 端到端通过；任务全链路 P95 ≤ 180s（R24.1）；报告查询 P95 ≤ 2s（R16.6）；看板查询 P95 ≤ 2s（R18.5）。
- 越权用例（design §15.6）：8 行用例全部通过自动化矩阵测试。
- 安全测试：敏感字段泄露探测全部通过；HTTPS 重定向（prod profile 启动）。
- 覆盖率门槛：JaCoCo 全工程语句覆盖率 ≥ 70%（R25.1）；门禁规则引擎独立用例集 60 条全部通过（R25.3）；AI 降级 3 类场景测试通过（R25.4）。
- PBT：8 条 Property（P1~P8）全部通过，迭代次数满足 design §19 标注（关键属性 200~500 次）。

---

## Notes

- 标记 `*` 的子任务为可选，可在快速 MVP 路径下跳过；但 IT-{N} 的覆盖率与 PBT 门槛要求 B1~B4 内核心模块的 `*` 任务（含 8 条属性测试与关键单元 / 集成测试）必须执行后再合并。
- 每条最叶子任务通过 `_Requirements:` 引用 `requirements.md` 中的需求编号（细化到子条款），便于在 PR 描述中粘贴并可由集成节点自动化校验。
- 共享文件冲突治理：`pom.xml`、`application.yml`、`router/index.ts`、`db/migration/` 序号在批次末尾通过 `chore/wire-batch-{N}` 集成 PR 合并；DDL 文件名严格按 `V{seq}__{module}_{purpose}.sql`（B0=V1~V9, B1=V10~V19, B2=V20~V29, B3=V30~V49, B4=V50~V69）。
- 任务下若涉及 `task_log` 写入，务必通过 `TaskLogger`（B3-A.9）而非直接 SQL，以确保 `taskNo` MDC 注入与结构化字段一致（R24.5）。
- 所有 8 条属性测试的实现位置：`src/test/java/com/acrqg/platform/property/`，使用 jqwik；标签格式 `Feature: ai-code-review-quality-gate-platform, Property {N}: {property text}`（按 design.md §19）。

## Property Test Mapping（PBT 总览）

| Property | Title | 任务编号 | 分支 | 验证需求 |
|---|---|---|---|---|
| P1 | 任务状态机迁移有向图 | B3-A.12 | feat/m03-task-core | R9.1, R9.3 |
| P2 | 问题状态迁移合法集 | B4-A.6 | feat/m08-issue | R17.1, R17.2, R17.3 |
| P3 | 任务幂等三元组 | B3-A.13 | feat/m03-task-core | R7.4, R8.3 |
| P4 | 门禁 BLOCKER ⇔ FAILED | B3-F.9 | feat/m07-gate-engine | R13.3, R14.2, R14.3, R14.4 |
| P5 | SensitiveFilter Token 过滤 | B3-E.9 | feat/m06-ai | R12.2, R23.3, R23.4 |
| P6 | JWT 黑名单 5 分钟内必失效 | B1-A.8 | feat/m01-auth | R3.2 |
| P7 | Diff 行数一致性 | B3-C.9 | feat/m04-diff | R10.2, R10.3 |
| P8 | 审计日志 append-only | B1-B.7 | feat/m01-audit | R22.4 |

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["B0-A.1", "B0-A.2", "B0-B.1"] },
    { "id": 1, "tasks": ["B0-A.3", "B0-A.4", "B0-A.5", "B0-A.6", "B0-A.7", "B0-A.8", "B0-A.9", "B0-B.2", "B0-B.3", "B0-B.4"] },
    { "id": 2, "tasks": ["B0-A.10", "B0-A.11", "B0-A.12", "B0-A.13", "B0-A.15", "B0-B.5", "B0-B.6", "B0-B.8"] },
    { "id": 3, "tasks": ["B0-A.14", "B0-B.7"] },
    { "id": 4, "tasks": ["B1-A.1", "B1-B.1", "B1-C.1", "B1-D.1"] },
    { "id": 5, "tasks": ["B1-A.2", "B1-A.5", "B1-B.2", "B1-C.2", "B1-D.2"] },
    { "id": 6, "tasks": ["B1-A.3", "B1-A.4", "B1-B.3", "B1-B.4", "B1-C.3", "B1-D.3", "B1-D.4", "B1-D.5"] },
    { "id": 7, "tasks": ["B1-C.4", "B1-C.5", "B1-C.6", "B1-D.6"] },
    { "id": 8, "tasks": ["B1-A.6", "B1-A.7", "B1-A.8", "B1-B.5", "B1-B.6", "B1-B.7", "B1-C.7", "B1-C.8", "B1-D.7", "B1-D.8"] },
    { "id": 9, "tasks": ["B2-A.1", "B2-B.1"] },
    { "id": 10, "tasks": ["B2-A.2", "B2-A.3", "B2-B.2"] },
    { "id": 11, "tasks": ["B2-A.4", "B2-A.5", "B2-B.3", "B2-B.4"] },
    { "id": 12, "tasks": ["B2-A.6", "B2-A.7", "B2-B.5", "B2-B.6"] },
    { "id": 13, "tasks": ["B3-A.1"] },
    { "id": 14, "tasks": ["B3-A.2", "B3-A.3", "B3-A.9"] },
    { "id": 15, "tasks": ["B3-A.4", "B3-A.5", "B3-A.6", "B3-A.7"] },
    { "id": 16, "tasks": ["B3-A.8", "B3-B.1", "B3-B.2", "B3-C.1", "B3-D.4", "B3-F.1"] },
    { "id": 17, "tasks": ["B3-B.3", "B3-C.2", "B3-C.3", "B3-D.1", "B3-D.5", "B3-E.1", "B3-E.2", "B3-E.3", "B3-E.6", "B3-F.2", "B3-F.3"] },
    { "id": 18, "tasks": ["B3-B.4", "B3-C.4", "B3-D.2", "B3-E.4", "B3-F.4"] },
    { "id": 19, "tasks": ["B3-C.5", "B3-C.6", "B3-D.3", "B3-D.6", "B3-E.5", "B3-F.5", "B3-F.6"] },
    { "id": 20, "tasks": ["B3-A.10", "B3-A.11", "B3-A.12", "B3-A.13", "B3-B.5", "B3-B.6", "B3-C.7", "B3-C.8", "B3-C.9", "B3-D.7", "B3-D.8", "B3-E.7", "B3-E.8", "B3-E.9", "B3-F.7", "B3-F.8", "B3-F.9"] },
    { "id": 21, "tasks": ["B4-A.1", "B4-D.1", "B4-E.1"] },
    { "id": 22, "tasks": ["B4-A.2", "B4-B.1", "B4-C.1", "B4-D.2", "B4-E.2", "B4-E.4"] },
    { "id": 23, "tasks": ["B4-A.3", "B4-B.2", "B4-C.2", "B4-D.3", "B4-D.4", "B4-E.3", "B4-E.5"] },
    { "id": 24, "tasks": ["B4-B.3", "B4-C.3", "B4-D.5", "B4-E.6", "B4-E.7", "B4-E.8"] },
    { "id": 25, "tasks": ["B4-A.4", "B4-A.5", "B4-A.6", "B4-B.4", "B4-B.5", "B4-C.4", "B4-C.5", "B4-D.6", "B4-D.7", "B4-E.9", "B4-E.10", "B4-E.11"] },
    { "id": 26, "tasks": ["B5-A.1", "B5-A.2"] },
    { "id": 27, "tasks": ["B5-A.3", "B5-A.4", "B5-A.5", "B5-A.6", "B5-A.7", "B5-A.8"] },
    { "id": 28, "tasks": ["B5-A.9", "B5-A.10", "B5-A.11", "B5-A.12", "B5-A.13"] },
    { "id": 29, "tasks": ["B5-A.14", "B5-A.16"] },
    { "id": 30, "tasks": ["B5-A.15"] },
    { "id": 31, "tasks": ["B6-A.1", "B6-A.2", "B6-A.3", "B6-A.4", "B6-A.6", "B6-A.7", "B6-A.8", "B6-A.9", "B6-A.10"] },
    { "id": 32, "tasks": ["B6-A.5", "B6-A.11"] },
    { "id": 33, "tasks": ["B6-A.12"] }
  ]
}
```

---

> 计划完成。请按以下步骤推进：
>
> 1. 通过 `gh repo create` 创建 GitHub 仓库并把当前工作区推送到 `main`，再切出 `develop`。
> 2. 启动批次 0：派出 2 个 subagent 分别认领 `chore/infra-bootstrap` 与 `chore/web-bootstrap`。
> 3. 通过 IT-1 后合并到 `develop`，再依据"批次总览"派单批次 1。
> 4. 每个批次结束在对应的 Integration Node 完成端到端 + 越权 + 覆盖率验收，再开下一批。
> 5. 任务执行可点击 tasks.md 中的 `Start task` 按钮逐项启动。
