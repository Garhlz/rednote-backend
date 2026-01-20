FROM elasticsearch:8.11.1

# 1. 将本地下载好的 zip 包复制进容器
# 【关键修复】加上 --chown=elasticsearch:elasticsearch
# 这样文件属于 elasticsearch 用户，它才有权限读取安装，并且最后删除
COPY --chown=elasticsearch:elasticsearch es/ik.zip /tmp/ik.zip
COPY --chown=elasticsearch:elasticsearch es/pinyin.zip /tmp/pinyin.zip

# 2. 使用 file:// 协议安装本地文件
RUN ./bin/elasticsearch-plugin install --batch file:///tmp/ik.zip
RUN ./bin/elasticsearch-plugin install --batch file:///tmp/pinyin.zip

# 3. 清理安装包
RUN rm /tmp/ik.zip /tmp/pinyin.zip