#!/usr/bin/env bash
# scripts/smoke/01_post_to_es.sh
# L3 冒烟测试：发帖 → sidecar → ES 可搜索
# 依赖：docker compose 环境已启动（含 Java 服务）
# 用法：./scripts/smoke/01_post_to_es.sh [gateway_url]

set -euo pipefail

GW="${1:-http://localhost:8888}"
SEARCH_DELAY="${SEARCH_DELAY:-5}"  # 等待 sidecar 同步到 ES 的秒数

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✅ PASS${NC} $1"; }
fail() { echo -e "${RED}❌ FAIL${NC} $1"; FAILED=$((FAILED+1)); }
info() { echo -e "${YELLOW}ℹ️  ${NC} $1"; }

FAILED=0

# ---- 步骤 1：登录获取 token ----
info "登录获取 access token..."
LOGIN_RESP=$(curl -sf -X POST "$GW/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@test.com","password":"smoke123"}' 2>/dev/null || echo '{}')

ACCESS_TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
if [ -z "$ACCESS_TOKEN" ]; then
  fail "登录失败，无法获取 accessToken。请先注册账号 smoke@test.com / smoke123"
  echo "跳过后续测试。退出码 1。"
  exit 1
fi
pass "登录成功，获取到 accessToken"

# ---- 步骤 2：发帖 ----
UNIQUE_TAG="smoke_$(date +%s)"
info "发帖，标签 $UNIQUE_TAG..."
CREATE_RESP=$(curl -sf -X POST "$GW/api/post/create" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"title\": \"smoke test post $UNIQUE_TAG\",
    \"content\": \"自动化冒烟测试帖子\",
    \"tags\": [\"$UNIQUE_TAG\"]
  }" 2>/dev/null || echo '{}')

POST_ID=$(echo "$CREATE_RESP" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -z "$POST_ID" ]; then
  fail "发帖失败。响应：$CREATE_RESP"
  exit 1
fi
pass "发帖成功，postId=$POST_ID"

# ---- 步骤 3：等待 sidecar 同步 ----
info "等待 ${SEARCH_DELAY}s 让 sidecar 将帖子同步到 ES..."
sleep "$SEARCH_DELAY"

# ---- 步骤 4：搜索验证 ----
info "搜索关键词 $UNIQUE_TAG..."
SEARCH_RESP=$(curl -sf "$GW/api/post/search?keyword=$UNIQUE_TAG&page=1&pageSize=10" \
  -H "Authorization: Bearer $ACCESS_TOKEN" 2>/dev/null || echo '{}')

if echo "$SEARCH_RESP" | grep -q "$POST_ID"; then
  pass "搜索结果中找到 postId=$POST_ID"
else
  fail "搜索结果中未找到 postId=$POST_ID。响应：$SEARCH_RESP"
fi

# ---- 步骤 5：搜索建议词 ----
info "测试搜索建议词 $UNIQUE_TAG..."
SUGGEST_RESP=$(curl -sf "$GW/api/post/search/suggest?keyword=$UNIQUE_TAG" \
  2>/dev/null || echo '{}')

if echo "$SUGGEST_RESP" | grep -q "$UNIQUE_TAG"; then
  pass "搜索建议词包含原词"
else
  fail "搜索建议词未包含原词。响应：$SUGGEST_RESP"
fi

# ---- 结果汇总 ----
echo ""
if [ "$FAILED" -eq 0 ]; then
  echo -e "${GREEN}=== 所有冒烟测试通过 ===${NC}"
  exit 0
else
  echo -e "${RED}=== $FAILED 个冒烟测试失败 ===${NC}"
  exit 1
fi
