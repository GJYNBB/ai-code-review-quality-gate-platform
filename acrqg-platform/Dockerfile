# =============================================================================
# AI 辅助代码评审与质量门禁平台 — 后端镜像（多阶段构建）
# Task:        B0-A.11
# Requirement: R24.6 (可观测 / 可部署)
# Design:      design.md §17.1 / §17.3
#
# 镜像策略：
#   - builder：maven:3.9-eclipse-temurin-17（自带 mvn + JDK 17，避免在 jdk-jammy 上额外 apt 安装 maven）
#   - runtime：eclipse-temurin:17-jre-jammy（仅 JRE，体积更小）
#   - 同一 jar 通过 SPRING_PROFILES_ACTIVE 在 docker-compose 中切换 api / worker
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: builder —— 编译并打包 Spring Boot 可执行 jar
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

# 1) 仅复制 pom.xml，预热依赖缓存（利用 Docker 层缓存：源码变更但依赖未变时跳过下载）
COPY pom.xml ./
RUN mvn -B -e -ntp dependency:go-offline

# 2) 复制源码并构建（跳过测试：测试由 CI 在 mvn verify 时单独执行）
COPY src ./src
RUN mvn -B -e -ntp -DskipTests package \
    && cp target/acrqg-platform.jar /workspace/app.jar

# -----------------------------------------------------------------------------
# Stage 2: runtime —— 仅包含 JRE 与可执行 jar
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

# OCI 镜像元信息（便于注册表 / 漏洞扫描追溯）
LABEL org.opencontainers.image.title="acrqg-platform" \
      org.opencontainers.image.description="AI-assisted code review and quality gate platform (Spring Boot)" \
      org.opencontainers.image.source="https://github.com/GJYNBB/ai-code-review-quality-gate-platform" \
      org.opencontainers.image.version="0.1.0-SNAPSHOT" \
      org.opencontainers.image.licenses="Course-Project" \
      org.opencontainers.image.vendor="acrqg"

# 时区与默认 Spring profile（worker 服务在 docker-compose 中覆盖为 prod,worker）
ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prod,api \
    JAVA_OPTS=""

# 安装 healthcheck 所需的极小工具（wget），并创建非 root 运行账户
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends wget tini; \
    apt-get clean; \
    rm -rf /var/lib/apt/lists/*; \
    groupadd --system --gid 1000 appgroup; \
    useradd  --system --uid 1000 --gid appgroup --home-dir /app --shell /sbin/nologin appuser; \
    mkdir -p /app; \
    chown -R appuser:appgroup /app

WORKDIR /app

# 从 builder 拷贝 fat-jar；归属非 root 运行用户
COPY --from=builder --chown=appuser:appgroup /workspace/app.jar /app/app.jar

USER appuser

EXPOSE 8080

# 容器级健康检查：API / Worker profile 均保留 Spring Actuator /actuator/health 端点
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD wget -q -O - http://127.0.0.1:8080/actuator/health || exit 1

# 使用 tini 作为 PID 1，正确处理信号与僵尸进程
ENTRYPOINT ["/usr/bin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
