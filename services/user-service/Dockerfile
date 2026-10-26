# =============================================================================
# user-service 镜像（多阶段构建）
#
# 构建（在仓库根目录执行）：
#     docker build -t user-service:week06 ./services/user-service
#
# 与 monolith 的 Dockerfile 同一套思路：
#   阶段 1 用 Maven 镜像编译打包，阶段 2 只把 app.jar 放进 JRE 镜像。
#   最终镜像不含 Maven、不含源码。
# =============================================================================

FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build

# 先只拷 pom 下载依赖，改业务代码时不必重下依赖。
# cache 挂载让 Maven 仓库在多次构建间共享。
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

# 自证：打印产物大小。只有几十 KB 说明 repackage 没生效，启动会报 no main manifest attribute
RUN ls -la target/*.jar

FROM eclipse-temurin:25-jre

# healthcheck 依赖 curl，temurin 运行镜像默认不带
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 非 root 运行
RUN groupadd --system app && useradd --system --gid app --home /app app

WORKDIR /app

# 精确拷贝固定名产物（pom 里 finalName=app）
COPY --from=builder --chown=app:app /build/target/app.jar /app/app.jar

USER app

EXPOSE 8081

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
