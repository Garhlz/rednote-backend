#!/bin/bash

# ==============================================================================
# verify_full_integration.sh
#
# 用于验证核心链路和修复回归的联调脚本。
# 基于文档 P2 阶段需求编写：
# 1. 一级评论软删回归
# 2. 二级评论删除后 replyCount 回归
# 3. 状态型通知重复 upsert 回归
# 4. Cookie / Header 双鉴权模式回归
# 5. sidecar 全量重建 ES 回归
#
# 使用说明：
# 1. 确保本地服务已启动 (docker-compose up -d)
# 2. 根据需要修改下方的变量配置（如 GATEWAY_URL, 各种 ID 和 TOKEN）
# 3. 赋予执行权限并运行: chmod +x scripts/verify_full_integration.sh && ./scripts/verify_full_integration.sh
# ==============================================================================

# 配置网关地址
GATEWAY_URL=${GATEWAY_URL:-"http://localhost:8090"}

# ------------------------------------------------------------------------------
# 认证配置
# ------------------------------------------------------------------------------
# 填入有效的登录 Token，用于请求鉴权
USER_TOKEN="your_valid_jwt_token_here"
# 填入另一个用户的 Token，用于测试交互（比如评论别人的帖子）
OTHER_USER_TOKEN="another_valid_jwt_token_here"

# ------------------------------------------------------------------------------
# 测试数据准备 (请替换为实际环境中的有效 ID)
# ------------------------------------------------------------------------------
TEST_POST_ID="1234567890"        # 用于评论测试的帖子ID
TEST_USER_ID="9876543210"        # 用于通知测试的目标用户ID
TEST_COMMENT_ID=""               # 运行过程中动态生成并赋值

echo "===================================================================="
echo "开始运行集成联调回归测试脚本"
echo "网关地址: $GATEWAY_URL"
echo "===================================================================="
echo ""


# ==============================================================================
# 1. 一级评论软删回归
# 验证：删除一级评论时，状态应变为软删，且相关统计数据正确处理。
# ==============================================================================
echo ">>> [测试 1] 一级评论软删回归"

# 1.1 创建一级评论
echo "  [1.1] 创建一级评论..."
CREATE_COMMENT_RESP=$(curl -s -X POST "$GATEWAY_URL/api/comment/create" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "postId": "'$TEST_POST_ID'",
    "content": "这是一条用于软删测试的一级评论"
  }')
echo "  响应: $CREATE_COMMENT_RESP"

# 尝试从响应中提取生成的 commentId (使用 jq，如果没有 jq 则需要手动获取并设置)
if command -v jq &> /dev/null; then
    TEST_COMMENT_ID=$(echo "$CREATE_COMMENT_RESP" | jq -r '.data.commentId // empty')
    echo "  提取到的评论ID: $TEST_COMMENT_ID"
else
    echo "  [提示] 未安装 jq，无法自动提取 commentId，后续步骤可能需要手动替换。"
fi

# 1.2 删除刚刚创建的一级评论
if [ -n "$TEST_COMMENT_ID" ]; then
    echo "  [1.2] 删除一级评论 (ID: $TEST_COMMENT_ID)..."
    DELETE_COMMENT_RESP=$(curl -s -X POST "$GATEWAY_URL/api/comment/delete" \
      -H "Authorization: Bearer $USER_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{
        "commentId": "'$TEST_COMMENT_ID'"
      }')
    echo "  响应: $DELETE_COMMENT_RESP"
    echo "  [预期] 响应成功，且底层数据库中该评论记录存在但状态标记为已删除（例如 status=0 或 isDeleted=true）。"
    echo "  [预期] mq 事件应正确发送，但不应导致 post.commentCount 错误扣减（验证之前修复的 bug）。"
else
    echo "  [跳过 1.2] 由于没有 commentId，跳过删除操作。"
fi
echo ""


# ==============================================================================
# 2. 二级评论删除后 replyCount 回归
# 验证：删除二级（子）评论时，其所属的一级评论的 replyCount 应该正确扣减，不出现重复扣减。
# ==============================================================================
echo ">>> [测试 2] 二级评论删除后 replyCount 回归"

# 此处需要先有一个存在的一级评论ID，假设重新创建一个
echo "  [2.1] 创建新的一级评论..."
ROOT_COMMENT_RESP=$(curl -s -X POST "$GATEWAY_URL/api/comment/create" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "postId": "'$TEST_POST_ID'",
    "content": "这是父评论，用于测试 replyCount"
  }')
ROOT_COMMENT_ID=""
if command -v jq &> /dev/null; then
    ROOT_COMMENT_ID=$(echo "$ROOT_COMMENT_RESP" | jq -r '.data.commentId // empty')
fi

if [ -n "$ROOT_COMMENT_ID" ]; then
    echo "  [2.2] 创建基于父评论 ($ROOT_COMMENT_ID) 的二级评论..."
    SUB_COMMENT_RESP=$(curl -s -X POST "$GATEWAY_URL/api/comment/create" \
      -H "Authorization: Bearer $USER_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{
        "postId": "'$TEST_POST_ID'",
        "content": "这是子评论",
        "replyToCommentId": "'$ROOT_COMMENT_ID'",
        "rootCommentId": "'$ROOT_COMMENT_ID'"
      }')
    echo "  响应: $SUB_COMMENT_RESP"
    SUB_COMMENT_ID=""
    if command -v jq &> /dev/null; then
        SUB_COMMENT_ID=$(echo "$SUB_COMMENT_RESP" | jq -r '.data.commentId // empty')
    fi

    if [ -n "$SUB_COMMENT_ID" ]; then
        echo "  [2.3] 删除二级评论 ($SUB_COMMENT_ID)..."
        DEL_SUB_RESP=$(curl -s -X POST "$GATEWAY_URL/api/comment/delete" \
          -H "Authorization: Bearer $USER_TOKEN" \
          -H "Content-Type: application/json" \
          -d '{
            "commentId": "'$SUB_COMMENT_ID'"
          }')
        echo "  响应: $DEL_SUB_RESP"
        echo "  [预期] 父评论 $ROOT_COMMENT_ID 的 replyCount 应该减 1，不能减成负数或多减。"
    fi
else
     echo "  [跳过] 未能获取父评论ID。"
fi
echo ""


# ==============================================================================
# 3. 状态型通知重复 upsert 回归
# 验证：像“点赞”、“收藏”这类状态型通知，多次触发（点赞-取消-再点赞）时，通知表能正确 upsert 状态，不产生重复记录。
# ==============================================================================
echo ">>> [测试 3] 状态型通知重复 upsert 回归"

echo "  [3.1] 模拟第一次操作：对帖子点赞 (通过 interaction 接口或模拟发 mq)"
# 假设有一个交互点赞接口
curl -s -X POST "$GATEWAY_URL/api/interaction/like" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "targetId": "'$TEST_POST_ID'",
    "targetType": 1,
    "action": 1
  }' | jq . || echo "  响应未格式化"

echo "  [3.2] 模拟取消操作：取消点赞"
curl -s -X POST "$GATEWAY_URL/api/interaction/like" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "targetId": "'$TEST_POST_ID'",
    "targetType": 1,
    "action": 0
  }' | jq . || echo "  响应未格式化"

echo "  [3.3] 模拟第二次操作：再次点赞"
curl -s -X POST "$GATEWAY_URL/api/interaction/like" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "targetId": "'$TEST_POST_ID'",
    "targetType": 1,
    "action": 1
  }' | jq . || echo "  响应未格式化"

echo "  [预期] 通知数据库中，对于该用户对该帖子的点赞通知记录只有一条，且状态为已更新。"
echo ""


# ==============================================================================
# 4. Cookie / Header 双鉴权模式回归
# 验证：网关能正确处理放置在 Authorization Header 或 Cookie 中的 Token。
# ==============================================================================
echo ">>> [测试 4] Cookie / Header 双鉴权模式回归"

echo "  [4.1] 使用 Authorization Header 请求未读消息数"
curl -s -X GET "$GATEWAY_URL/api/message/unread-count" \
  -H "Authorization: Bearer $USER_TOKEN" | jq . || echo "请求失败"

echo "  [4.2] 使用 Cookie (accessToken) 请求未读消息数"
# 注意：确认网关支持的 Cookie key，这里假设是 accessToken 或 Authorization
curl -s -X GET "$GATEWAY_URL/api/message/unread-count" \
  -b "accessToken=$USER_TOKEN" | jq . || echo "请求失败"

echo "  [预期] 两次请求都能成功鉴权并返回业务数据，没有 401 错误。"
echo ""


# ==============================================================================
# 5. sidecar 全量重建 ES 回归
# 验证：能通过接口或命令触发 sidecar 全量同步/重建 ES 索引，且过程正常完成。
# ==============================================================================
echo ">>> [测试 5] sidecar 全量重建 ES 回归"

# 这里假设 sidecar 暴露了一个 admin 或内部管理接口用于触发全量同步
# 如果是通过发 MQ 消息触发，这里提供发消息的示例接口
echo "  [5.1] 触发 ES 全量重建..."
# TODO: 替换为实际的全量重建触发接口 (可能在内部端口或特定的 admin 路径)
REBUILD_URL="$GATEWAY_URL/api/internal/search/rebuild-index"

# 尝试调用 (如果接口不存在会 404，属于正常演示)
echo "  调用: curl -X POST $REBUILD_URL"
curl -s -X POST "$REBUILD_URL" \
  -H "Authorization: Bearer $USER_TOKEN" | jq . || echo "  可能是尚未开放的内部接口，请视情况修改。"

echo "  [预期] 触发成功后，ES 索引数据应当和数据库（Mongo/MySQL）帖子数据一致，可以通过搜索接口验证结果数量。"
echo "  可以通过如下搜索请求验证："
echo "  curl -s -X GET \"$GATEWAY_URL/api/post/search?keyword=test\""
echo ""

echo "===================================================================="
echo "测试脚本执行完毕。"
echo "请手动检查各步骤的输出和 [预期] 结果是否吻合。"
echo "===================================================================="
