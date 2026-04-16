# syntax=docker/dockerfile:1.5

FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build/services/platform-java

# 先复制依赖描述和 proto，尽量利用缓存层
COPY services/platform-java/pom.xml ./
COPY proto /build/proto

RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml dependency:go-offline

# 再复制源码进行打包
COPY services/platform-java/src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN apk add --no-cache tzdata ca-certificates

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

COPY --from=builder /build/services/platform-java/target/rednote_backend.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
