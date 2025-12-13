#!/bin/bash

# ================= 配置加载区 =================
# 1. 尝试加载本地的 .deploy_env 文件 (如果存在)
if [ -f ".deploy_env" ]; then
    source .deploy_env
fi

# 2. 检查变量是否已设置 (无论是通过文件加载，还是系统环境变量)
# 如果变量为空，脚本将报错并停止，提示缺少配置
: "${SERVER_HOST:?❌ 错误: 未设置 SERVER_HOST，请在 .deploy_env 中配置或导出环境变量}"
: "${SERVER_USER:?❌ 错误: 未设置 SERVER_USER，请在 .deploy_env 中配置或导出环境变量}"
: "${REMOTE_PROJECT_PATH:?❌ 错误: 未设置 REMOTE_PROJECT_PATH}"
: "${JAR_NAME:?❌ 错误: 未设置 JAR_NAME}"
# ============================================

# 定义颜色，让输出更好看
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 开始全局计时
TOTAL_START=$SECONDS

echo -e "${BLUE}===============================================${NC}"
echo -e "${BLUE}   🚀 开始部署流程: Afternoon3 Backend Platform   ${NC}"
echo -e "${BLUE}===============================================${NC}"

# -----------------------------------------------------------------
# 1. 本地构建 (利用本地缓存和 CPU)
# -----------------------------------------------------------------
STEP1_START=$SECONDS
echo -e "${GREEN}📦 [1/3] 正在进行本地构建 (Maven)...${NC}"

# -q: 减少日志输出
# -DskipTests: 跳过测试 (因为本地环境可能连不上生产库)
./mvnw clean package -DskipTests

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 构建失败！请检查代码或 Maven 配置。${NC}"
    exit 1
fi

STEP1_DURATION=$(($SECONDS - $STEP1_START))
echo -e "✅ 构建完成！(耗时: ${STEP1_DURATION}秒)"

# -----------------------------------------------------------------
# 2. 同步文件 (使用 Rsync 增量传输)
# -----------------------------------------------------------------
STEP2_START=$SECONDS
echo -e "${GREEN}📤 [2/3] 正在同步文件到服务器...${NC}"

# 2.1 同步项目配置和脚本 (排除大文件和敏感文件)
# 注意：必须排除 .env，防止覆盖服务器上的生产配置！
echo "   >> 同步配置文件..."
rsync -rlvz --omit-dir-times --no-perms --no-owner --no-group \
    --exclude 'target' \
    --exclude '.git' \
    --exclude '.idea' \
    --exclude 'logs' \
    --exclude 'pg_data' \
    --exclude 'mongo_data' \
    --exclude 'grafana_data' \
    --exclude 'mvnw' \
    --exclude 'src' \
    --exclude '.env' \
    --exclude 'deploy.sh' \
    . ${SERVER_USER}@${SERVER_HOST}:${REMOTE_PROJECT_PATH}/

# 2.2 单独同步 JAR 包
echo "   >> 增量同步 JAR 包..."
# 确保目标目录存在
ssh ${SERVER_USER}@${SERVER_HOST} "mkdir -p ${REMOTE_PROJECT_PATH}/target"

# 只传输变化的字节，极大节省带宽
rsync -avz --progress target/${JAR_NAME} ${SERVER_USER}@${SERVER_HOST}:${REMOTE_PROJECT_PATH}/target/

STEP2_DURATION=$(($SECONDS - $STEP2_START))
echo -e "✅ 同步完成！(耗时: ${STEP2_DURATION}秒)"

# -----------------------------------------------------------------
# 3. 远程重启 (构建镜像并启动)
# -----------------------------------------------------------------
STEP3_START=$SECONDS
echo -e "${GREEN}🔄 [3/3] 正在远程重构并重启容器...${NC}"

# --build: 强制重新构建镜像 (读取新的 Jar)
# -d: 后台运行
ssh ${SERVER_USER}@${SERVER_HOST} "cd ${REMOTE_PROJECT_PATH} && \
docker compose up -d --build backend && \
docker image prune -f"

STEP3_DURATION=$(($SECONDS - $STEP3_START))
echo -e "✅ 重启完成！(耗时: ${STEP3_DURATION}秒)"

# -----------------------------------------------------------------
# 总结报告
# -----------------------------------------------------------------
TOTAL_DURATION=$(($SECONDS - $TOTAL_START))

echo -e "${BLUE}===============================================${NC}"
echo -e "${GREEN}🎉 部署成功！${NC}"
echo -e "-----------------------------------------------"
echo -e "📊 耗时统计:"
echo -e "   🔨 本地构建:  ${STEP1_DURATION} 秒"
echo -e "   📤 增量同步:  ${STEP2_DURATION} 秒"
echo -e "   🚀 远程重启:  ${STEP3_DURATION} 秒"
echo -e "-----------------------------------------------"
echo -e "⏱️  总耗时:      ${TOTAL_DURATION} 秒"
echo -e "${BLUE}===============================================${NC}"