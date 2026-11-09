#!/usr/bin/env bash
# 复现「图片识别 Vision」功能点的接口验证脚本
# 用法: bash verify-vision.sh [BASE_URL]     默认 http://localhost:8083
# 前置: 同目录下有 vision-test.png / not-image.txt / empty.png（仓库已自带）
set -u
BASE="${1:-http://localhost:8083}"
DIR="$(cd "$(dirname "$0")" && pwd)"
LOG="$DIR/终端验证.log"
exec > >(tee "$LOG") 2>&1

cd "$DIR"
echo "### 验证时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "### 目标地址: $BASE"
echo "### 测试图片: $(ls -l vision-test.png | awk '{print $5" 字节"}')"
echo

echo "== 步骤3: 正常识别(文本 + 图片一起提交) =="
echo "\$ curl -s -F 'image=@vision-test.png' -F 'message=描述这张图片' -F 'model=remote-deepseek' $BASE/api/vision"
curl -s -m 120 --noproxy '*' \
  -F "image=@vision-test.png" -F "message=描述这张图片" -F "model=remote-deepseek" \
  "$BASE/api/vision"
echo; echo

echo "== 步骤4: 空文件, 预期返回 请上传图片 =="
curl -s -m 20 --noproxy '*' -F "image=@empty.png;type=image/png" "$BASE/api/vision"
echo; echo

echo "== 步骤5: 非图片文件, 预期返回 只支持图片文件 =="
curl -s -m 20 --noproxy '*' -F "image=@not-image.txt" "$BASE/api/vision"
echo; echo

echo "== 步骤6: 未知模型, 预期返回 未知模型 =="
curl -s -m 20 --noproxy '*' -F "image=@vision-test.png" -F "model=gpt-4o" "$BASE/api/vision"
echo
