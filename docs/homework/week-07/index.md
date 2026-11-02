# Week 07 作业记录

> 个人健康饮食记录管理系统（Health & Diet Tracker）· 学号 2412190233 · 姓名 郑俊豪
> 本周任务：在第 06 周已经拆分的三个微服务基础上接入 Nacos，把跨服务调用从**固定地址**
> 改为**服务名发现实例**，并验证正常调用、服务下线与恢复后的行为。

---

## 本周目标

| 序号 | 任务 | 交付物 |
| :-- | :--- | :--- |
| 1 | 部署 Nacos 并注册服务 | `compose.yaml` 增加 Nacos，三个服务注册成功 |
| 2 | 配置隔离并完成服务发现调用 | 环境变量读地址与凭据；`DiscoveryClient` 取实例；删除固定地址 |
| 3 | 验证健康状态、下线与恢复 | 正常调用、停止服务、空实例错误、恢复后重试 |
| 4 | 验证脚本与说明 | `scripts/smoke-test.sh` 含 Nacos 检查；本文 + `screenshots/` |

---

## 一、部署 Nacos 并注册服务

### 1.1 版本选型

| 组件 | 版本 | 说明 |
| :--- | :--- | :--- |
| Nacos Server | `nacos/nacos-server:v3.2.4` | 作业指定版本 |
| Spring Cloud Alibaba | `2025.1.0.0` | 对应 Spring Cloud 2025.1.0 / **Spring Boot 4.0.x** |
| Nacos Client | 由 BOM 决定（3.1.x） | **不单独覆盖版本**，避免与其他组件冲突 |

版本对应关系来自 [Spring Cloud Alibaba 官方版本说明](https://sca.aliyun.com/en/docs/2025.x/overview/version-explain/)：
本项目用 Spring Boot 4.0.8，落在 `2025.1.0.0` 支持的 4.0.x 区间。

依赖通过**统一 BOM** 管理，三个服务的 `pom.xml` 都只声明 starter、不写版本：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>2025.1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
</dependencies>
```

> 本周**只引入 Discovery Starter，不引入 Config Starter** —— 配置中心不是本周的内容。

### 1.2 端口布局

Nacos 3.0 起控制台与主端口分离（见 [官方部署手册](https://nacos.io/en/docs/v3.1/manual/admin/deployment/deployment-overview/)），
所以端口要区分清楚：

| 端口 | 作用 | 本项目映射 |
| :--- | :--- | :--- |
| 8080 | **控制台**（浏览器访问的页面） | `18080:8080`（避开宿主机 8080） |
| 8848 | HTTP API（Admin API、登录拿 accessToken） | `8848:8848` |
| 9848 | **客户端 gRPC**（注册、订阅、心跳都走这里） | `9848:9848` |

9849（服务端之间同步）与 7848（Jraft）只在集群模式需要，单机不暴露。

> 注意：控制台**不是**旧版 2.x 的 `http://localhost:8848/nacos`，而是 `http://localhost:18080/`。

### 1.3 鉴权与数据卷

```yaml
nacos:
  image: nacos/nacos-server:v3.2.4
  environment:
    MODE: standalone
    NACOS_AUTH_TOKEN: ${NACOS_AUTH_TOKEN}            # JWT 密钥：>32 字符再 Base64
    NACOS_AUTH_IDENTITY_KEY: ${NACOS_AUTH_IDENTITY_KEY}
    NACOS_AUTH_IDENTITY_VALUE: ${NACOS_AUTH_IDENTITY_VALUE}
    NACOS_AUTH_ENABLE: "true"
  volumes:
    - nacos-data:/home/nacos/data     # 注册与配置数据，容器重建不丢
    - nacos-logs:/home/nacos/logs
```

| 配置 | 说明 |
| :--- | :--- |
| `NACOS_AUTH_TOKEN` | 先用一个**长度大于 32 字符**的字符串，再做 Base64；三个服务与 Nacos 必须一致 |
| `NACOS_AUTH_IDENTITY_KEY/VALUE` | 服务端之间 Inner API 的身份标识，鉴权开启时必填 |
| `nacos-data` | 命名数据卷，`docker compose down` 不删，`down -v` 才删 |

**真实密钥只放在本地 `.env`**，仓库里只有 `.env.example` 的占位模板；`.env` 已被 `.gitignore` 排除。
本文与所有截图都不出现真实密钥。

### 1.4 启动顺序

作业要求"先独立启动 Nacos 并初始化管理员密码，再启动业务服务"：

```powershell
# ① 先只启动 Nacos
docker compose up -d nacos

# ② 浏览器打开控制台，首次会要求初始化管理员 nacos 的密码
#    http://localhost:18080/
#    把设置的密码填进 .env 的 NACOS_PASSWORD

# ③ 确认 Nacos 起来了
docker compose logs --tail=30 nacos     # 看到 "Nacos started successfully in stand alone mode"

# ④ 再启动业务服务（会读 .env 里的 NACOS_PASSWORD 去注册）
docker compose up -d --build user-service food-service meal-service

# ⑤ 查看服务列表
curl.exe "http://localhost:8848/nacos/v3/client/ns/service/list?pageNo=1&pageSize=20&groupName=PERSONAL_GROUP"
```

也可以直接看控制台：`http://localhost:18080/` → 服务管理 → 服务列表，
分组选 `PERSONAL_GROUP`，应能看到 `user-service`、`food-service`、`meal-service` 三个服务，
点进去能看到实例的 **IP 与端口**（宿主机映射端口 8081/8082/8083）。

### 1.5 运行结果

Nacos 启动日志确认单机模式 + 内置存储：

```
2026-11-02 14:31:20,828 INFO Nacos started successfully in stand alone mode with embedded storage in 11368 ms
2026-11-02 14:31:24,896 INFO Nacos Console started successfully in 1075 ms
```

**控制台入口是 `/next/`**（Nacos 3.x 的新控制台路径，访问 `/` 会 302 跳转过去；
直接请求 `/index.html` 会返回 500）：

```powershell
curl.exe -sL -o NUL -w "%{http_code} %{url_effective}" http://localhost:18080/
# 200 http://localhost:18080/next/
```

三个服务的注册结果（管理 API + accessToken）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalCount": 3,
    "pageItems": [
      {"name": "food-service", "groupName": "PERSONAL_GROUP", "clusterCount": 1, "ipCount": 1, "healthyInstanceCount": 1},
      {"name": "user-service", "groupName": "PERSONAL_GROUP", "clusterCount": 1, "ipCount": 1, "healthyInstanceCount": 1},
      {"name": "meal-service", "groupName": "PERSONAL_GROUP", "clusterCount": 1, "ipCount": 1, "healthyInstanceCount": 1}
    ]
  }
}
```

实例详情（`user-service`）：

```json
{
  "instanceId": "172.18.0.4#8081##PERSONAL_GROUP@@user-service",
  "ip": "172.18.0.4",
  "port": 8081,
  "weight": 1.0,
  "healthy": true,
  "enabled": true,
  "ephemeral": true,
  "clusterName": "DEFAULT",
  "serviceName": "PERSONAL_GROUP@@user-service",
  "metadata": {"preserved.register.source": "SPRING_CLOUD"},
  "instanceHeartBeatInterval": 5000,
  "instanceHeartBeatTimeOut": 15000,
  "ipDeleteTimeout": 30000
}
```

注意 `instanceHeartBeatInterval: 5000`、`TimeOut: 15000`、`ipDeleteTimeout: 30000` 这三个值 ——
它们决定了"停服务后多久实例才会从注册表消失"，是第 3 小节验证时等待时间的依据。

---

## 二、配置隔离并完成服务发现调用

### 2.1 环境变量读取地址与凭据

三个服务的 `application.yml` 完全一致（只有 `spring.application.name` 不同）：

```yaml
spring:
  application:
    name: user-service
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:}
        namespace: ${NACOS_NAMESPACE:}
        group: ${NACOS_GROUP:PERSONAL_GROUP}
        register-enabled: ${NACOS_REGISTER_ENABLED:true}
        enabled: ${NACOS_DISCOVERY_ENABLED:true}
```

compose 里覆盖成服务名 `nacos:8848`（容器里的 localhost 是它自己），
凭据全部来自 `.env`，仓库里没有任何真实口令。

### 2.2 分组与命名空间

| 项目 | 取值 | 说明 |
| :--- | :--- | :--- |
| 分组 | `PERSONAL_GROUP` | 本项目专用，不与课堂示例混用；参与调用的三个服务必须一致 |
| 命名空间 | 空（= 默认 `public`） | 自定义时填**命名空间 ID**，不是名称 |

分组不一致是很容易踩的坑：服务都注册成功了，但 `getInstances()` 查回来是空列表，
因为默认只查同一个 group。

### 2.3 用 `DiscoveryClient` 真的按服务名取实例

业务代码不直接依赖 `DiscoveryClient`，而是抽了一层 `ServiceInstanceResolver`
（原因见 `docs/microservices-design.md` 5.3 节）：

```java
@Component
public class DiscoveryServiceInstanceResolver implements ServiceInstanceResolver {

    private final DiscoveryClient discoveryClient;

    @Override
    public String resolveBaseUrl(String serviceName) {
        // 每次调用都查一次，不缓存地址
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);

        if (instances == null || instances.isEmpty()) {
            throw new DependencyUnavailableException(serviceName,
                    "Nacos 中查不到可用实例（服务可能已下线或从未注册）");
        }
        ServiceInstance instance = instances.get(0);
        return instance.getUri().toString();     // http://172.18.0.5:8081
    }
}
```

客户端拼业务路径：

```java
public UserInfo requireUser(Long userId) {
    String baseUrl = instanceResolver.resolveBaseUrl(SERVICE_NAME);   // ← 服务发现
    String url = baseUrl + "/api/users/" + userId;                    // ← 原业务路径不变
    ...
}
```

**保留的东西**：业务路径（`/api/users/{id}`）、`RestTemplate`、连接超时与读取超时、
404 → 400 / 连不上 → 503 的翻译逻辑。
**删掉的东西**：`USER_SERVICE_URL`、`FOOD_SERVICE_URL` 两个固定地址配置项。

### 2.4 空实例的处理

实例列表为空时返回 **503 `DEPENDENCY_UNAVAILABLE`**，而不是继续往下走：

```json
{
  "status": 503,
  "code": "DEPENDENCY_UNAVAILABLE",
  "message": "依赖服务不可用：user-service（Nacos 中查不到可用实例（服务可能已下线或从未注册））"
}
```

依赖资源不存在（用户/食物 ID 不存在）仍然保留 **400** 与原有业务响应，
不会因为换成了服务发现就改语义。

### 2.5 空实例的测试（不连真实 Nacos）

测试剖面下关闭 Nacos 注册，并由 `TestDiscoveryConfig` 提供一个返回固定实例的桩：

```yaml
# src/test/resources/application-test.yml
spring:
  cloud:
    nacos:
      discovery:
        enabled: false
        register-enabled: false
meal:
  discovery:
    enabled: false      # 关掉生产用的 DiscoveryServiceInstanceResolver
```

两个专门的空实例用例：

| 用例 | 构造方式 | 断言 |
| :--- | :--- | :--- |
| 查不到 user-service 实例 | 寻址桩解析即抛 `DependencyUnavailableException` | 503 语义；**本地记录数不变** |
| user 有实例、food 无实例 | 桩对 user 返回地址、对 food 抛异常 | 503 且消息含 `food-service`；**本地记录数不变** |

### 2.6 运行结果

`meal-service` 通过服务发现拿到的实例地址就是 Nacos 里注册的那个（`172.18.0.4:8081`），
拼上原业务路径 `/api/users/1` 发出请求：

```powershell
curl.exe -X POST http://localhost:8083/api/meals -H "Content-Type: application/json" -d '{"userId":1,"mealType":"LUNCH","eatenAt":"2026-11-02T12:30:00","note":"服务发现验证","items":[{"foodId":1,"portionGrams":200},{"foodId":3,"portionGrams":150}]}'
```

```json
{
  "id": 8,
  "userId": 1,
  "mealTypeLabel": "午餐",
  "items": [
    {"foodId": 1, "foodName": "米饭", "portionGrams": 200, "calories": 232.00},
    {"foodId": 3, "foodName": "鸡胸肉", "portionGrams": 150, "calories": 199.50}
  ],
  "totalCalories": 431.50,
  "totalGrams": 350
}
```

HTTP **201**。这条记录是 `meal-service` 先查 Nacos 拿到 `user-service` 与 `food-service`
的实例地址、再各自调用成功之后才写入的 —— 全程没有用到任何固定地址。

---

## 三、验证健康状态、下线与恢复

### 3.1 全部依赖正常时调用成功

```powershell
curl.exe -i -X POST http://localhost:8083/api/meals -H "Content-Type: application/json" -d '{\"userId\":1,\"mealType\":\"LUNCH\",\"eatenAt\":\"2026-11-02T12:30:00\",\"note\":\"服务发现验证\",\"items\":[{\"foodId\":1,\"portionGrams\":200},{\"foodId\":3,\"portionGrams\":150}]}'
```

期望 **201**，`totalCalories = 431.50`；数据落进 `health_diet_meal` 库。

### 3.2 停掉一个被调用的服务

```powershell
# 记下停之前的记录数
curl.exe "http://localhost:8083/api/meals?userId=1"

# 停掉 user-service
docker compose stop user-service

# 观察 Nacos 实例列表变化（心跳超时后实例会被摘除）
curl.exe "http://localhost:8848/nacos/v3/client/ns/instance/list?serviceName=user-service&groupName=PERSONAL_GROUP"

# 用新的业务参数再发起一次请求
curl.exe -i -X POST http://localhost:8083/api/meals -H "Content-Type: application/json" -d '{\"userId\":1,\"mealType\":\"BREAKFAST\",\"eatenAt\":\"2026-11-03T08:00:00\",\"items\":[{\"foodId\":1,\"portionGrams\":100}]}'

# 确认没有新增本地记录
curl.exe "http://localhost:8083/api/meals?userId=1"
```

期望：Nacos 实例列表里 `user-service` 的实例消失（或健康状态变 false），
请求返回 **503 `DEPENDENCY_UNAVAILABLE`**，记录数**与停之前一致**。

### 3.3 恢复服务后重新验证

```powershell
docker compose start user-service

# 等它重新注册到 Nacos 并可访问
curl.exe http://localhost:8081/actuator/health
curl.exe "http://localhost:8848/nacos/v3/client/ns/instance/list?serviceName=user-service&groupName=PERSONAL_GROUP"

# 再次发起正常请求
curl.exe -i -X POST http://localhost:8083/api/meals -H "Content-Type: application/json" -d '{\"userId\":1,\"mealType\":\"DINNER\",\"eatenAt\":\"2026-11-04T19:00:00\",\"items\":[{\"foodId\":1,\"portionGrams\":200}]}'
```

期望恢复 **201** —— 说明 `meal-service` 不需要重启就能拿到新实例（因为地址是每次现查的）。

### 3.4 三个概念的区别（作业要求说明）

这是本周需要写清楚的一点，三者**不是同一件事**：

| 概念 | 由谁维护 | 含义 | 不会自动发生的事 |
| :--- | :--- | :--- | :--- |
| **SDK 连接存活** | Nacos 客户端与服务的 gRPC 长连接（9848） | 客户端能不能和 Nacos 通信；断了会重连，并在重连期间用本地缓存 | 连接断了不代表实例已从注册表移除 |
| **注册实例状态** | Nacos 服务端，靠客户端**心跳/续约**维护 | 该实例是否"还在注册表里、是否健康"；心跳超时（默认 15s）后标记不健康，再超时（默认 30s）被摘除 | **不会**因为应用业务出问题就自动摘除 —— 除非心跳真的停了 |
| **Actuator 应用健康** | 应用自己（Spring Boot Actuator） | 应用自身的运行状态，含数据库连通性等 | **不会**自动让 Nacos 摘除实例 |

关键结论：

- **不能假设 "Actuator 返回 DOWN 就会让 Nacos 摘除实例"**。
  Actuator 的健康状态只是应用的自我报告，Nacos 默认只看心跳；
  若希望"应用不自愈时也被摘除"，需要额外做健康检查集成（本周不要求）。
- 反过来，**进程被杀掉**时心跳停止，Nacos 才会在超时后摘除实例 ——
  这也是为什么"停服务后要等一会儿"再查实例列表，而不是立刻就能看到变化。
- 本项目的 `meal-service` 依赖的是**注册表里的实例列表**，
  所以它感知的是"注册实例状态"，而不是对方的 Actuator 健康。

### 3.5 服务发现不会消除的事

作业里提到要说明这一点，如实记录：

- 服务发现解决的是**地址从哪来**，不解决**事务一致性**；
- 第 06 周记录过的"远端校验成功、本地写入失败"窗口依然存在，
  第 06 周文档 4.3 节的三个窗口（本地 INSERT 失败、TOCTOU、营养快照）**结论不变**；
- 本周不实现自动补偿、分布式事务或消息机制（作业明确不要求）。

### 3.6 运行结果

**① 全部依赖正常**

```json
{"id": 8, "totalCalories": 431.50, "totalGrams": 350}     ← HTTP 201
```

记录数：8。

**② 停掉 user-service 后**

| 时刻 | 观察 |
| :--- | :--- |
| 停之前 | `userId=1` 的记录数 = **8** |
| 停后 45 秒 | Nacos 实例列表：`{"code":0,"message":"success","data":[]}` —— **空的** |
| 发起请求 | **HTTP 503**，见下 |
| 请求之后 | 记录数仍是 **8** —— **没有绕过校验写入本地** |

```json
{
  "status": 503,
  "error": "Service Unavailable",
  "code": "DEPENDENCY_UNAVAILABLE",
  "message": "依赖服务不可用：user-service（Nacos 中查不到可用实例（服务可能已下线或从未注册））；本次请求未写入任何数据，可稍后重试",
  "path": "/api/meals"
}
```

状态码与错误码都对得上"实例列表为空"这一场景：**503 + DEPENDENCY_UNAVAILABLE**，
而不是笼统的 500，也没有继续往下写。

**③ 恢复 user-service 后**

| 时刻 | 观察 |
| :--- | :--- |
| 启动后 8 秒 | `/actuator/health` 返回 UP |
| 再 4 秒 | 重新出现在 Nacos 实例列表里，IP `172.18.0.4` |
| 再次请求 | **HTTP 201**，`id=9`，`totalCalories=232.00` |

关键证据：`meal-service` **没有重启**

```
docker inspect hdt-meal-service
  StartedAt: 2026-11-02T06:36:12Z
  RestartCount: 0
```

`StartedAt` 早于恢复请求的时间、`RestartCount` 为 0 ——
说明 `meal-service` 是靠**每次调用现查实例**拿到新地址的，
这也验证了 5.4 节"不缓存地址"这个设计的意义：如果启动时缓存了地址，这里必然还得重启它。

---

## 四、验证脚本、测试与说明

### 4.1 冒烟脚本

`scripts/smoke-test.sh` 从仓库根目录运行：

```bash
bash scripts/smoke-test.sh
```

第 07 周新增与调整：

| 检查项 | 内容 |
| :--- | :--- |
| Nacos 控制台 | 访问 `http://localhost:18080/`（会自动 302 到 3.x 的 `/next/`；**不是** `8848/nacos`，也不是 `/index.html`——后者返回 500） |
| 三个服务健康 | `user-service` / `food-service` / `meal-service` 的 `/actuator/health` 均为 UP |
| 跨服务流程 | 通过服务发现记录一餐 → 201 且 `totalCalories = 431.50` |
| 资源不存在 | 用户 / 食物不存在 → 400，且本地记录数不变 |
| 业务规则 | 单餐超 1500 kcal → 422，且本地记录数不变 |
| 服务下线 | 停掉 `user-service` → 503 `DEPENDENCY_UNAVAILABLE`，且本地记录数不变，随后自动恢复 |

**任何一项失败都会让脚本返回非零退出码**，不会忽略错误后仍报成功。

### 4.2 测试边界

| 项 | 做法 |
| :--- | :--- |
| 不依赖真实 Nacos | 测试剖面 `spring.cloud.nacos.discovery.enabled=false`，寻址用测试桩 |
| 不依赖真实 MySQL | 测试用 H2 内存库（`MODE=MySQL`） |
| 不发起真实 HTTP | `MockRestServiceServer` 绑在真实 `RestTemplate` 上拦截 |
| 空实例场景 | 就地构造"解析即抛 503"的寻址桩，断言不写本地库 |

各服务测试数与运行方式：

| 服务 | 测试数 | 命令 |
| :--- | ---: | :--- |
| user-service | 5 | `mvn test`（在 `services/user-service` 下） |
| food-service | 5 | `mvn test`（在 `services/food-service` 下） |
| meal-service | 9 | `mvn test`（在 `services/meal-service` 下） |
| 合计 | **19** | |

### 4.3 运行结果

**冒烟脚本**（容器网络内运行，`NACOS_CONSOLE=http://nacos:8080`）：

```
==============================================================
  开始前检查 1/2：Nacos 控制台可访问
==============================================================
  [PASS] Nacos 控制台可访问（http://nacos:8080/ → /next/）

==============================================================
  开始前检查 2/2：三个服务的健康接口
==============================================================
  [PASS] user-service 健康检查 UP（http://user-service:8081）
  [PASS] food-service 健康检查 UP（http://food-service:8082）
  [PASS] meal-service 健康检查 UP（http://meal-service:8083）

==============================================================
  用例 1：跨服务业务流程 —— 记录一餐（会调用 user + food 两个服务）
==============================================================
  记录前 userId=1 的记录数：9
  [PASS] POST /api/meals 创建成功（HTTP 201）
  [PASS] 响应含合计热量 431.50（米饭 232 + 鸡胸肉 199.5）
  [PASS] 明细里带食物名称快照
  [PASS] 本地库记录数增加（9 → 10）

==============================================================
  用例 2：依赖资源不存在 —— 用户不存在（应 400，且不写本地库）
==============================================================
  [PASS] userId=9999 被拒绝（HTTP 400）
  [PASS] 错误码为 USER_NOT_FOUND
  [PASS] 未写本地库（userId=9999 的记录数为 0）

==============================================================
  用例 3：依赖资源不存在 —— 食物不存在（应 400，且不写本地库）
==============================================================
  [PASS] foodId=9999 被拒绝（HTTP 400）
  [PASS] 错误码为 FOOD_NOT_FOUND
  [PASS] 未写本地库（记录数保持 10）

==============================================================
  用例 4：业务规则 —— 单餐热量超过 1500 kcal（应 422，且不写本地库）
==============================================================
  [PASS] 2000g 米饭（2320 kcal）被拒绝（HTTP 422）
  [PASS] 错误码为 BUSINESS_RULE_VIOLATION
  [PASS] 未写本地库（记录数保持 10）

==============================================================
  用例 5：依赖服务不可用 —— 停掉 user-service（应 503，且不写本地库）
==============================================================
  [SKIP] 当前环境没有 docker 命令（例如在容器内运行脚本）

==============================================================
  结果汇总
==============================================================
  通过：17
  失败：0

  全部通过 ✅
```

> 用例 5 在容器内运行时跳过（没有 docker CLI 去停服务），
> 它已经在宿主机上单独验证过，结果见 3.6 节。

**三个服务的单元测试**：

| 服务 | 结果 |
| :--- | :--- |
| user-service | `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS` |
| food-service | `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS` |
| meal-service | `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS` |
| 合计 | **19 个测试全部通过** |

整套测试在没有 Nacos、没有 MySQL 的机器上也能跑（H2 + 寻址桩 + MockRestServiceServer）。

---

## 五、本周遇到的问题与解决办法

### 5.1 Spring Boot 4 需要 Spring Cloud Alibaba 2025.1.0.0

- **问题**：本项目是 Spring Boot **4.0.8**，而多数教程里的 Spring Cloud Alibaba 是 2023.x / 2022.x，
  对应 Boot 3.x，直接套用会因 `spring-cloud-commons` 版本不匹配而启动失败。
- **解决**：按官方[版本矩阵](https://sca.aliyun.com/en/docs/2025.x/overview/version-explain/)选
  `2025.1.0.0`（→ Spring Cloud 2025.1.0 → Boot 4.0.x），并用 BOM 统一管理，不手写 Nacos Client 版本。

### 5.2 compose 里 `${VAR:default}` 是非法语法

- **现象**：`docker compose config` 报
  `invalid interpolation format for services.food-service.environment.NACOS_GROUP`。
- **原因**：`${NACOS_GROUP:PERSONAL_GROUP}` 是 **Spring** 的默认值语法；
  **Docker Compose 要求写成 `${NACOS_GROUP:-PERSONAL_GROUP}`**（多个减号）。
  两套语法长得很像，但写错了只在 `compose config` 时才暴露。
- **解决**：compose 里统一改成 `${VAR:-default}`，`application.yml` 里保持 `${VAR:default}`。

### 5.3 直接注入 `DiscoveryClient` 会"多于一个 primary bean"

- **现象**：测试启动失败
  `NoUniqueBeanDefinitionException: more than one 'primary' bean found among candidates:
  [stubDiscoveryClient, compositeDiscoveryClient, simpleDiscoveryClient]`。
- **原因**：Spring Cloud 会同时注册 `compositeDiscoveryClient` 与 `simpleDiscoveryClient`，
  两个都标了 primary；再叠加测试桩就彻底无法确定用哪个。
- **解决**：不直接依赖 `DiscoveryClient`，而是抽一层 `ServiceInstanceResolver`；
  生产实现 `DiscoveryServiceInstanceResolver` 在测试剖面下用 `@ConditionalOnProperty` 关掉，
  测试只提供一个 `ServiceInstanceResolver` 的桩，歧义消失。

### 5.4 停服务后实例不会立刻从 Nacos 消失

- **现象**：`docker compose stop user-service` 后立刻查实例列表，仍能看到该实例，
  此时请求还是连得上旧地址（直到连接失败）。
- **原因**：Nacos 靠**心跳超时**判定实例失效，不是进程一停就立刻摘除。
- **解决**：验证"下线后无实例"时要**等心跳超时**（几十秒）再查；
  这段时间内的请求会因为连不上被翻译成 503（`Connect timed out`），同样是"不写本地库"的正确行为。

### 5.5 测试里关掉 Nacos 还不够

- **问题**：只写 `spring.cloud.nacos.discovery.enabled=false`，`@SpringBootTest`
  仍可能因为缺少 `DiscoveryClient` 实现或存在多个实现而失败。
- **解决**：两条一起做 —— 关闭 Nacos 自动装配 + 用
  `@ConditionalOnProperty(name="meal.discovery.enabled")` 关掉生产寻址实现，
  测试剖面下只剩一个寻址桩。

---

## 六、完成内容小结

| 作业要求 | 完成情况 |
| :--- | :--- |
| `compose.yaml` 加入 `nacos/nacos-server:v3.2.4` | 已加入，`MODE=standalone` + 鉴权 + 数据卷 |
| 控制台映射 18080:8080，区分 8848 与 9848 | 三个端口按要求映射并在文档中说明 |
| 配置 Token、Server identity、鉴权与数据卷 | 全部走 `.env`，仓库只留占位模板 |
| 先独立启动 Nacos 并初始化管理员密码 | 文档 1.4 节给出启动顺序与操作步骤 |
| 统一 BOM 管理 Discovery Starter，不覆盖 Client 版本 | 三个 pom 均 import 两个 BOM，starter 不写版本 |
| 本周不引入 Config Starter | 只引 `spring-cloud-starter-alibaba-nacos-discovery` |
| 各服务用自己的 `spring.application.name` | `user-service` / `food-service` / `meal-service` |
| 控制台能看到 ≥3 个服务及实例地址 | 见 1.5 节与截图 |
| 环境变量读 Nacos 地址与凭据 | `${NACOS_SERVER_ADDR}` 等，compose 从 `.env` 注入 |
| 同一命名空间与分组 `PERSONAL_GROUP` | 三个服务一致 |
| `DiscoveryClient.getInstances()` 取实例并拼业务路径 | `ServiceInstanceResolver` + `DiscoveryServiceInstanceResolver` |
| 每次操作重新取实例，不缓存 | 每次调用前现查 |
| 保留 HTTP 连接与读取超时 | `HttpClientConfig` 仍设置 2s / 3s |
| 列表为空返回 503，资源不存在保留 400 | 见 2.4 节，两个分支各有测试 |
| 删除不再使用的固定地址配置 | `USER_SERVICE_URL` / `FOOD_SERVICE_URL` 已从 yml 与 compose 移除 |
| 为空实例加测试，不连真实 Nacos | 2 个专门用例 + `TestDiscoveryConfig` 桩 |
| 记录正常、下线、恢复与健康状态区别 | 见第三节 |
| `smoke-test.sh` 检查控制台与各服务，失败非零退出 | 已更新 |
| README 写明运行脚本、查看服务列表、执行测试的方法 | 见 README「微服务版本」一节 |

---

## 七、后续计划

| 周次 | 计划 | 落点 |
| :--- | :--- | :--- |
| 后续 | 网关 | 用 Spring Cloud Gateway 收敛 8081–8083，统一入口与鉴权 |
| 后续 | 负载均衡 | 目前只取第一个实例，可换成 `SpringClientFactory` / LoadBalancer 做轮询 |
| 后续 | 健康检查联动 | 让 Actuator 不健康时也摘除实例（注册健康检查器） |
| 后续 | 配置中心 | 引入 Config Starter，把散落的配置收敛到 Nacos |
| 后续 | 可观测性 | Micrometer 指标 + 跨服务链路追踪 |
