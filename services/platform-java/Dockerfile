# 阶段 1: 构建阶段 (使用 JDK)
FROM maven:3.8.5-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# 利用 Docker 层缓存：先只复制 pom.xml
COPY pom.xml .
# 预下载依赖 (这一步如果不改变 pom.xml 就会被缓存)
RUN mvn dependency:go-offline

# 复制源码并打包 (跳过单元测试以加快速度)
COPY src ./src
RUN mvn clean package -DskipTests

# 阶段 2: 运行阶段 (使用精简 JRE)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 安装时区数据和基础工具 (Alpine 默认可能缺少)
RUN apk add --no-cache tzdata
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 从构建阶段复制生成的 JAR
COPY --from=builder /build/target/rednote_backend.jar app.jar

# JVM 参数优化：感知容器内存限制
# -XX:+UseContainerSupport 让 Java 自动识别 Docker 的内存限制
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XshowSettings:vm"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]