# -------------------------------------------------------------
# 只需要运行环境，不再需要 JDK 和 Maven
# -------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 【核心变化】
# 以前是从 builder 阶段复制，现在直接从构建上下文（宿主机目录）复制
# 我们约定把打好的 jar 包传到服务器的 target 目录下
COPY target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]