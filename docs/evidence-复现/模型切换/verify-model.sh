#!/usr/bin/env bash
# 复现「模型切换」功能点的接口验证脚本
# 用法: bash verify-model.sh [BASE_URL]     默认 http://localhost:8083
# 输出同时打印并写入同目录的 终端验证.log
set -u
BASE="${1:-http://localhost:8083}"
LOG="$(dirname "$0")/终端验证.log"
exec > >(tee "$LOG") 2>&1

echo "### 验证时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "### 目标地址: $BASE"
echo

echo "== 步骤1: GET /api/models 拿到模型清单 =="
echo "\$ curl -s $BASE/api/models"
curl -s -m 10 --noproxy '*' "$BASE/api/models"
echo; echo

echo "== 步骤2: 用默认模型(remote-deepseek)对话 =="
echo "\$ curl -s -H 'Content-Type: application/json' -d '{\"message\":\"用一句话说明你是谁\",\"model\":\"remote-deepseek\"}' $BASE/api/chat"
curl -s -m 90 --noproxy '*' -H 'Content-Type: application/json' \
  -d '{"message":"用一句话说明你是谁","model":"remote-deepseek"}' "$BASE/api/chat"
echo; echo

echo "== 步骤3: 切到本地 Ollama 模型(本机未启动 Ollama, 预期报错) =="
echo "\$ curl -s ... -d '{\"message\":\"hi\",\"model\":\"ollama-deepseek-r1\"}' $BASE/api/chat"
curl -s -m 60 --noproxy '*' -H 'Content-Type: application/json' \
  -d '{"message":"hi","model":"ollama-deepseek-r1"}' "$BASE/api/chat"
echo; echo

echo "== 步骤4: 不存在的模型 id, 预期返回 未知模型 =="
curl -s -m 10 --noproxy '*' -H 'Content-Type: application/json' \
  -d '{"message":"hi","model":"gpt-4o"}' "$BASE/api/chat"
echo; echo

echo "== 步骤5: 空消息, 预期返回 message cannot be empty =="
curl -s -m 10 --noproxy '*' -H 'Content-Type: application/json' \
  -d '{"message":"   ","model":"remote-deepseek"}' "$BASE/api/chat"
echo
