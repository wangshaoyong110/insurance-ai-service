#!/usr/bin/env bash
#
# 安鑫保险智能客服 · 宿主机加固（公网部署必做）
#
# 用法(在服务器上、项目根目录执行):
#   sudo bash deploy/hardening.sh
#
# 做四件事：
#   1. SSH 爆破防护：同一 IP 60 秒内新建连接数超限即 DROP（基于 iptables recent，无需 fail2ban）
#   2. sshd 保守加固：收紧认证尝试次数与空闲超时，但**不改变密码登录方式**，避免把自己锁在门外
#   3. 内核网络参数：SYN cookies / 反向路径校验 / 忽略重定向
#   4. 对外监听端口审计：只应有 22 与 80
#
# 幂等：重复执行不会叠加规则；规则通过 systemd unit 开机自动恢复。
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

log()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m  ✓ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m  ! %s\033[0m\n' "$*"; }
die()  { printf '\n\033[1;31m==> 失败: %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "请用 root 执行:sudo bash deploy/hardening.sh"

# ---------- 1. SSH 爆破防护 ----------
log "SSH 爆破防护"
if command -v iptables >/dev/null 2>&1; then
    SSH_PORT=$(sshd -T 2>/dev/null | awk '/^port / {print $2; exit}')
    SSH_PORT=${SSH_PORT:-22}

    cat > /usr/local/sbin/ssh-guard.sh <<EOF
#!/bin/sh
# 由 deploy/hardening.sh 生成：限制单 IP 对 SSH 的建连频率
# 阈值 12 次 / 60 秒——正常运维远达不到，爆破脚本几秒就会触发
set -e
IPT=\$(command -v iptables) || exit 0
\$IPT -N SSH_GUARD 2>/dev/null || true
\$IPT -F SSH_GUARD
\$IPT -A SSH_GUARD -p tcp --dport ${SSH_PORT} -m conntrack --ctstate NEW \\
    -m recent --set --name SSH_BRUTE --rsource
\$IPT -A SSH_GUARD -p tcp --dport ${SSH_PORT} -m conntrack --ctstate NEW \\
    -m recent --update --seconds 60 --hitcount 12 --name SSH_BRUTE --rsource -j DROP
\$IPT -C INPUT -j SSH_GUARD 2>/dev/null || \$IPT -I INPUT 1 -j SSH_GUARD
EOF
    chmod 700 /usr/local/sbin/ssh-guard.sh

    cat > /etc/systemd/system/ssh-guard.service <<'EOF'
[Unit]
Description=SSH brute-force rate limit (iptables recent)
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/usr/local/sbin/ssh-guard.sh

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable ssh-guard.service >/dev/null 2>&1 || true
    systemctl restart ssh-guard.service
    ok "已限制单 IP 每 60 秒最多 12 次 SSH 建连（端口 ${SSH_PORT}）"
else
    warn "未找到 iptables，跳过 SSH 限速"
fi

# ---------- 2. sshd 保守加固 ----------
log "sshd 加固"
if [ -d /etc/ssh/sshd_config.d ]; then
    cat > /etc/ssh/sshd_config.d/99-hardening.conf <<'EOF'
# 由 deploy/hardening.sh 生成
# 注意：这里刻意不改 PasswordAuthentication / PermitRootLogin，
# 以免与既有的登录方式冲突、把运维人员锁在门外。
MaxAuthTries 4
LoginGraceTime 30
PermitEmptyPasswords no
X11Forwarding no
AllowAgentForwarding no
ClientAliveInterval 300
ClientAliveCountMax 2
EOF
    if sshd -t 2>/dev/null; then
        systemctl reload sshd 2>/dev/null || systemctl reload ssh 2>/dev/null || true
        ok "已收紧认证尝试次数(MaxAuthTries=4)与登录宽限期(30s)，登录方式未变"
    else
        rm -f /etc/ssh/sshd_config.d/99-hardening.conf
        warn "sshd 配置校验失败，已回滚该文件"
    fi
else
    warn "该发行版无 sshd_config.d，跳过 sshd 加固"
fi

# ---------- 3. 内核网络参数 ----------
log "内核网络参数"
cat > /etc/sysctl.d/99-insure-hardening.conf <<'EOF'
# SYN 洪泛防护
net.ipv4.tcp_syncookies = 1
# 反向路径校验，缓解 IP 伪造
net.ipv4.conf.all.rp_filter = 1
net.ipv4.conf.default.rp_filter = 1
# 忽略 ICMP 重定向与源路由（防路由劫持）
net.ipv4.conf.all.accept_redirects = 0
net.ipv4.conf.default.accept_redirects = 0
net.ipv4.conf.all.send_redirects = 0
net.ipv4.conf.all.accept_source_route = 0
# 不响应广播 ping
net.ipv4.icmp_echo_ignore_broadcasts = 1
# 记录异常源地址包，便于排查扫描
net.ipv4.conf.all.log_martians = 0
EOF
sysctl -p /etc/sysctl.d/99-insure-hardening.conf >/dev/null
ok "已应用(SYN cookies / rp_filter / 忽略重定向)"

# ---------- 4. 对外监听审计 ----------
log "对外监听端口审计"
LISTEN=$(ss -lntH 2>/dev/null | awk '{print $4}' | grep -vE '^(127\.|::1|\[::1\])' | sort -u || true)
printf '%s\n' "$LISTEN" | sed 's/^/    /'
ALLOWED=$(printf '%s\n' "$LISTEN" | grep -cE ':(22|80|443)$' || true)
TOTAL=$(printf '%s\n' "$LISTEN" | grep -c ':' || true)
if [ "$TOTAL" -gt "$ALLOWED" ]; then
    warn "存在非 22/80/443 的对外监听，请确认是否必要（MySQL/Redis/后端都不应出现）"
else
    ok "对外仅监听 22/80，数据库与后端均在 Docker 内网"
fi

# ---------- 5. 时间同步 ----------
if systemctl list-unit-files 2>/dev/null | grep -q '^chronyd.service'; then
    systemctl enable --now chronyd >/dev/null 2>&1 || true
    ok "chronyd 已启用（TLS 与 JWT 时效依赖系统时钟）"
fi

log "加固完成"
cat <<'EOF'

  建议继续做（需要你在云控制台/自己权衡）：
    1. 安全组只放通 22 / 80（443 待配证书后再放）
    2. 把 root 密码换成强口令，或用密钥登录后关闭密码认证：
         ssh-keygen -t ed25519
         ssh-copy-id root@<IP>
         # 验证密钥能登录后，再改 /etc/ssh/sshd_config.d/99-hardening.conf 加：
         #   PasswordAuthentication no
    3. 有条件时配域名 + HTTPS，避免登录口令在公网明文传输
EOF
