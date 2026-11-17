# =============================================================================
# gateway-service 镜像（多阶段构建）
#
# 构建（在仓库根目录执行）：
#     docker build -t gateway-service:week09 ./services/gateway-service
#
# 与其它服务完全一致：阶段 1 编译，阶段 2 只保留 JRE + app.jar。
# =============================================================================

FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

RUN ls -la target/*.jar

FROM eclipse-temurin:25-jre

# healthcheck 依赖 curl
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --home /app app

WORKDIR /app

COPY --from=builder --chown=app:app /build/target/app.jar /app/app.jar

USER app

# 统一对外入口
EXPOSE 8080

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
