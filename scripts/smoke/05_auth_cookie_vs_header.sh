#!/usr/bin/env bash
# 05_auth_cookie_vs_header.sh
# 验证：Cookie 鉴权（accessToken cookie）与 Header 鉴权（Authorization: Bearer）
# 两种路径都能正常访问评论/通知接口
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8090}"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
pass() { echo -e "${GREEN}[PASS]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }

echo ">>> [05] Cookie 鉴权 vs Header 鉴权双路径验证"

# ---- 1. 登录（/api/auth/login）获取 Token ----
LOGIN_RESP=$(curl -sf -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@test.com","password":"smoke123"}')
TOKEN=$(echo "$LOGIN_RESP" | python3 -c \
  "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])" 2>/dev/null || true)

if [ -z "$TOKEN" ]; then
  TOKEN="${SMOKE_TOKEN:-}"
  [ -z "$TOKEN" ] && fail "无法获取登录 Token，请设置 SMOKE_TOKEN 环境变量"
fi

# ---- 2. Header 模式：Authorization: Bearer <token> ----

# 2a. 通知未读数（需要登录）
UNREAD_RESP=$(curl -sf "$BASE_URL/api/message/unread-count" \
  -H "Authorization: Bearer $TOKEN")
UNREAD_CODE=$(echo "$UNREAD_RESP" | python3 -c \
  "import sys,json; print(json.load(sys.stdin).get('code', -1))" 2>/dev/null || echo "-1")
[ "$UNREAD_CODE" = "0" ] \
  && pass "Header 鉴权：/api/message/unread-count code=0" \
  || fail "Header 鉴权：/api/message/unread-count 返回 code=$UNREAD_CODE，预期 0"

# 2b. 评论列表（登录态，任意 postId，只验证鉴权不返回 401）
COMMENT_HTTP=$(curl -sf -o /dev/null -w "%{http_code}" \
  "$BASE_URL/api/comment/list?postId=000000000000000000000000&page=1&pageSize=5" \
  -H "Authorization: Bearer $TOKEN")
[ "$COMMENT_HTTP" != "401" ] \
  && pass "Header 鉴权：/api/comment/list 不返回 401（HTTP $COMMENT_HTTP）" \
  || fail "Header 鉴权：/api/comment/list 返回 401，鉴权失败"

# ---- 3. Cookie 模式：Cookie: accessToken=<token>（与 auth.go 中 r.Cookie("accessToken") 对齐）----

# 3a. 通知未读数
UNREAD_COOKIE_RESP=$(curl -sf "$BASE_URL/api/message/unread-count" \
  -H "Cookie: accessToken=$TOKEN")
UNREAD_COOKIE_CODE=$(echo "$UNREAD_COOKIE_RESP" | python3 -c \
  "import sys,json; print(json.load(sys.stdin).get('code', -1))" 2>/dev/null || echo "-1")
[ "$UNREAD_COOKIE_CODE" = "0" ] \
  && pass "Cookie 鉴权：/api/message/unread-count code=0" \
  || fail "Cookie 鉴权：/api/message/unread-count 返回 code=$UNREAD_COOKIE_CODE，预期 0"

# 3b. 评论列表
COMMENT_COOKIE_HTTP=$(curl -sf -o /dev/null -w "%{http_code}" \
  "$BASE_URL/api/comment/list?postId=000000000000000000000000&page=1&pageSize=5" \
  -H "Cookie: accessToken=$TOKEN")
[ "$COMMENT_COOKIE_HTTP" != "401" ] \
  && pass "Cookie 鉴权：/api/comment/list 不返回 401（HTTP $COMMENT_COOKIE_HTTP）" \
  || fail "Cookie 鉴权：/api/comment/list 返回 401，鉴权失败"

# ---- 4. 无 Token：公开接口（搜索）不需要鉴权 ----
SEARCH_HTTP=$(curl -sf -o /dev/null -w "%{http_code}" \
  "$BASE_URL/api/post/search?keyword=test&page=1&pageSize=5")
[ "$SEARCH_HTTP" != "401" ] \
  && pass "无 Token：公开搜索接口不返回 401（HTTP $SEARCH_HTTP）" \
  || fail "无 Token：公开搜索接口返回 401，不应要求鉴权"

# ---- 5. 无 Token：需鉴权接口应返回 401 ----
NOTIF_HTTP=$(curl -sf -o /dev/null -w "%{http_code}" \
  "$BASE_URL/api/message/unread-count")
[ "$NOTIF_HTTP" = "401" ] \
  && pass "无 Token：/api/message/unread-count 正确返回 401" \
  || fail "无 Token：/api/message/unread-count 返回 $NOTIF_HTTP，预期 401"

echo ""
echo -e "${GREEN}[05] Cookie 鉴权 vs Header 鉴权双路径验证通过${NC}"
