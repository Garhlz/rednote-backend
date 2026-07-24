#!/usr/bin/env bash
# scripts/smoke/03_notification_unread.sh
# L3 冒烟测试：通知未读数与通知列表联动
# 依赖：docker compose 环境已启动
# 用法：./scripts/smoke/03_notification_unread.sh [gateway_url]

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

FAILED=0

# ---- 步骤 1：用两个账号登录（被点赞方 / 点赞方）----
info "登录 receiver 账号 $SMOKE_RECEIVER_EMAIL..."
RECV_TOKEN=$(require_token "$SMOKE_RECEIVER_EMAIL") || exit 1

info "登录 sender 账号 $SMOKE_SENDER_EMAIL..."
SEND_TOKEN=$(require_token "$SMOKE_SENDER_EMAIL") || exit 1
pass "两个账号均已登录"

# ---- 步骤 2：清理上次运行状态，保证脚本可重复执行 ----
info "清理上次运行留下的未读通知..."
request -X POST "$BASE_URL/api/message/read" \
  -H "Authorization: Bearer $RECV_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}' >/dev/null

info "获取 receiver 初始未读数..."
UNREAD_BEFORE=$(request "$BASE_URL/api/message/unread-count" \
  -H "Authorization: Bearer $RECV_TOKEN") || UNREAD_BEFORE='{"data":0}'
COUNT_BEFORE=$(printf '%s' "$UNREAD_BEFORE" | json_get 'data.get("data", 0)')
info "初始未读数：$COUNT_BEFORE"

# ---- 步骤 3：sender 点赞 receiver 的一篇帖子 ----
info "获取 receiver 的帖子..."
POSTS=$(request "$BASE_URL/api/user/posts?page=1&size=1" \
  -H "Authorization: Bearer $RECV_TOKEN") || POSTS='{}'
POST_ID=$(printf '%s' "$POSTS" | json_get 'data["data"]["records"][0]["id"]')
[ -z "$POST_ID" ] && { fail "receiver 没有帖子，请先发帖"; exit 1; }

request -X POST "$BASE_URL/api/interaction/unlike/post" \
  -H "Authorization: Bearer $SEND_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"targetId\":\"$POST_ID\"}" >/dev/null 2>&1 || true

info "sender 点赞帖子 $POST_ID..."
LIKE_RESP=$(request -X POST "$BASE_URL/api/interaction/like/post" \
  -H "Authorization: Bearer $SEND_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"targetId\":\"$POST_ID\"}") || {
    fail "点赞请求失败：$LIKE_RESP"
    exit 1
  }
pass "点赞请求完成"

# ---- 步骤 4：等待通知异步到达 ----
# ---- 步骤 5：验证未读数增加 ----
info "轮询 receiver 点赞后未读数..."
COUNT_AFTER="$COUNT_BEFORE"
for _ in $(seq 1 "${NOTIFICATION_ATTEMPTS:-10}"); do
  UNREAD_AFTER=$(request "$BASE_URL/api/message/unread-count" \
    -H "Authorization: Bearer $RECV_TOKEN") || UNREAD_AFTER='{"data":0}'
  COUNT_AFTER=$(printf '%s' "$UNREAD_AFTER" | json_get 'data.get("data", 0)')
  [ "$COUNT_AFTER" -gt "$COUNT_BEFORE" ] && break
  sleep 1
done
info "点赞后未读数：$COUNT_AFTER"

if [ "$COUNT_AFTER" -gt "$COUNT_BEFORE" ]; then
  pass "未读数在点赞后正确增加（${COUNT_BEFORE} → ${COUNT_AFTER}）"
else
  fail "未读数未增加（before=${COUNT_BEFORE} after=${COUNT_AFTER}），可能通知写入失败"
fi

# ---- 步骤 6：查看通知列表 ----
info "获取通知列表..."
NOTIF_LIST=$(request "$BASE_URL/api/message/notifications?page=1&size=10" \
  -H "Authorization: Bearer $RECV_TOKEN") || NOTIF_LIST='{}'
if echo "$NOTIF_LIST" | grep -qi "like\|LIKE\|点赞"; then
  pass "通知列表中包含点赞通知"
else
  fail "通知列表中未找到点赞通知。响应片段：$(echo "$NOTIF_LIST" | head -c 300)"
fi

# ---- 步骤 7：全部已读后未读数归零 ----
info "全部标记已读..."
request -X POST "$BASE_URL/api/message/read" \
  -H "Authorization: Bearer $RECV_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}' >/dev/null 2>&1 || true

sleep 1

UNREAD_FINAL=$(request "$BASE_URL/api/message/unread-count" \
  -H "Authorization: Bearer $RECV_TOKEN") || UNREAD_FINAL='{"data":-1}'
COUNT_FINAL=$(printf '%s' "$UNREAD_FINAL" | json_get 'data.get("data", -1)')
if [ "$COUNT_FINAL" -eq 0 ]; then
  pass "全部已读后未读数归零"
else
  fail "全部已读后未读数仍为 $COUNT_FINAL"
fi

# ---- 结果汇总 ----
echo ""
if [ "$FAILED" -eq 0 ]; then
  echo -e "${GREEN}=== 通知未读联动冒烟测试全部通过 ===${NC}"
  exit 0
else
  echo -e "${RED}=== $FAILED 个冒烟测试失败 ===${NC}"
  exit 1
fi
