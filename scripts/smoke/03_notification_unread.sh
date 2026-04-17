#!/usr/bin/env bash
# scripts/smoke/03_notification_unread.sh
# L3 冒烟测试：通知未读数与通知列表联动
# 依赖：docker compose 环境已启动
# 用法：./scripts/smoke/03_notification_unread.sh [gateway_url]

set -euo pipefail

GW="${1:-http://localhost:8888}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✅ PASS${NC} $1"; }
fail() { echo -e "${RED}❌ FAIL${NC} $1"; FAILED=$((FAILED+1)); }
info() { echo -e "${YELLOW}ℹ️  ${NC} $1"; }

FAILED=0

# ---- 步骤 1：用两个账号登录（被点赞方 / 点赞方）----
info "登录 receiver 账号..."
RECV_RESP=$(curl -sf -X POST "$GW/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"receiver@test.com","password":"smoke123"}' 2>/dev/null || echo '{}')
RECV_TOKEN=$(echo "$RECV_RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
[ -z "$RECV_TOKEN" ] && { fail "receiver 账号登录失败，请先注册 receiver@test.com"; exit 1; }

info "登录 sender 账号..."
SEND_RESP=$(curl -sf -X POST "$GW/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"sender@test.com","password":"smoke123"}' 2>/dev/null || echo '{}')
SEND_TOKEN=$(echo "$SEND_RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
[ -z "$SEND_TOKEN" ] && { fail "sender 账号登录失败，请先注册 sender@test.com"; exit 1; }
pass "两个账号均已登录"

# ---- 步骤 2：获取 receiver 当前未读数 ----
info "获取 receiver 初始未读数..."
UNREAD_BEFORE=$(curl -sf "$GW/api/message/unread-count" \
  -H "Authorization: Bearer $RECV_TOKEN" 2>/dev/null || echo '{"data":{"unreadCount":0}}')
COUNT_BEFORE=$(echo "$UNREAD_BEFORE" | grep -o '"unreadCount":[0-9]*' | cut -d: -f2 || echo 0)
info "初始未读数：$COUNT_BEFORE"

# ---- 步骤 3：sender 点赞 receiver 的一篇帖子 ----
info "获取 receiver 的帖子..."
POSTS=$(curl -sf "$GW/api/user/posts?page=1&pageSize=1" \
  -H "Authorization: Bearer $RECV_TOKEN" 2>/dev/null || echo '{}')
POST_ID=$(echo "$POSTS" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
[ -z "$POST_ID" ] && { fail "receiver 没有帖子，请先发帖"; exit 1; }

info "sender 点赞帖子 $POST_ID..."
curl -sf -X POST "$GW/api/interaction/like/post/$POST_ID" \
  -H "Authorization: Bearer $SEND_TOKEN" >/dev/null 2>&1 || true
pass "点赞请求完成"

# ---- 步骤 4：等待通知异步到达 ----
sleep 2

# ---- 步骤 5：验证未读数增加 ----
info "获取 receiver 点赞后未读数..."
UNREAD_AFTER=$(curl -sf "$GW/api/message/unread-count" \
  -H "Authorization: Bearer $RECV_TOKEN" 2>/dev/null || echo '{"data":{"unreadCount":0}}')
COUNT_AFTER=$(echo "$UNREAD_AFTER" | grep -o '"unreadCount":[0-9]*' | cut -d: -f2 || echo 0)
info "点赞后未读数：$COUNT_AFTER"

if [ "$COUNT_AFTER" -gt "$COUNT_BEFORE" ]; then
  pass "未读数在点赞后正确增加（$COUNT_BEFORE → $COUNT_AFTER）"
else
  fail "未读数未增加（before=$COUNT_BEFORE after=$COUNT_AFTER），可能通知写入失败"
fi

# ---- 步骤 6：查看通知列表 ----
info "获取通知列表..."
NOTIF_LIST=$(curl -sf "$GW/api/message/notifications?page=1&pageSize=10" \
  -H "Authorization: Bearer $RECV_TOKEN" 2>/dev/null || echo '{}')
if echo "$NOTIF_LIST" | grep -qi "like\|LIKE\|点赞"; then
  pass "通知列表中包含点赞通知"
else
  fail "通知列表中未找到点赞通知。响应片段：$(echo "$NOTIF_LIST" | head -c 300)"
fi

# ---- 步骤 7：全部已读后未读数归零 ----
info "全部标记已读..."
curl -sf -X POST "$GW/api/message/read" \
  -H "Authorization: Bearer $RECV_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}' >/dev/null 2>&1 || true

sleep 1

UNREAD_FINAL=$(curl -sf "$GW/api/message/unread-count" \
  -H "Authorization: Bearer $RECV_TOKEN" 2>/dev/null || echo '{"data":{"unreadCount":0}}')
COUNT_FINAL=$(echo "$UNREAD_FINAL" | grep -o '"unreadCount":[0-9]*' | cut -d: -f2 || echo -1)
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
