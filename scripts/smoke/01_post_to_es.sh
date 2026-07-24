#!/usr/bin/env bash
# scripts/smoke/01_post_to_es.sh
# L3 冒烟测试：发帖 → sidecar → ES 可搜索
# 依赖：docker compose 环境已启动（含 Java 服务）
# 用法：./scripts/smoke/01_post_to_es.sh [gateway_url]

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

FAILED=0

# ---- 步骤 1：登录获取 token ----
info "登录获取 access token..."
ACCESS_TOKEN=$(require_token "$SMOKE_EMAIL") || exit 1
pass "登录成功，获取到 accessToken"

# ---- 步骤 2：发帖 ----
UNIQUE_TAG="chain_$(date +%s)"
info "发帖，标签 $UNIQUE_TAG..."
CREATE_RESP=$(request -X POST "$BASE_URL/api/post/" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"title\": \"自动化链路验证 $UNIQUE_TAG\",
    \"content\": \"用于验证发布与搜索同步链路\",
    \"type\": 0,
    \"images\": [\"https://example.com/smoke-test.png\"],
    \"tags\": [\"$UNIQUE_TAG\"]
  }") || { fail "发帖请求失败：$CREATE_RESP"; exit 1; }

POST_ID=$(printf '%s' "$CREATE_RESP" | json_get 'data["data"]["id"]')
if [ -z "$POST_ID" ]; then
  fail "发帖失败。响应：$CREATE_RESP"
  exit 1
fi
pass "发帖成功，postId=$POST_ID"

# ---- 步骤 3：等待 sidecar 同步 ----
info "轮询等待 sidecar 将帖子同步到 ES..."
FOUND=""
for _ in $(seq 1 "${SEARCH_ATTEMPTS:-10}"); do
  SEARCH_RESP=$(request "$BASE_URL/api/post/search?keyword=$UNIQUE_TAG&page=1&size=10" \
    -H "Authorization: Bearer $ACCESS_TOKEN") || SEARCH_RESP='{}'
  FOUND=$(printf '%s' "$SEARCH_RESP" | json_get "any(str(item.get('id')) == '$POST_ID' for item in data.get('data', {}).get('records', []))")
  [ "$FOUND" = "True" ] && break
  sleep 1
done

if [ "$FOUND" = "True" ]; then
  pass "搜索结果中找到 postId=$POST_ID"
else
  fail "搜索结果中未找到 postId=${POST_ID}。响应：${SEARCH_RESP}"
fi

# ---- 步骤 5：搜索建议词 ----
info "测试搜索建议词 $UNIQUE_TAG..."
SUGGEST_RESP=$(request "$BASE_URL/api/post/search/suggest?keyword=$UNIQUE_TAG") || SUGGEST_RESP='{}'

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
