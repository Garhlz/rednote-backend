#!/bin/bash

# ================= 配置加载区 =================
if [ -f ".deploy_env" ]; then
    source .deploy_env
fi

: "${SERVER_HOST:?❌ 错误: 未设置 SERVER_HOST}"
: "${SERVER_USER:?❌ 错误: 未设置 SERVER_USER}"
: "${REMOTE_PROJECT_PATH:?❌ 错误: 未设置 REMOTE_PROJECT_PATH}"
: "${JAR_NAME:?❌ 错误: 未设置 JAR_NAME}"
# ============================================

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

TOTAL_START=$SECONDS

echo -e "${BLUE}===============================================${NC}"
echo -e "${BLUE}   🚀 开始部署流程 (Java Backend + Go Sidecar)   ${NC}"
echo -e "${BLUE}===============================================${NC}"

# =================================================================
# 1. 本地构建 (Java JAR + Go Docker Image)
# =================================================================
STEP1_START=$SECONDS
echo -e "${GREEN}📦 [1/3] 正在进行本地构建...${NC}"

# 1.1 构建 Java
echo "   🔨 构建 Java Backend..."
./mvnw clean package -DskipTests
if [ $? -ne 0 ]; then exit 1; fi

# 1.2 构建 Go Sidecar Docker 镜像 (多阶段构建)
# 【修改】不再需要本地安装 Go 环境，直接让 Docker 负责编译
echo "   🐳 本地构建 Go Sidecar Docker 镜像..."
cd sync-sidecar || exit

# --platform linux/amd64: 确保服务器(Linux)能跑
docker build --platform linux/amd64 -t afternoon-sync-sidecar:latest .
if [ $? -ne 0 ]; then echo -e "${RED}❌ Sidecar 镜像构建失败${NC}"; exit 1; fi

# 1.3 保存镜像为 tar.gz
echo "   📦 打包镜像为 tar.gz..."
docker save afternoon-sync-sidecar:latest | gzip > sidecar-image.tar.gz
cd ..

STEP1_DURATION=$(($SECONDS - $STEP1_START))
echo -e "✅ 构建完成！(耗时: ${STEP1_DURATION}秒)"

# =================================================================
# 2. 同步文件
# =================================================================
STEP2_START=$SECONDS
echo -e "${GREEN}📤 [2/3] 正在同步文件到服务器...${NC}"

# 2.1 同步普通配置 (保持不变)
echo "   >> 同步配置文件..."
rsync -rlvz --omit-dir-times --no-perms --no-owner --no-group \
    --exclude 'target' --exclude 'sync-sidecar' --exclude '.git' \
    --exclude 'pg_data' --exclude 'mongo_data' --exclude 'es_data'  --exclude 'logs'\
    --exclude 'src' --exclude '.env' --exclude 'deploy.sh' \
    . ${SERVER_USER}@${SERVER_HOST}:${REMOTE_PROJECT_PATH}/

# 2.2 同步 Java JAR (保持不变)
echo "   >> 同步 Java JAR..."
ssh ${SERVER_USER}@${SERVER_HOST} "mkdir -p ${REMOTE_PROJECT_PATH}/target"
rsync -avz target/${JAR_NAME} ${SERVER_USER}@${SERVER_HOST}:${REMOTE_PROJECT_PATH}/target/

# 2.3 同步 Sidecar 镜像包 (保持不变)
echo "   >> 同步 Sidecar 镜像包..."
rsync -avz --progress sync-sidecar/sidecar-image.tar.gz ${SERVER_USER}@${SERVER_HOST}:${REMOTE_PROJECT_PATH}/

STEP2_DURATION=$(($SECONDS - $STEP2_START))
echo -e "✅ 同步完成！(耗时: ${STEP2_DURATION}秒)"

# =================================================================
# 3. 远程部署
# =================================================================
STEP3_START=$SECONDS
echo -e "${GREEN}🔄 [3/3] 正在远程加载镜像并重启...${NC}"

ssh ${SERVER_USER}@${SERVER_HOST} "cd ${REMOTE_PROJECT_PATH} && \
# 1. 加载上传的镜像
echo '   🐳 Loading Docker Image...' && \
docker load -i sidecar-image.tar.gz && \

# 2. 启动服务 (Backend 重新构建, Sidecar 使用刚才 Load 的镜像)
docker compose up -d --build backend sync-sidecar && \

# 3. 清理垃圾
rm sidecar-image.tar.gz && \
docker image prune -f"

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 远程部署失败！${NC}"
    exit 1
fi

STEP3_DURATION=$(($SECONDS - $STEP3_START))
echo -e "✅ 重启完成！(耗时: ${STEP3_DURATION}秒)"