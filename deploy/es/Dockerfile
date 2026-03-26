# ==========================================
# 第一阶段：建筑工 (Builder)
# 用一个轻量级的 Alpine 镜像来处理解压
# ==========================================
FROM alpine:3.18 AS plugin-builder

# 设置临时工作目录
WORKDIR /tmp/plugins

# 1. 将本地下载好的 zip 包复制进这个临时容器
# 注意：路径是相对于你的 build context（根目录）
COPY deploy/es/ik.zip .
COPY deploy/es/pinyin.zip .

# 2. 安装 unzip 工具并解压插件到指定目录
# 这样不仅避免了 file:// 协议的复杂性，还比 elasticsearch-plugin install 更快
RUN apk add --no-cache unzip && \
    mkdir -p ik pinyin && \
    unzip -q ik.zip -d ik && \
    unzip -q pinyin.zip -d pinyin && \
    # 这一步清理是彻底的，因为这个 alpine 镜像最后会被丢弃
    rm ik.zip pinyin.zip

# ==========================================
# 第二阶段：正式镜像 (Final)
# ==========================================
FROM elasticsearch:8.11.1

# 设置 ES 的插件目录变量，方便阅读
ARG ES_PLUGIN_DIR=/usr/share/elasticsearch/plugins
# 3. 【关键优化】直接从上一个阶段 (plugin-builder) 拷贝解压好的文件夹
# 同时使用 --chown=elasticsearch:elasticsearch 确保权限正确
# 这意味着正式镜像中没有任何 zip 包残留，体积达到最小
COPY --from=plugin-builder --chown=elasticsearch:elasticsearch /tmp/plugins/ik ${ES_PLUGIN_DIR}/ik
COPY --from=plugin-builder --chown=elasticsearch:elasticsearch /tmp/plugins/pinyin ${ES_PLUGIN_DIR}/pinyin

# 正式镜像不需要执行 rm，因为它本来就没有 zip 包