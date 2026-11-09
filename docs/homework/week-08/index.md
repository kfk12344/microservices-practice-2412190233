# 第 08 周 · 大作业实践：服务通信与负载均衡

> 分值 35 分　·　对应提交要求见各个小节
> 相关代码：`services/meal-service`（OpenFeign 客户端）、`compose.yaml`（多实例）、`scripts/e2e-test.sh`（端到端验证）

---

## 1. 选择一个跨服务业务流程

### 设计

选定的流程是 **记录一餐**：`POST /api/meals`。

它必须跨服务完成，因为校验需要的数据不在 meal-service 自己的库里：

| 需要的信息 | 数据归属 | 说明 |
|---|---|---|
| 用户是否存在 | user-service · `health_diet_user.app_user` | 跨库查不到（不同库 / 不同账号 / 无跨库外键） |
| 食物是否存在、每 100 克营养值 | food-service · `health_diet_food.food` | 营养值归食物的所有者维护 |

meal-service 只保存 `user_id` / `food_id` 两个外部引用，以及折算后的营养快照。

调用链路图与接口契约见 **[`docs/service-communication.md`](../../service-communication.md)**，
摘要如下：

```
客户端 → meal-service :8083
            ├── Feign GET user-service  /api/users/{id}   ← 校验用户
            └── Feign GET food-service   /api/foods/{id}  ← 校验食物 + 取营养值
         → 折算 → 写入本地 health_diet_meal
```

方向是单向的（meal → user/food），下游之间不互相调用，没有环路。

### 完成情况

- 正常路径：8 次连续下单全部返回 **201**，记录数从 13 增加到 21。
- 用户/食物不存在：返回 **400**（`USER_NOT_FOUND` / `FOOD_NOT_FOUND`），**不写库**。
- 下游不可用：返回 **503**（`DEPENDENCY_UNAVAILABLE`），**不写库**。

### 关键结论

**"跨服务"这件事的代价是真实的**：一次下单会串行触发 1 + N 次网络调用，
每一次都可能变慢或失败；而且校验与写库之间存在 TOCTOU 窗口（校验通过后被删）。
本项目的处理原则是：**宁可 503 让调用方重试，也不跳过校验先写进去** ——
写脏数据的排查成本远高于一次失败请求。

---

## 2. 使用 OpenFeign

### 依赖（`services/meal-service/pom.xml`）

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

第二个依赖是必须的：Feign 拿到服务名后要靠 LoadBalancer 才能解析成具体实例，
缺了它会直接报 `Load balancer does not contain an instance for the service ...`。

版本由课程 BOM 统一管理（Spring Cloud 2025.1.0 → openfeign 5.0.0），不在依赖里写版本号。

### 客户端

`services/meal-service/src/main/java/com/zjgsu/hdt/meal/client/UserFeignApi.java`

```java
@FeignClient(name = UserFeignApi.SERVICE_NAME)      // 服务名，不是 IP
public interface UserFeignApi {
    String SERVICE_NAME = "user-service";

    @GetMapping("/api/users/{id}")
    UserInfo getUser(@PathVariable("id") Long id);
}
```

`FoodFeignApi` 完全对称（`name = "food-service"`，路径 `/api/foods/{id}`）。

启动类上加 `@EnableFeignClients(basePackages = "com.zjgsu.hdt.meal.client")`。

### 调用逻辑

```java
UserInfo user = api.getUser(userId);        // 由 Feign 代理发出 HTTP
if (user == null) {
    throw DependencyResourceNotFoundException.user(userId);
}
recorder.record(SERVICE_NAME, user.servedBy());   // 记录命中的实例
```

业务层（`MealRecordService`）调用的是门面类 `UserClient` / `FoodClient`，
它们内部持有 Feign 接口。**业务代码不认识 HTTP 异常**，这是门面存在的理由。

### 失败处理

Feign 抛的是 HTTP 层面的异常，门面负责翻译成业务语义：

| 下游表现 | Feign 抛出 | 翻译为 | 对外 |
|---|---|---|---|
| 404 | `FeignException.NotFound` | `DependencyResourceNotFoundException` | 400 |
| 5xx | `FeignException` | `DependencyUnavailableException` | 503 |
| 连不上 / 超时 | `RetryableException`（cause 是 IOException） | `DependencyUnavailableException` | 503 |
| 挑不到实例 | `IllegalStateException`（不是 FeignException，需兜底 catch） | `DependencyUnavailableException` | 503 |

超时值由 `services.connect-timeout-ms` / `services.read-timeout-ms` 控制，
compose 里显式设成 2000 / 3000 毫秒 —— 默认无限等待会让一个变慢的依赖拖垮整个服务。

### 验证方法

```bash
# 正常路径：返回 201，且响应里能看到营养快照
curl -s -X POST http://localhost:8083/api/meals -H 'Content-Type: application/json' \
  -d '{"userId":1,"mealType":"LUNCH","eatenAt":"2026-11-20T12:30:00","note":"demo",
       "items":[{"foodId":1,"portionGrams":200},{"foodId":3,"portionGrams":150}]}'

# 下游 404 → 400
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8083/api/meals \
  -H 'Content-Type: application/json' \
  -d '{"userId":99999,"mealType":"LUNCH","eatenAt":"2026-11-20T12:30:00",
       "items":[{"foodId":1,"portionGrams":100}]}'
```

截图见 `screenshots/08-01-e2e-全流程通过.png`、`screenshots/08-04-OpenFeign客户端.png`。

**完成结果**：meal-service 的测试 10 项全通过（含 Feign 404 / 连不上 / 无实例三种失败的翻译），
e2e 脚本 13 项全通过。

---

## 3. 负载均衡验证

### 怎么做的

`compose.yaml` 里让 **user-service 与 food-service 各跑 2 个实例**：

| 服务 | 实例 | 宿主机端口 | 容器端口 |
|---|---|---|---|
| user-service | `user-service-1`（`hdt-user-service`） | 8081 | 8081 |
| user-service | `user-service-2`（`hdt-user-service-2`） | **不映射** | 8081 |
| food-service | `food-service-1` | 8082 | 8082 |
| food-service | `food-service-2` | **不映射** | 8082 |

2 号实例只写 `expose`，不写 `ports`：两个实例都要监听 8081，
再映射同一个宿主端口必然冲突；容器之间走 `hdt-net` 用服务名访问，不需要宿主端口。
Nacos 里两个实例靠**不同的容器 IP** 区分。

### 怎么证明请求真的分发了（不是只看注册列表）

每个服务启动时确定一个实例标识（`APP_INSTANCE_ID` 环境变量），
`GET /api/users/{id}` 与 `GET /api/foods/{id}` 的响应体里带 `servedBy` 字段。

meal-service 每次调用成功后把这个标识记一笔，`GET /api/meals/instances` 暴露统计结果：

```bash
$ curl -s http://localhost:8083/api/meals/instances
{"food-service":{"food-service-1":8,"food-service-2":8},
 "user-service":{"user-service-1":4,"user-service-2":4}}
```

两个实例的计数都在增长 → 请求确实落到了两台实例上。
统计的是**真实业务调用**（下单时对 user/food 的校验），不是健康探针，
也不是注册列表。

### 运行命令

```powershell
# 仓库根目录
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File scripts\e2e-test.ps1
```

> Linux / macOS / Git Bash 里直接用 `bash scripts/e2e-test.sh`（同一个脚本）。
> Windows 上如果 `bash` 指向 WSL 转发器会报 `execvpe(/bin/bash) failed`，那就用上面的 `.ps1`。

e2e 脚本会自动完成：连续下单、检查分发、停一台实例、停全部实例（期望 503）、恢复。

### 结果

| 场景 | 期望 | 实测 |
|---|---|---|
| 8 次下单 | 全部 201 | 全部 201 |
| 分发统计 | 两个实例都出现 | `user-service-1:4 / -2:4`，`food-service-1:8 / -2:8` |
| 停掉 1 台 user 实例 | 仍可用 | 201（LB 自动避开被摘除的实例） |
| 停掉全部 user 实例 | 503 + 不写库 | 503 `DEPENDENCY_UNAVAILABLE`，记录数不变 |
| 恢复实例 | 恢复 201 | 201 |

截图：`screenshots/08-02-负载均衡分发证据.png`、`screenshots/08-03-依赖不可用503.png`。

---

## 4. 测试

### 4.1 Feign client / service 层测试

`services/meal-service/src/test/java/com/zjgsu/hdt/meal/MealRecordServiceTests.java`
（10 项，全部通过）

第 08 周把跨服务调用换成 Feign 后，测试改为用 `@MockitoBean` 替换
**Feign 接口**（`UserFeignApi` / `FoodFeignApi`），门面类保持真实 ——
这样"HTTP 失败 → 业务异常"的翻译逻辑仍然被真实执行到。

| 用例 | 验证点 |
|---|---|
| 成功路径 | 营养折算 431.50 kcal、明细快照、写库 +1 |
| 负载均衡记录 | 两次调用落在不同实例时，统计里两个实例都有 |
| 用户 404 | `USER_NOT_FOUND` + **不写库** |
| 食物 404 | `FOOD_NOT_FOUND` + **不写库** |
| user 连不上 | 503 语义 + **不写库** |
| food 超时 | 503 语义 + **不写库** |
| 单餐 2320 kcal | `BusinessRuleException` + **不写库** |
| user 无可用实例 | 503 语义 + **不写库** |
| food 无可用实例 | 503 语义 + **不写库** |

```bash
mvn -f services/meal-service/pom.xml test
# Tests run: 10, Failures: 0, Errors: 0
```

### 4.2 端到端验证脚本

`scripts/e2e-test.sh`，**从仓库根目录运行**，13 项检查：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\e2e-test.ps1
```

覆盖：服务就绪、8 次跨服务下单、记录数一致、两个实例都收到请求、
停一台仍可用、停全部返回 503 且不写库、恢复后可用。

截图：`screenshots/08-01-e2e-全流程通过.png`。

> 截图怎么自己截、每张图里必须出现什么，见 **[`screenshots/README.md`](screenshots/README.md)**。

### 4.3 模拟依赖不可用

e2e 脚本的第 3 段就是这件事：

```bash
docker stop hdt-user-service hdt-user-service-2
sleep 45          # 心跳 5s + 判超时 15s + 摘除 30s
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8083/api/meals ...
# → 503
```

脚本同时断言：**调用失败没有被当成业务成功** ——
记录数在失败前后保持不变，说明没有"跳过校验先写进去"。

### 4.4 超时后的结果说明

读超时意味着**请求可能已经被下游处理了，也可能没有** —— 结果未知。
本项目的跨服务调用都是只读校验（GET），所以超时重试没有副作用；
但如果将来引入下游写操作，必须做幂等键，否则重试会产生重复数据。

这条限制是刻意写在这里的：拆成多服务后，"一次调用到底是成功还是失败"
不再像单机事务那样有明确答案，能说清边界比假装它不存在更重要。

---

## 5. 运行环境

```bash
# 1. 准备 .env（含数据库口令、Nacos 口令）
cp .env.example .env

# 2. 起全部服务（含 2 个 user 实例、2 个 food 实例）
docker compose up -d --build

# 3. 等 40~60 秒（Nacos 启动慢），确认容器健康
docker compose ps

# 4. 端到端验证
powershell -ExecutionPolicy Bypass -File scripts\e2e-test.ps1
```

| 服务 | 端口 |
|---|---|
| meal-service | 8083 |
| user-service（1 号实例） | 8081 |
| food-service（1 号实例） | 8082 |
| Nacos 控制台 | 18080（入口 `/next/`） |
