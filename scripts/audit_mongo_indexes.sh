#!/bin/bash

# MongoDB 数据库连接 URI
MONGO_URI="mongodb://localhost:27017/rednote"

# 需要审计索引的集合列表
COLLECTIONS=("comments" "comment_likes" "notifications" "posts" "user_follows")

echo "开始审计 MongoDB 索引 (数据库: rednote)..."
echo ""

# 判断可用的 MongoDB 客户端
if command -v mongosh &> /dev/null; then
    CMD="mongosh --quiet"
elif command -v mongo &> /dev/null; then
    CMD="mongo --quiet"
else
    # 尝试在运行中的 Docker 容器内执行
    MONGO_CONTAINER=$(docker ps --format '{{.Names}}' | grep -i "mongo" | head -n 1)
    if [ -n "$MONGO_CONTAINER" ]; then
        if docker exec "$MONGO_CONTAINER" command -v mongosh &> /dev/null; then
            CMD="docker exec $MONGO_CONTAINER mongosh --quiet"
        else
            CMD="docker exec $MONGO_CONTAINER mongo --quiet"
        fi
    else
        echo "错误: 未找到本地 mongosh 或 mongo 客户端，且未发现运行中的 MongoDB 容器。"
        exit 1
    fi
fi

# 遍历集合并查询索引
for col in "${COLLECTIONS[@]}"; do
    echo "=================================================="
    echo "集合: $col"
    echo "=================================================="
    $CMD "$MONGO_URI" --eval "printjson(db.getCollection('$col').getIndexes())"
    echo ""
done

echo "审计完成。"
