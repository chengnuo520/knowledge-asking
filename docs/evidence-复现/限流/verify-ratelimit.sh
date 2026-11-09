#!/usr/bin/env bash
# 复现「接口限流」用：同一个客户端连打 4 次，观察 200 → 429 的转折
# 用法: bash verify-ratelimit.sh [BASE_URL]    默认 http://localhost:8086（限额 3 次的实例）
# 说明: 用 X-Forwarded-For 模拟不同客户端，避免和浏览器的计数互相干扰
set -u
BASE="${1:-http://localhost:8086}"
DIR="$(cd "$(dirname "$0")" && pwd)"
exec > >(tee "$DIR/终端验证.log") 2>&1

echo "### 验证时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "### 目标地址: $BASE （启动参数: --rate-limit.limit=3 --rate-limit.window-seconds=60）"
echo

echo "== 步骤2: 客户端 10.0.0.9 连续请求 4 次 GET /api/models =="
for i in 1 2 3 4; do
  echo "---------- 第 $i 次 ----------"
  curl -s --noproxy '*' -D - -o /tmp/rl-body.txt -H "X-Forwarded-For: 10.0.0.9" "$BASE/api/models" \
    | grep -Ei "^HTTP/|^X-RateLimit|^Retry-After"
  echo "body: $(head -c 160 /tmp/rl-body.txt)"
  echo
done

echo "== 步骤3: 换一个客户端 10.0.0.8, 计数独立, 应立刻放行 =="
curl -s --noproxy '*' -D - -o /tmp/rl-body2.txt -H "X-Forwarded-For: 10.0.0.8" "$BASE/api/models" \
  | grep -Ei "^HTTP/|^X-RateLimit"
echo "body: $(head -c 160 /tmp/rl-body2.txt)"
echo

echo "== 步骤4: 观察被限流时后端日志（窗口剩余毫秒数） =="
grep -h "限流拦截" "$DIR/01-启动日志.log" | tail -3
echo
