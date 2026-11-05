#!/usr/bin/env bash
# 本地一键校验：提交前跑一遍，等价于 CI 的检查项
# 用法: bash scripts/ci.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "=========================================="
echo "1/4 后端编译"
echo "=========================================="
(cd backend && mvn -B -q compile)

echo "=========================================="
echo "2/4 后端单元测试"
echo "=========================================="
(cd backend && mvn -B test)

echo "=========================================="
echo "3/4 前端类型检查 + 生产构建"
echo "=========================================="
(cd frontend && npx vue-tsc --noEmit && npx vite build)

echo "=========================================="
echo "4/4 密钥泄漏检查"
echo "=========================================="
if grep -rnE "(sk-[A-Za-z0-9]{20,}|BEGIN (RSA|OPENSSH) PRIVATE KEY)" \
    --include='*.java' --include='*.yml' --include='*.yaml' \
    --include='*.ts' --include='*.vue' --include='*.properties' \
    --exclude-dir=node_modules --exclude-dir=target --exclude-dir=dist . \
    | grep -vE 'sk-(fake|AbCd)'; then
  echo "❌ 发现疑似真实密钥，请改用环境变量注入" >&2
  exit 1
fi
echo "✅ 未发现硬编码密钥"

echo ""
echo "全部检查通过 ✅"
