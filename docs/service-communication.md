# 跨服务调用链路设计

> 第 08 周 · 服务通信与负载均衡
> 对应代码：`services/meal-service` 的 `client` 包，以及 `user-service` / `food-service` 的控制器

本文说明本项目的**跨服务业务流程选择**、**调用链路**、**失败语义**，以及**负载均衡在哪里生效**。

---

## 1. 选定的业务流程：记录一餐

**入口**：`POST /api/meals`（meal-service 对外唯一的写接口）

这个流程**必须跨服务完成**，因为校验所需的数据不在 meal-service 自己的库里：

| 需要的信息 | 数据归属 | 为什么不能在本地查 |
|---|---|---|
| 用户是否存在 | `health_diet_user.app_user`（user-service） | 跨库查询在物理上做不到（不同库、不同账号、无跨库外键） |
| 每种食物的每 100 克营养值 | `health_diet_food.food`（food-service） | 同上；而且营养值必须由食物的所有者维护 |

meal-service 只保存 `user_id` 与 `food_id` 这两个**外部引用**，以及折算后的**营养快照**。
这既是拆分后的限制，也是它成立的前提：边界清楚了，才谈得上独立部署。

---

## 2. 调用链路

```
                 ┌──────────────────────────────────────────┐
                 │  客户端（curl / Postman / e2e 脚本）        │
                 └───────────────────┬──────────────────────┘
                                     │ POST /api/meals
                                     ▼
                 ┌──────────────────────────────────────────┐
                 │  meal-service :8083                       │
                 │  MealController → MealRecordService.create │
                 └───────┬───────────────────────┬──────────┘
                         │ ① 校验用户             │ ② 校验食物并取营养
                         │ Feign: user-service    │ Feign: food-service
                         ▼                        ▼
        ┌──────────────────────────┐   ┌──────────────────────────┐
        │ user-service :8081        │   │ food-service :8082        │
        │ GET /api/users/{id}       │   │ GET /api/foods/{id}       │
        │ 200 存在 / 404 不存在      │   │ 200 存在 / 404 不存在      │
        └──────────────────────────┘   └──────────────────────────┘
                         │                        │
                         └──────────┬─────────────┘
                                    │ ③ 折算营养（份量 ÷ 100 × 每 100 克值）
                                    ▼
                 ┌──────────────────────────────────────────┐
                 │  meal-service 本地库 health_diet_meal      │
                 │  meal_record + meal_item（含营养快照）      │
                 └──────────────────────────────────────────┘
```

要点：

- **方向是单向的**：meal-service → user-service / food-service。
  user 与 food 之间没有调用关系，也不回查 meal-service，所以不存在环路。
- **每次下单的调用次数**：1 次 user-service + N 次 food-service（N = 明细条数）。
- **两个下游都通过服务名调用**，地址由 Nacos 注册中心 + Spring Cloud LoadBalancer 在运行时决定。

---

## 3. 接口契约

| 调用方 | 被调用方 | 方法 | 路径 | 成功 | 失败 |
|---|---|---|---|---|---|
| meal-service | user-service | GET | `/api/users/{id}` | 200 + 用户 JSON | 404 用户不存在 |
| meal-service | food-service | GET | `/api/foods/{id}` | 200 + 食物 JSON | 404 食物不存在 |

响应里除了业务字段，还有一个 `servedBy` 字段：**实际处理这次请求的实例标识**
（来自各实例的 `APP_INSTANCE_ID` 环境变量）。它不是业务数据，
而是为了让"负载均衡到底有没有把请求分到多个实例"这件事可以被**验证**。

---

## 4. 失败语义（对外可观察的行为）

| 情况 | 内部异常 | 对外状态码 | 错误码 | 是否写库 |
|---|---|---|---|---|
| 一切正常 | — | 201 | — | 写 |
| 用户不存在（对方 404） | `DependencyResourceNotFoundException` | 400 | `USER_NOT_FOUND` | **不写** |
| 食物不存在（对方 404） | `DependencyResourceNotFoundException` | 400 | `FOOD_NOT_FOUND` | **不写** |
| 下游连不上 / 超时 / 5xx | `DependencyUnavailableException` | 503 | `DEPENDENCY_UNAVAILABLE` | **不写** |
| 负载均衡找不到可用实例 | `DependencyUnavailableException` | 503 | `DEPENDENCY_UNAVAILABLE` | **不写** |
| 违反业务规则（如热量超限） | `BusinessRuleException` | 422 | `BUSINESS_RULE_VIOLATION` | **不写** |

**为什么 404 要翻译成 400 而不是原样透传**：
对 meal-service 的调用方来说，"你给我的 userId 不存在"是**它的请求有问题**（400），
而不是 meal-service 自己出错（5xx）。透传 404 会让前端误以为是"饮食记录接口不存在"。

**为什么"下游不可用"要单独成一类**：
这类错误可以重试，而 400 重试没有意义。二者混在一起，调用方就没法写重试策略。

---

## 5. 负载均衡在哪里生效

链路里有两个下游，每个下游都跑 **2 个实例**：

| 服务 | 实例 | 宿主机端口 | 容器内端口 |
|---|---|---|---|
| user-service | `user-service-1` / `user-service-2` | 只有 1 号映射 8081 | 都是 8081 |
| food-service | `food-service-1` / `food-service-2` | 只有 1 号映射 8082 | 都是 8082 |

2 号实例**不发布宿主机端口**（compose 里只写 `expose`）：
两个实例都要监听 8081，再映射同一个宿主端口必然冲突；而容器之间走 `hdt-net` 用服务名访问，本来就不需要宿主端口。

分发过程：

1. Feign 接口里写的是服务名 `user-service`，不是 IP；
2. `spring-cloud-starter-loadbalancer` 从 Nacos 拿到该服务的实例列表（2 个）；
3. 每次调用按轮询挑一个实例，拼出真实 URL；
4. 被调用方在响应里回报自己的实例标识，meal-service 记账；
5. `GET /api/meals/instances` 把记账结果暴露出来。

实测一次（8 次下单，每次 1 次 user 调用 + 2 次 food 调用）：

```json
{"food-service":{"food-service-1":8,"food-service-2":8},
 "user-service":{"user-service-1":4,"user-service-2":4}}
```

两个实例的计数都在增长 —— 这就是"请求真的分发到了两台实例"的证据，
而不是"注册列表里有两个实例"。

---

## 6. 一致性上的已知取舍

- **不追求强一致**：meal-service 校验通过后写自己的库，user-service 若在这期间删了用户，
  本地记录会留下一个悬空引用（TOCTOU 窗口）。作业不要求分布式事务，这里如实记录。
- **写库失败可安全重试**：本地插入是最后一步，失败时下游已经校验过，重试不会产生副作用。
- **超时后的结果未知**：读超时说明"请求可能已经被下游处理了"，
  对只读的校验调用没有副作用；但如果将来引入下游写操作，必须做幂等。
- **营养值存快照**：food-service 改了营养值，历史记录仍然显示当时的值 —— 这是有意的，
  否则历史记录会莫名其妙地变。
