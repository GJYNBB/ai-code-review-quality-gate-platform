# AI-Assisted Code Review and Quality Gate Platform

> Software name: **ai-code-review-quality-gate-platform**
>
> A one-stop AI-assisted code review and quality gate platform. After developers submit code or create a PR/MR, the platform automatically retrieves code diffs, runs static analysis, performs AI-assisted review, aggregates quality metrics, evaluates quality gate rules, generates structured review reports, and writes gate results back to the code hosting platform.

[中文 README](./README.md)

## Documentation

| Document | Purpose |
|---|---|
| [docs/architecture.md](./docs/architecture.md) | System architecture, module decomposition, branch and integration strategy, and repository structure |
| [docs/frontend.md](./docs/frontend.md) | Frontend state management, component contracts, error code mapping, and route-permission matrix |
| [docs/openapi-baseline.json](./docs/openapi-baseline.json) | OpenAPI contract baseline verified by `OpenApiContractIT` |
| [docs/it6-verification-report.md](./docs/it6-verification-report.md) | IT-6 integration verification manual covering coverage, k6, authorization, PBT, SD, and security checks |
| [acrqg-platform/](./acrqg-platform/) | Backend Maven project |
| [acrqg-web/](./acrqg-web/) | Frontend Vue project |
| [CHANGELOG.md](./CHANGELOG.md) | Batch-based changelog |

## Technology Stack

- **Backend**: Spring Boot 3.x (Java 17), MyBatis-Plus, Spring Security, Redis Stream task queue
- **Frontend**: Vue 3, Vite, TypeScript, Element Plus, Pinia, Vue Router
- **Database**: PostgreSQL 15
- **Cache / Queue / Token Blacklist / Idempotency**: Redis 7
- **Static Scanners**: Checkstyle, ESLint, Pylint, Semgrep through the `StaticScannerAdapter` layer
- **AI Client**: OpenAI Chat Completions-compatible client, JSON Schema validation, and `SensitiveFilter` with path allowlists, token regex detection, and hash comparison before/after filtering
- **Testing**: JUnit 5, Mockito, Testcontainers, jqwik property-based testing, Vitest, k6
- **Containerization**: Docker and docker-compose

## Repository Structure

```text
ai-code-review-quality-gate-platform/
├── acrqg-platform/         # Backend Maven single-module project (Spring Boot 3 + Java 17)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/{main,test}/{java,resources}/
├── acrqg-web/              # Frontend Vue single-package project (Vue 3 + Vite + TypeScript)
│   ├── package.json
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/{api,components,layouts,pages,router,stores,styles,types}/
├── docker-compose.yml      # PostgreSQL + Redis + backend + worker + frontend
├── .env.example            # Environment variable template; copy it to .env
├── docs/                   # Architecture, frontend docs, contract baseline, and IT-6 acceptance report
├── perf/                   # k6 performance benchmark scripts for report-query, dashboard, and task-pipeline
├── scripts/smoke.sh        # docker-compose smoke test script (B6-A.12)
└── .github/workflows/      # GitHub Actions CI workflows
```

## Module List (M01 ~ M11)

| Module | Main Responsibilities |
|---|---|
| M01 Auth + Audit | Login, JWT blacklist, user management, and append-only audit logs |
| M02 Project + RepositoryBinding | Project/member management and repository binding for GitHub, GitLab, and Gitee with encrypted tokens |
| M03 ReviewTask + Webhook | Review task state machine, Redis Stream consumer, webhook integration with signature verification and idempotency |
| M04 Diff | PR diff retrieval and unified diff parsing |
| M05 Static Scanner | Four scanner adapters and severity normalization |
| M06 AI Review | OpenAI-compatible client, `SensitiveFilter`, JSON Schema validation, and three fallback modes |
| M07 Quality Gate + Waiver | Quality gate rule CRUD, six collectors, rule engine, and waiver approval flow |
| M08 Issue + Report + Dashboard | Issue state transitions, report aggregation, and project trend dashboard |
| M09 Notification + Writeback | In-app notifications and commit status writeback |
| M10 System Admin | Model, scanner, and system parameter management with sensitive parameter encryption |
| M11 Quality Metric Reports | Real integration of TestCoverage from JaCoCo CSV and DuplicateRate from PMD-CPD XML |

## Branch Model

> All feature branches have been merged into `main`, and the released version is `v1.0.0`.
>
> For further development, the following naming conventions are recommended:

- `main`: always buildable and deployable.
- `develop`: receives each batch after its corresponding Integration Node (`IT-{N}`) passes.
- Feature branches: `feat/<module-id>-<short-name>`.
- DDL naming: `V{seq}__{module}_{purpose}.sql`; sequence ranges are B0=V1~V9, B1=V10~V19, B2=V20~V29, B3=V30~V49, B4=V50~V69, and M11=V13.

## Integration Verification Nodes (IT-1 ~ IT-6)

| Node | Trigger Batch | Required Checks |
|---|---|---|
| IT-1 | B0 completed | docker-compose starts successfully; `/health=UP`; JaCoCo ≥ 70% |
| IT-2 | B1 completed | End-to-end login/auth, user, project, audit, and system management flows; six unauthorized-access cases |
| IT-3 | B2 completed | Repository binding with connectivity and encryption; quality gate rule configuration for 6×5×2 combinations |
| IT-4 | B3 completed | SD-1 ~ SD-4 end-to-end; three AI fallback categories; PBT P1/P3/P4/P5/P7 pass |
| IT-5 | B4 completed | Reports, dashboard, notifications, writeback, and waivers; SD-5; PBT P2 pass |
| IT-6 | B6 completed | k6 report P95 ≤ 2s; authorization matrix; all eight PBT properties pass; JaCoCo ≥ 70% |

See [docs/it6-verification-report.md](./docs/it6-verification-report.md) for details.

## Quick Start

### One-Command Orchestration (Recommended)

```bash
cp .env.example .env          # Edit JWT_SECRET, TOKEN_ENCRYPTION_KEY, and other required values
docker compose up -d
bash scripts/smoke.sh         # Smoke test: health -> /v3/api-docs -> protected endpoint returns 401
```

Seed administrator account: `admin / admin`.

The initial password hash is injected through `V1__init.sql`. Change it immediately in production-like environments.

### Local Development

```bash
# Backend
cd acrqg-platform
mvn -B verify                 # Includes Flyway migration and JaCoCo checks

# Frontend
cd acrqg-web
npm ci
npm run dev                   # Vite dev server; proxy /api -> http://localhost:8080
```

### Performance Benchmarks

```bash
# Prepare 100 tasks after startup, through the admin API or seed SQL
TS=$(date +%Y%m%d_%H%M%S)
k6 run -e TIMESTAMP=$TS -e TASK_ID_LIST=101,102,...,200 perf/report-query.js
k6 run -e TIMESTAMP=$TS -e PROJECT_ID_LIST=1,2,3 perf/dashboard-trend.js
k6 run -e TIMESTAMP=$TS -e PROJECT_ID=1 -e WEBHOOK_SECRET=mock-secret perf/task-pipeline.js
```

## Testing

| Category | Command | Notes |
|---|---|---|
| Unit + Integration | `mvn -pl acrqg-platform test` | Includes IT classes; Docker is required for Testcontainers |
| Property-Based Testing (PBT) | `mvn -pl acrqg-platform test -Dtest='*PropertyTest'` | Eight properties: P1 ~ P8 |
| Authorization Matrix | `mvn -pl acrqg-platform test -Dtest=AuthorizationMatrixIT` | Eight test rows from design §15.6 |
| End-to-End Smoke | `mvn -pl acrqg-platform test -Dtest=EndToEndSmokeIT` | SD-1 ~ SD-6 |
| Frontend | `cd acrqg-web && npm run test:unit -- --run` | Vitest; only applicable when corresponding tests are implemented |
| Containerized Smoke Test | `bash scripts/smoke.sh` | Starts docker-compose and verifies key endpoints |

## License

For course project / graduation project prototype use only.
