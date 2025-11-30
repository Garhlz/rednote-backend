# -------------------------------------------------------------
# Stage 1: Build the application
# 使用官方 Maven/JDK 镜像进行构建
# -------------------------------------------------------------
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

# 1. 先拷贝 Maven 包装器和配置，利用 Docker 缓存机制
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# 2. 赋予 mvnw 执行权限 (以防万一)
RUN chmod +x mvnw

# 3. 预下载依赖 (这一步能大大加快后续构建速度)
# 如果网络不好，这一步可能会卡住，可以使用阿里云镜像源配置 settings.xml
RUN ./mvnw dependency:go-offline

# 4. 拷贝源代码
COPY src ./src

# 5. 打包 (跳过测试，因为测试需要连接数据库，构建环境可能连不上)
RUN ./mvnw package -DskipTests

# -------------------------------------------------------------
# Stage 2: Run the application
# 使用精简版 JRE 运行，减小镜像体积
# -------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# [关键修复] 设置时区为上海，解决 MongoDB 和日志时间差 8 小时的问题
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 从构建阶段拷贝 jar 包
# 注意：这里假设打包后的名字是 platform-0.0.1-SNAPSHOT.jar
COPY --from=builder /app/target/platform-0.0.1-SNAPSHOT.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]