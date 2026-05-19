# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 风格，并采用 [SemVer 2.0.0](https://semver.org/lang/zh-CN/) 进行版本管理。

每条记录尾部以 `(Rxx)` 注明所覆盖的 EARS 需求编号，便于回溯到 [requirements.md](./.kiro/specs/ai-code-review-quality-gate-platform/requirements.md) 与 [design.md](./.kiro/specs/ai-code-review-quality-gate-platform/design.md)。

## [Unreleased]

### Added
- 暂无。下一阶段（B0-B）将引入前端 `acrqg-web/` Vue 3 + Vite 骨架；之后批次 B1~B6 按 [tasks.md](./.kiro/specs/ai-code-review-quality-gate-platform/tasks.md) 推进。

## [0.1.0] - Batch B0-A 后端基础设施 Bootstrap

> 后端基础设施 Bootstrap 批次（git 分支 `chore/infra-bootstrap`，Integration Node IT-1）。完成 14 项交付，为 M01~M10 全部业务模块提供单向依赖的基础层（`common` / `infra`）、运行时编排（Docker / docker-compose）、CI 模板与可观测端点。

### Added
- **B0-A.1** Maven 单模块骨架与 `pom.xml`（Spring Boot 3 / Java 17 / MyBatis-Plus / Flyway / JJWT / springdoc / Micrometer）；JaCoCo 覆盖率插件 (R24.1, R25.1)
- **B0-A.2** `AcrqgApplication` 启动类 + 6 份 Profile 配置（`dev` / `test` / `prod` × `api` / `worker`），Worker profile 不暴露 web 控制器 (R17.3, R24.6)
- **B0-A.3** `common` 包：`ApiResponse` / `PageResult` / `FieldError` / `ErrorCode` / `BusinessException` / `GlobalExceptionHandler` / `MaskUtils` / `JsonUtils` / `IdGenerator` (R23.3, 全局错误码骨架)
- **B0-A.4** JWT + Spring Security：`JwtTokenProvider` / `JwtAuthFilter` / `SecurityConfig` / `CurrentUserHolder`，含白名单与 401 ApiResponse 编排 (R1.4, R23.1)
- **B0-A.5** `AesGcmCipher`（12B IV + 128bit Tag + PBKDF2 派生 256bit 密钥）+ `TokenEncryptor` 薄包装 (R5.3, R5.4, R21.1, R23.2)
- **B0-A.6** Redis 基建：`RedisConfig` / `RedisStreamPublisher` / `IdempotencyStore` / `JwtBlacklist` 及 `jti` SPI bean (R3.2, R7.4, R8.4)
- **B0-A.7** `@RequirePermission` 注解 + AOP 切面 + `Role` / `ProjectRole` 枚举 + `PermissionEvaluator` 骨架（`isProjectMember` / `hasProjectRole` 占位至 B1-C 落地） (R2.1, R2.2, R2.3, R2.4, R2.5)
- **B0-A.8** `TraceIdFilter`（`X-Request-Id` ↔ MDC `traceId`） + `MaskingLogbackEncoder` + `logback-spring.xml`（JSON 输出） (R23.3, R24.5)
- **B0-A.9** `WebMvcConfig` / CORS + `ResponseMaskingAspect`（递归字段掩码） + `OpenApiConfig`（bearer-auth 全局安全方案） + `MyBatisPlusConfig`（分页 / 乐观锁 / `created_at` `updated_at` 自动填充） (R23.3)
- **B0-A.10** Flyway `V1__init.sql`：`"user"` / `role` / `user_role` / `audit_log` 表 + 5 条种子角色 + 初始 `admin` 用户 + `audit_log` append-only 触发器 + `updated_at` 触发器 (R1.6, R2, R22.4, R23.3)
- **B0-A.11** 多阶段 `Dockerfile`（builder=`eclipse-temurin:17-jdk` / runtime=`17-jre`）+ `docker-compose.yml`（postgres:15 / redis:7 / backend / worker / nginx 占位）+ `.env.example` (R24.6)
- **B0-A.12** GitHub Actions CI：`backend-build` / `frontend-build` / `e2e` 三 job 模板，含 PostgreSQL + Redis service container 与 JaCoCo 覆盖率 ≥ 70% 校验 (R25.1, R25.2)
- **B0-A.13** Health & Metrics：`RedisStreamHealthIndicator` / `AiServiceHealthIndicator` 占位 + `acrqg_jwt_blacklist_size` Gauge 注册 (R24.6)
- **B0-A.15** OpenAPI 契约基线 `docs/openapi-baseline.json`（synthetic seed，待 B1+ 控制器接入后由 `/v3/api-docs` 重生成） + 本 `CHANGELOG.md` + `docs/architecture.md` 团队入口文档 (R25.2)

### Notes
- B0-A.14（基础设施单元测试）为可选子任务，已在 tasks.md 中以 `*` 标注，未在本批次交付，将随各业务模块测试集中补齐。
- 仅基础设施层无业务接口，因此 OpenAPI 基线 `paths` 为空；`bearer-auth` 安全方案与元信息已锁定，B1+ 新增控制器在合入 `develop` 后由 IT-x 集成节点重新导出基线。
- 加密密钥 `tokenEncryptionKey` 通过环境变量装载，DB 不存明文（与 R23.2 对齐）。

[Unreleased]: ./CHANGELOG.md
[0.1.0]: ./CHANGELOG.md
