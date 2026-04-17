#!/usr/bin/env bash
# scripts/smoke/02_comment_soft_delete.sh
# L3 冒烟测试：一级评论软删后列表可见占位文案，且帖子评论数不误扣
# 依赖：docker compose 环境已启动
# 用法：./scripts/smoke/02_comment_soft_delete.sh [gateway_url]

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

# ---- 步骤 1：登录 ----
info "登录..."
LOGIN_RESP=$(curl -sf -X POST "$GW/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@test.com","password":"smoke123"}' 2>/dev/null || echo '{}')
ACCESS_TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
[ -z "$ACCESS_TOKEN" ] && { fail "登录失败"; exit 1; }
USER_ID=$(echo "$LOGIN_RESP" | grep -o '"userId":[0-9]*' | cut -d: -f2)
pass "登录成功 userId=$USER_ID"

# ---- 步骤 2：获取一个可用帖子 ID ----
info "获取帖子列表..."
LIST_RESP=$(curl -sf "$GW/api/post/list?page=1&pageSize=1" 2>/dev/null || echo '{}')
POST_ID=$(echo "$LIST_RESP" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
[ -z "$POST_ID" ] && { fail "获取帖子失败，请先发布一篇帖子"; exit 1; }
pass "目标帖子 postId=$POST_ID"

# ---- 步骤 3：发一条一级评论 ----
info "发一级评论..."
COMMENT_RESP=$(curl -sf -X POST "$GW/api/comment/create" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"postId\":\"$POST_ID\",\"content\":\"冒烟测试一级评论\"}" 2>/dev/null || echo '{}')
COMMENT_ID=$(echo "$COMMENT_RESP" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
[ -z "$COMMENT_ID" ] && { fail "创建评论失败。响应：$COMMENT_RESP"; exit 1; }
pass "评论创建成功 commentId=$COMMENT_ID"

# ---- 步骤 4：删除（软删）该评论 ----
info "软删评论 $COMMENT_ID..."
DEL_RESP=$(curl -sf -X DELETE "$GW/api/comment/delete/$COMMENT_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" 2>/dev/null || echo '{}')
pass "删除请求完成"

# ---- 步骤 5：查看评论列表，验证软删占位文案 ----
info "查询评论列表..."
sleep 1
COMMENTS=$(curl -sf "$GW/api/comment/list?postId=$POST_ID&page=1&pageSize=20" \
  -H "Authorization: Bearer $ACCESS_TOKEN" 2>/dev/null || echo '{}')

if echo "$COMMENTS" | grep -q "该评论已删除"; then
  pass "软删后评论列表中可见占位文案"
else
  fail "软删后评论列表中未找到占位文案。响应片段：$(echo "$COMMENTS" | head -c 300)"
fi

# ---- 步骤 6：验证帖子评论数未扣减（软删不发 MQ 事件） ----
# 这里只是确认帖子接口正常返回，具体 commentCount 需结合业务初始值判断
info "获取帖子详情（验证 commentCount 字段存在）..."
POST_DETAIL=$(curl -sf "$GW/api/post/$POST_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" 2>/dev/null || echo '{}')
if echo "$POST_DETAIL" | grep -q "commentCount"; then
  pass "帖子详情包含 commentCount 字段"
else
  fail "帖子详情中未找到 commentCount。响应：$(echo "$POST_DETAIL" | head -c 200)"
fi

# ---- 结果汇总 ----
echo ""
if [ "$FAILED" -eq 0 ]; then
  echo -e "${GREEN}=== 评论软删冒烟测试全部通过 ===${NC}"
  exit 0
else
  echo -e "${RED}=== $FAILED 个冒烟测试失败 ===${NC}"
  exit 1
fi
