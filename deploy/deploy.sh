#!/usr/bin/env bash
#
# 安鑫保险智能客服 · 服务器一键部署
#
# 用法(在服务器上、项目根目录执行):
#   sudo bash deploy/deploy.sh
#   sudo bash deploy/deploy.sh --mirror https://docker.m.daocloud.io   # 国内拉镜像慢时指定加速
#   sudo bash deploy/deploy.sh --no-harden                            # 跳过宿主机加固
#
# 做的事:装 Docker → 生成 .env(随机密钥) → 构建镜像 → 起容器 → 健康检查 → 宿主机加固
# 幂等:重复执行只重建镜像,不删数据(MySQL/Redis 在 named volume 里)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MIRROR=""
HARDEN=1
while [ $# -gt 0 ]; do
    case "$1" in
        --mirror) MIRROR="${2:-}"; shift 2 ;;
        --no-harden) HARDEN=0; shift ;;
        -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
        *) echo "未知参数: $1" >&2; exit 1 ;;
    esac
done

log()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m  ✓ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m  ! %s\033[0m\n' "$*"; }
die()  { printf '\n\033[1;31m==> 失败: %s\033[0m\n' "$*" >&2; exit 1; }

# ---------- 0. 前置检查 ----------
[ "$(id -u)" -eq 0 ] || die "请用 root 执行:sudo bash deploy/deploy.sh"
[ -f docker-compose.yml ] || die "请在项目根目录执行(找不到 docker-compose.yml)"

if [ -r /proc/meminfo ]; then
    MEM_MB=$(awk '/MemTotal/ {printf "%d", $2/1024}' /proc/meminfo)
else
    MEM_MB=0
fi
DISK_GB=$(df -Pk . | awk 'NR==2 {printf "%d", $4/1024/1024}')
log "环境: 内存 ${MEM_MB}MB / 可用磁盘 ${DISK_GB}GB"
[ "$DISK_GB" -ge 8 ] || warn "可用磁盘不足 8GB,镜像与数据可能写满"

if [ "$MEM_MB" -gt 0 ] && [ "$MEM_MB" -lt 3500 ]; then
    warn "内存不足 4GB。后端 Maven 构建在容器内可能被 OOM 杀掉"
    warn "建议改用「本地构建 + 上传产物」方式,见 README「十、Docker 与 Nginx 部署」"
fi

# ---------- 1. 安装 Docker ----------
if command -v docker >/dev/null 2>&1; then
    ok "Docker 已安装: $(docker --version | awk '{print $3}' | tr -d ',')"
else
    log "安装 Docker Engine + Compose 插件"
    if command -v apt-get >/dev/null 2>&1; then
        export DEBIAN_FRONTEND=noninteractive
        apt-get update -y -qq
        apt-get install -y -qq ca-certificates curl gnupg
        install -m 0755 -d /etc/apt/keyrings
        DISTRO=$(. /etc/os-release && echo "$ID")
        CODENAME=$(. /etc/os-release && echo "$VERSION_CODENAME")
        curl -fsSL "https://download.docker.com/linux/${DISTRO}/gpg" -o /etc/apt/keyrings/docker.asc
        chmod a+r /etc/apt/keyrings/docker.asc
        echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/${DISTRO} ${CODENAME} stable" \
            > /etc/apt/sources.list.d/docker.list
        apt-get update -y -qq
        apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    elif command -v dnf >/dev/null 2>&1; then
        dnf -y -q install dnf-plugins-core
        dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
        dnf -y -q install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    elif command -v yum >/dev/null 2>&1; then
        yum -y -q install yum-utils
        yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
        yum -y -q install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    else
        die "未识别的包管理器,请手动安装 Docker"
    fi
    systemctl enable --now docker
    ok "Docker 安装完成"
fi

docker info >/dev/null 2>&1 || { systemctl start docker || die "Docker 启动失败"; }
docker compose version >/dev/null 2>&1 || die "缺少 docker compose 插件(v2),请升级 Docker"

# ---------- 2. 镜像加速(可选) ----------
if [ -n "$MIRROR" ]; then
    log "配置镜像加速: $MIRROR"
    mkdir -p /etc/docker
    if [ -f /etc/docker/daemon.json ]; then
        cp /etc/docker/daemon.json "/etc/docker/daemon.json.bak.$(date +%s)"
        warn "已备份原 daemon.json"
    fi
    cat > /etc/docker/daemon.json <<EOF
{
  "registry-mirrors": ["$MIRROR"],
  "log-driver": "json-file",
  "log-opts": { "max-size": "50m", "max-file": "3" }
}
EOF
    systemctl daemon-reload
    systemctl restart docker
    ok "加速已生效"
fi

# ---------- 3. 生成 .env ----------
if [ -f .env ]; then
    ok ".env 已存在,保持不变(如需重置请先备份并删除)"
else
    log "生成 .env 与会话密钥"
    # 去掉 +/=/ 等 .env 容易出问题的字符,只保留字母数字
    JWT_SECRET=$(head -c 64 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 48)
    CRYPTO_SECRET=$(head -c 64 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 40)
    DB_PASS=$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
    REDIS_PASS=$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
    cat > .env <<EOF
# 由 deploy/deploy.sh 自动生成于 $(date '+%Y-%m-%d %H:%M:%S')
# 警告:INSURE_CRYPTO_SECRET 一旦用于加密模型 Key 就不能再改,否则已存的 Key 无法解密

WEB_PORT=80

MYSQL_DATABASE=insurance_ai
MYSQL_ROOT_PASSWORD=${DB_PASS}
# Redis 只在 Docker 内网,仍设密码:防止同宿主机其他容器横向访问
REDIS_PASSWORD=${REDIS_PASS}

INSURE_JWT_SECRET=${JWT_SECRET}
INSURE_CRYPTO_SECRET=${CRYPTO_SECRET}

INSURE_SMS_MOCK=true
INSURE_SMS_VERIFY_REGISTER=true
INSURE_SMS_IP_HOURLY_LIMIT=20
INSURE_SMS_IP_DAILY_LIMIT=50
INSURE_SMS_GLOBAL_DAILY_LIMIT=1000

# 演示环境保留验证码开关,但关闭明文回显(否则等于没有验证码)
INSURE_CAPTCHA_ENABLED=true
INSURE_CAPTCHA_DEBUG=false

INSURE_UPLOAD_MAX_SIZE=50MB
INSURE_UPLOAD_AVATAR_LIMIT=2MB
INSURE_UPLOAD_KB_LIMIT=20MB
INSURE_UPLOAD_CHAT_IMAGE_LIMIT=10MB
INSURE_UPLOAD_CHAT_VIDEO_LIMIT=50MB
INSURE_UPLOAD_COMMON_LIMIT=20MB

# 模型 Key 留空:首次启动不创建模型配置,登录后台「系统管理-模型配置」录入
INSURE_DEEPSEEK_API_KEY=

# 公网部署关闭接口文档(Nginx 层另有一道 404 拦截)
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
EOF
    chmod 600 .env
    ok ".env 已生成(权限 600)"
fi

mkdir -p data/logs data/upload
# 后端镜像以非 root（uid 1001）运行，挂载目录属主必须是它，否则 logback 写日志直接
# Permission denied 然后启动失败——容器会一直 Restarting，日志里只有一堆堆栈
chown -R 1001:1001 data/logs data/upload
ok "日志与上传目录就绪: ./data/（属主 1001:1001）"

# ---------- 4. 构建并启动 ----------
log "构建镜像并启动(首次约 5-15 分钟,取决于机器与网络)"
docker compose up -d --build || die "compose 启动失败,执行 docker compose logs 查看原因"

# ---------- 5. 健康检查 ----------
log "等待服务就绪"
WEB_PORT=$(grep -E '^WEB_PORT=' .env | cut -d= -f2)
WEB_PORT=${WEB_PORT:-80}
READY=0
for i in $(seq 1 40); do
    if curl -fsS --max-time 2 "http://127.0.0.1:${WEB_PORT}/healthz" >/dev/null 2>&1; then
        READY=1
        break
    fi
    sleep 3
done

if [ "$READY" -eq 1 ]; then
    ok "前端已就绪"
else
    warn "前端健康检查未通过,最近日志:"
    docker compose logs --tail 40 web || true
fi

# 后端可能仍在初始化数据源,单独探一次
for i in $(seq 1 20); do
    if docker compose exec -T backend wget -qO- http://127.0.0.1:8080/api/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
        ok "后端已就绪(数据库初始化完成)"
        break
    fi
    sleep 5
done

# ---------- 6. 宿主机加固 ----------
if [ "$HARDEN" -eq 1 ] && [ -f deploy/hardening.sh ]; then
    bash deploy/hardening.sh || warn "加固脚本执行异常,不影响业务容器,可稍后单独重跑"
fi

HOST_IP=$(curl -fsS --max-time 3 https://ipinfo.io/ip 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}')
log "部署完成"
cat <<EOF

  管理端     http://${HOST_IP}/
  手机端     http://${HOST_IP}/mobile.html

  账号       管理端 admin / 123456     手机端 13900000001 / 123456
             ⚠ 公网环境请登录后立刻修改默认密码（右上角头像 → 修改密码）

  已关闭     接口文档 / Swagger / actuator 均不对公网开放（Nginx 404）

  下一步     登录后台「系统管理-模型配置」填入 API Key 并点「测试连通性」,
            否则发送消息会提示 AI 调用失败。

  常用命令   docker compose ps
             docker compose logs -f backend
             docker compose restart backend
             docker stats --no-stream
EOF
