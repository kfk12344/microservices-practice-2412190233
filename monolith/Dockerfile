# =============================================================================
# 个人健康饮食记录管理系统 —— 应用镜像（多阶段构建）
#
# 构建（在仓库根目录执行）：
#     docker build -t health-diet-tracker:week05 ./monolith
#
# 多阶段的意义：
#   阶段 1 用带 Maven 的 JDK 镜像编译打包，阶段 2 只把产出的 JAR 放进
#   精简的 JRE 镜像。最终镜像里不含 Maven、不含源码、不含本地依赖缓存，
#   体积明显更小，攻击面也更小——攻击者进到容器里也看不到构建工具和源代码。
# =============================================================================

# -----------------------------------------------------------------------------
# 阶段 1：构建（builder）
# 用 JDK 25 版本的 Maven 镜像，与应用 pom.xml 里声明的 java.version=25 保持一致
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build

# 先只复制 pom.xml 并下载依赖。
# 这样只要 pom 没变，下面这一层就能命中缓存；改业务代码时不必重新下载全部依赖。
#
# --mount=type=cache 是 BuildKit 的缓存挂载：
#   把容器内的 /root/.m2（Maven 本地仓库）挂到宿主的构建缓存上，
#   多个构建之间共享。否则每次 pom.xml 一改，这一层就要从空仓库重新下载
#   全部依赖（实测首次约 7 分钟）。
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

# 再复制源码并打包。跳过测试：
#   单测在本地 / CI 的 ./mvnw test 阶段已经跑过，镜像构建只负责产出可运行 JAR。
# 这里同样挂载 Maven 仓库缓存，打包阶段不必再下载依赖。
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

# 自证一步：打印 target/ 下的产物。
#   app.jar          = spring-boot-maven-plugin repackage 生成的 fat JAR（含 Main-Class 与全部依赖）
#   app.jar.original = 未重打包的瘦 JAR
# 如果 app.jar 只有几十 KB，说明 repackage 没生效，启动时必然报
# `no main manifest attribute`。放在构建日志里，比在容器里看重启循环好排查得多。
RUN ls -la target/*.jar

# -----------------------------------------------------------------------------
# 阶段 2：运行（runtime）
# 只保留 JRE，不包含编译工具链
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre

# 安装 curl：compose.yaml 里 app 的 healthcheck 要访问 /actuator/health，
# 而 temurin 运行镜像默认不带 curl；不用它的话容器会一直处于 unhealthy。
# --no-install-recommends + 清空 apt 索引，避免把包管理器缓存留在镜像里。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 用非 root 用户运行：容器逃逸的代价因此更高，是容器安全的基本要求
RUN groupadd --system app && useradd --system --gid app --home /app app

WORKDIR /app

# 精确拷贝固定名字的产物：
#   不用 target/*.jar 通配符，因为它可能匹配到 target/ 下的其它 jar
#   （例如测试用的 mockito agent），那样就会把不含 Main-Class 的 jar 当成应用 jar。
#   产物名由 pom.xml 的 <finalName>app</finalName> 固定。
#
# --chown=app:app 在这里直接设置属主，而不是后面再 RUN chown：
#   chown 会修改文件元数据，Docker 必须把整个文件复制到新的一层，
#   62MB 的 jar 因此会在镜像里存两份（实测浪费 62.2MB）。
COPY --from=builder --chown=app:app /build/target/app.jar /app/app.jar

USER app

EXPOSE 8080

# 容器内的时区与语言，保证中文日志与日期格式正常
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8

# 用 exec 形式启动，保证 java 是 PID 1，能正确接收 docker stop 的 SIGTERM 做优雅停机
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
