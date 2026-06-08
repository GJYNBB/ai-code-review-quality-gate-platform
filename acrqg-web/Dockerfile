# =============================================================================
# AI 辅助代码评审与质量门禁平台 — 前端镜像构建
# Task:        B0-B.6
# Requirement: R23.6
# Design:      design.md §17.1 / §17.3
#
# 多阶段构建：
#   1. builder: node:20-alpine 安装依赖并构建生产产物（dist/）
#   2. runtime: nginx:1.25-alpine 提供静态资源 + /api 反向代理后端
# =============================================================================

# -------- Stage 1: Build --------
FROM node:20-alpine AS builder

WORKDIR /app

# 优先复制依赖描述文件以最大化层缓存命中
COPY package.json package-lock.json* ./
COPY .npmrc ./

# 必须使用 lockfile 构建，保证依赖可重复且与 CI 的 npm ci 行为一致
RUN npm ci --no-audit --no-fund

# 复制源码并构建
COPY tsconfig*.json vite.config.ts env.d.ts index.html ./
COPY src ./src
RUN npm run build

# -------- Stage 2: Runtime --------
FROM nginx:1.25-alpine AS runtime

# 移除默认站点配置，注入项目专属 nginx.conf
RUN rm -f /etc/nginx/conf.d/default.conf

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

# 健康检查（与 docker-compose healthcheck 对齐）
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O - http://127.0.0.1/ >/dev/null || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
