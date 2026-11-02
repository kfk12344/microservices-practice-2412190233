# 截图目录说明（Week 07）

第 07 周大作业有四个小题需要截图，每题可上传 1–5 张。
`index.md` 中已按这些文件名引用图片 —— **文件名必须完全一致**，否则 GitHub 上是破图。

> 所有截图都**不能出现真实密钥**：`NACOS_AUTH_TOKEN`、`NACOS_PASSWORD`、
> 数据库口令一律打码或避开。`.env` 不要整屏截图。

---

## 小题 1「Nacos 部署与注册」（3 张）

| 文件名 | 拍什么 | 要能看出的点 |
| :--- | :--- | :--- |
| `01-nacos-console-services.png` | 控制台「服务管理 → 服务列表」，分组选 `PERSONAL_GROUP` | **三个服务**：`user-service`、`food-service`、`meal-service`，各自实例数为 1 |
| `02-nacos-instances.png` | 点进 `user-service` 看实例详情 | 实例的 **IP 与端口**（8081）、健康状态、分组 |
| `03-compose-ps-nacos.png` | `docker compose ps` | `hdt-nacos` 与 mysql、三个微服务一起在运行 |

**控制台地址**：`http://localhost:18080/`（会自动跳到 3.x 的 `/next/`；不是 `8848/nacos`）

---

## 小题 2「服务发现与业务调用」（2 张）

| 文件名 | 拍什么 | 要能看出的点 |
| :--- | :--- | :--- |
| `04-code-discovery-client.png` | `DiscoveryServiceInstanceResolver.java` 的 `resolveBaseUrl()` | `discoveryClient.getInstances(serviceName)`、空列表抛 503、`instance.getUri()` |
| `05-code-business-call.png` | `UserClient.requireUser()` | 先 `resolveBaseUrl(SERVICE_NAME)`，再拼 `/api/users/{id}` 发 HTTP |

如果想把"固定地址已删除"也证明一下，可以加一张（可选）：

| 文件名 | 拍什么 |
| :--- | :--- |
| `06-config-no-fixed-url.png` | `meal-service` 的 `application.yml` 里 `services:` 段只剩超时，没有 `USER_SERVICE_URL` |

---

## 小题 3「上下线与健康验证」（3 张）

| 文件名 | 拍什么 | 要能看出的点 |
| :--- | :--- | :--- |
| `07-call-success.png` | 正常时 `POST /api/meals` 返回 201 | 状态行 `HTTP/1.1 201` + `totalCalories` |
| `08-service-down-503.png` | 停掉 user-service 后的请求 | **503** + `DEPENDENCY_UNAVAILABLE` + 消息含 `user-service`；同时能看出记录数没变 |
| `09-recovered-201.png` | `docker compose start user-service` 后再请求 | 恢复 201，证明**不用重启 meal-service** 就能拿到新实例 |

---

## 小题 4「脚本与测试」（2 张）

| 文件名 | 拍什么 | 要能看出的点 |
| :--- | :--- | :--- |
| `10-smoke-test.png` | `bash scripts/smoke-test.sh` 的完整输出 | Nacos 控制台检查 PASS + 三个服务健康 + 各用例，末尾「失败：0」 |
| `11-mvn-test.png` | 三个服务分别 `mvn test` | user 5 / food 5 / meal 9，全部 `BUILD SUCCESS` |

---

## 复现命令

### 1）启动

```powershell
cd C:\Users\31238\Documents\course-repo

# 先只起 Nacos
docker compose up -d nacos

# 浏览器打开 http://localhost:18080/ ，首次会要求初始化 nacos 管理员密码
# 把该密码写入 .env 的 NACOS_PASSWORD，然后：
docker compose up -d --build user-service food-service meal-service

docker compose ps
```

### 2）看服务列表

```powershell
curl.exe "http://localhost:8848/nacos/v3/client/ns/service/list?pageNo=1&pageSize=20&groupName=PERSONAL_GROUP"
curl.exe "http://localhost:8848/nacos/v3/client/ns/instance/list?serviceName=user-service&groupName=PERSONAL_GROUP"
```

### 3）正常调用

```powershell
curl.exe -i -X POST http://localhost:8083/api/meals -H "Content-Type: application/json" -d '{\"userId\":1,\"mealType\":\"LUNCH\",\"eatenAt\":\"2026-11-02T12:30:00\",\"note\":\"服务发现验证\",\"items\":[{\"foodId\":1,\"portionGrams\":200},{\"foodId\":3,\"portionGrams\":150}]}'
```

### 4）下线 → 失败 → 恢复

```powershell
# 记下记录数
curl.exe "http://localhost:8083/api/meals?userId=1"

docker compose stop user-service
Start-Sleep -Seconds 40      # 等心跳超时，实例才会从 Nacos 摘除

curl.exe "http://localhost:8848/nacos/v3/client/ns/instance/list?serviceName=user-service&groupName=PERSONAL_GROUP"

curl.exe -i -X POST http://localhost:8083/api/meals -H "Content-Type: application/json" -d '{\"userId\":1,\"mealType\":\"BREAKFAST\",\"eatenAt\":\"2026-11-03T08:00:00\",\"items\":[{\"foodId\":1,\"portionGrams\":100}]}'

curl.exe "http://localhost:8083/api/meals?userId=1"     # 记录数应与停之前一致

docker compose start user-service
Start-Sleep -Seconds 20
curl.exe -i -X POST http://localhost:8083/api/meals -H "Content-Type: application/json" -d '{\"userId\":1,\"mealType\":\"DINNER\",\"eatenAt\":\"2026-11-04T19:00:00\",\"items\":[{\"foodId\":1,\"portionGrams\":200}]}'
```

### 5）冒烟脚本（容器网络内运行，容器里有 bash + curl）

```powershell
docker run --rm --network hdt-net -v "${PWD}:/work:ro" -w /work --entrypoint bash `
  -e USER_BASE=http://user-service:8081 -e FOOD_BASE=http://food-service:8082 `
  -e MEAL_BASE=http://meal-service:8083 -e NACOS_CONSOLE=http://nacos:8080 `
  user-service:week07 scripts/smoke-test.sh
```

> 如果你装了 Git Bash，也可以在仓库根目录直接 `bash scripts/smoke-test.sh`，
> 那样"停服务"用例会自动执行。

### 6）三个服务的测试

```powershell
mvn -f services/user-service/pom.xml test
mvn -f services/food-service/pom.xml test
mvn -f services/meal-service/pom.xml test
```

---

## 拍摄要点

- **控制台那张是得分关键**：必须同时看到三个服务名 + 分组 `PERSONAL_GROUP` + 实例数。
- 实例详情那张要能看清 **IP:端口**，这是"服务发现真的拿到了地址"的直接证据。
- 下线那张要同时体现三件事：**503**、`DEPENDENCY_UNAVAILABLE`、**本地记录数没变**。
- 恢复那张要能看出**没有重启 meal-service**（`docker compose ps` 里 meal-service 的
  Started 时间早于恢复请求的时间），说明地址是运行时现查的。
- 截图里不要出现 `.env` 的完整内容、`NACOS_AUTH_TOKEN`、任何口令。

---

## 课内实践（15 分）另需

课内实践是在**选课系统**上做 Nacos 注册，不出现在本目录，直接在作业系统提交：

| 内容 | 说明 |
| :--- | :--- |
| 座位号 | 课堂实际座位号 |
| 代码截图 | 能看出 `DiscoveryClient` 寻址与使用发现地址的 HTTP 调用（1 张） |
| 运行截图 | 能看出 Nacos 服务注册列表 + 一次选课/退课的成功调用结果 |

要求**课堂完成**，明确"不要求 GitHub 仓库、Git 提交、课后文档或测试报告"。
