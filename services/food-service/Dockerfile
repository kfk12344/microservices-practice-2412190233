# =============================================================================
# food-service 镜像（多阶段构建）
#
# 构建（在仓库根目录执行）：
#     docker build -t food-service:week06 ./services/food-service
# =============================================================================

FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

# 自证产物大小：只有几十 KB 说明 repackage 没生效
RUN ls -la target/*.jar

FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --home /app app

WORKDIR /app

COPY --from=builder --chown=app:app /build/target/app.jar /app/app.jar

USER app

EXPOSE 8082

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
