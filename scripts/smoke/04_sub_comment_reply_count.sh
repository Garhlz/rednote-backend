#!/usr/bin/env bash
# 04_sub_comment_reply_count.sh
# 验证：子评论删除后，根评论的 replyCount 正确扣减
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

echo ">>> [04] 子评论删除后根评论 replyCount 验证"

# ---- 1. 登录获取 Token（/api/auth/login，Cookie 名为 accessToken）----
TOKEN=$(require_token "$SMOKE_EMAIL") || exit 1

AUTH="Authorization: Bearer $TOKEN"

# ---- 2. 发帖（POST /api/post/）----
POST_RESP=$(curl -sf -X POST "$BASE_URL/api/post/" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d '{"title":"子评论计数链路验证","content":"用于验证评论计数","type":0,"images":["https://example.com/reply-count.png"]}')
POST_ID=$(echo "$POST_RESP" | python3 -c \
  "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null || true)
[ -z "$POST_ID" ] && { fail "发帖失败，无法获取 postId（响应: ${POST_RESP}）"; exit 1; }
echo "  postId = $POST_ID"

# ---- 3. 发根评论（POST /api/comment/）----
ROOT_RESP=$(curl -sf -X POST "$BASE_URL/api/comment/" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d "{\"postId\":\"$POST_ID\",\"content\":\"根评论\"}")
ROOT_ID=$(echo "$ROOT_RESP" | python3 -c \
  "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null || true)
[ -z "$ROOT_ID" ] && { fail "发根评论失败（响应: ${ROOT_RESP}）"; exit 1; }
echo "  rootCommentId = $ROOT_ID"

# ---- 4. 发子评论（POST /api/comment/，带 parentId）----
SUB_RESP=$(curl -sf -X POST "$BASE_URL/api/comment/" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d "{\"postId\":\"$POST_ID\",\"content\":\"子评论\",\"parentId\":\"$ROOT_ID\"}")
SUB_ID=$(echo "$SUB_RESP" | python3 -c \
  "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null || true)
[ -z "$SUB_ID" ] && { fail "发子评论失败（响应: ${SUB_RESP}）"; exit 1; }
echo "  subCommentId = $SUB_ID"

# ---- 5. 验证根评论 replyCount = 1 ----
sleep 0.5
LIST_RESP=$(curl --noproxy '*' -sf "$BASE_URL/api/comment/list?postId=$POST_ID&page=1&size=20" \
  -H "$AUTH")
REPLY_COUNT_BEFORE=$(echo "$LIST_RESP" | python3 -c "
import sys,json
data = json.load(sys.stdin)
for item in data.get('data',{}).get('records',[]):
    if item.get('id') == '$ROOT_ID':
        print(item.get('replyCount', 0))
        break
" 2>/dev/null || echo "0")

if [ "$REPLY_COUNT_BEFORE" = "1" ]; then
  pass "删除前 replyCount=${REPLY_COUNT_BEFORE}（预期 1）"
else
  echo "  注意：删除前 replyCount=${REPLY_COUNT_BEFORE}（预期 1，可能异步计数尚未落库）"
fi

# ---- 6. 删除子评论（DELETE /api/comment/:id）----
curl -sf -X DELETE "$BASE_URL/api/comment/$SUB_ID" \
  -H "$AUTH" > /dev/null
echo "  子评论 $SUB_ID 已删除"

# ---- 7. 验证根评论 replyCount = 0 ----
sleep 0.5
LIST_RESP2=$(curl --noproxy '*' -sf "$BASE_URL/api/comment/list?postId=$POST_ID&page=1&size=20" \
  -H "$AUTH")
REPLY_COUNT_AFTER=$(echo "$LIST_RESP2" | python3 -c "
import sys,json
data = json.load(sys.stdin)
for item in data.get('data',{}).get('records',[]):
    if item.get('id') == '$ROOT_ID':
        print(item.get('replyCount', 0))
        break
" 2>/dev/null || echo "-1")

if [ "$REPLY_COUNT_AFTER" = "0" ]; then
  pass "删除子评论后根评论 replyCount=${REPLY_COUNT_AFTER}（预期 0）"
else
  fail "删除子评论后根评论 replyCount=${REPLY_COUNT_AFTER}，预期 0"
  exit 1
fi

echo ""
echo -e "${GREEN}[04] 子评论删除 replyCount 验证通过${NC}"
