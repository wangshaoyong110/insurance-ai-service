# ---------- 构建阶段 ----------
FROM node:22-alpine AS build
WORKDIR /app

# 国内 npm 官方源 RTT 高，npmmirror 快 5 倍以上；海外构建可覆盖
ARG NPM_REGISTRY=https://registry.npmmirror.com
RUN npm config set registry "$NPM_REGISTRY"

# 先装依赖，利用层缓存
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci || npm install

COPY frontend/ ./
# 生产构建固定用 production 模式（接口走同源 /api，由 Nginx 反代）
# 注意：脚本名是 build:prod，package.json 里没有 build，写成 build 会让镜像构建直接失败
RUN npm run build:prod

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine

# 时区与日志
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone

# 站点配置 + 两个被 include 的片段（限流/安全头/反代公共参数）
RUN mkdir -p /etc/nginx/snippets
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY deploy/nginx-proxy.inc /etc/nginx/snippets/proxy.inc
COPY deploy/nginx-sec-headers.inc /etc/nginx/snippets/sec-headers.inc
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1/healthz || exit 1
