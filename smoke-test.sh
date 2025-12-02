#!/bin/bash

# 这里的 localhost 对应宿主机，因为你做了 8080:8080 映射
BASE_URL="http://8.148.145.178:8080"

# 颜色定义，方便看结果
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo "🚀 开始生产环境冒烟测试..."

# ==========================================
# 1. 测试账号登录 (API: /api/auth/login/account)
# ==========================================
echo -n "1. 尝试登录 "

# 发送 POST 请求，获取响应
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login/account" \
  -H "Content-Type: application/json" \
  -d '{
    "account": "test3@test.com",
    "password": "123456"
  }')

# 简单的字符串匹配检查是否成功 (检查 code: 20000)
# 注意：这里使用 grep -q 来静默查找
if echo "$LOGIN_RESPONSE" | grep -q 200; then
    echo -e "${GREEN}[OK]${NC}"

    # 使用 sed 提取 token (模拟 JSON 解析)
    TOKEN=$(echo "$LOGIN_RESPONSE" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

    if [ -z "$TOKEN" ]; then
         echo -e "${RED}[ERROR]${NC} 登录成功但未提取到 Token！"
         echo "响应内容: $LOGIN_RESPONSE"
         exit 1
    fi
    echo "   🔑 Token: ${TOKEN:0:20}..."
else
    echo -e "${RED}[FAILED]${NC}"
    echo "   ❌ 登录失败，响应内容: $LOGIN_RESPONSE"
    echo "   请检查：数据库是否已插入数据？密码是否正确？"
    exit 1
fi

# ==========================================
# 2. 测试获取个人资料 (API: /api/user/profile)
# ==========================================
echo -n "2. 验证 Token 获取资料... "

# 发送 GET 请求，带上 Bearer Token
PROFILE_RESPONSE=$(curl -s -X GET "$BASE_URL/api/user/profile" \
  -H "Authorization: Bearer $TOKEN")

if echo "$PROFILE_RESPONSE" | grep -q 200; then
    echo -e "${GREEN}[OK]${NC}"
    # 提取昵称验证
    NICKNAME=$(echo "$PROFILE_RESPONSE" | sed -n 's/.*"nickname":"\([^"]*\)".*/\1/p')
    echo "   👤 当前用户: $NICKNAME"

    # 简单验证昵称是否匹配
    if [[ "$NICKNAME" != *"冒烟测试员"* ]] && [[ "$PROFILE_RESPONSE" != *"冒烟测试员"* ]]; then
         echo -e "${RED}[WARNING]${NC} 昵称似乎不匹配，请人工核对。"
    fi
else
    echo -e "${RED}[FAILED]${NC}"
    echo "   ❌ 获取资料失败，响应内容: $PROFILE_RESPONSE"
    exit 1
fi

echo "------------------------------------------------"
echo -e "✅✅✅ 冒烟测试全部通过！服务运行正常。 ✅✅✅"