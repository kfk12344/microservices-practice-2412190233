# 截图目录说明（Week 06）

第 06 周大作业有三个小题需要截图，每题可上传 1–5 张。
下面按小题分组，`index.md` 中已按这些文件名引用图片 —— **文件名必须完全一致**，否则 GitHub 上是破图。

---

## 小题 2「微服务代码」（5 张）

| 文件名 | 拍什么 | 要能看出的点 |
| :--- | :--- | :--- |
| `01-services-tree.png` | VSCode 里展开 `services/` 目录树 | 三个独立工程，各有自己的 `pom.xml` + `src/`；`monolith/` 仍在旁边未被改动 |
| `02-code-meal-service.png` | `MealRecordService.create()` 的 ①–⑤ 五步注释 | **先跨服务校验、后写库**的顺序；失败时不会写本地 |
| `03-code-user-client.png` | `UserClient.requireUser()` 的 catch 顺序 | 404 → `USER_NOT_FOUND`（400），其余 `RestClientException` → `DEPENDENCY_UNAVAILABLE`（503） |
| `04-config-yml.png` | `meal-service/src/main/resources/application.yml` 的 `services:` 段 | 地址可被环境变量覆盖 + 连接/读取超时 |
| `05-tests-pass.png` | 三个服务分别 `mvn test` 的结果 | user 5 / food 5 / meal 7，全部 `BUILD SUCCESS` |

## 小题 3「本地编排」（2 张）

| 文件名 | 拍什么 | 要能看出的点 |
| :--- | :--- | :--- |
| `06-compose-ps.png` | `docker compose ps` | **四个容器全部 healthy**：mysql + user-service + food-service + meal-service |
| `07-compose-yaml.png` | `compose.yaml` 里 mysql 的 `volumes`、三个服务的 `environment`、`depends_on` | 数据卷、服务名通信、健康检查依赖 |

## 小题 4「验证」（4 张）

| 文件名 | 拍什么 | 要能看出的点 |
| :--- | :--- | :--- |
| `08-db-isolation.png` | `SHOW GRANTS` + `svc_meal` 读 `health_diet_user` 被拒绝 | 数据隔离在**数据库权限层**生效（ERROR 1142） |
| `09-smoke-test.png` | `scripts/smoke-test.sh` 的完整输出 | 16 项通过、0 失败 |
| `10-cross-service-201.png` | `POST /api/meals` 返回 201 与 `totalCalories:431.50` | 跨服务流程成功（meal 调了 user + food） |
| `11-dependency-503.png` | 停掉 user-service 后的 503 响应 | `DEPENDENCY_UNAVAILABLE` + 明确指向 user-service + 未写数据 |

---

## 复现命令

### 小题 2 的截图

```powershell
# 01：在 VSCode 左侧资源管理器展开 services/ 目录
# 02/03/04：在 VSCode 里打开对应文件
# 05：三个服务分别跑测试
cd C:\Users\31238\Documents\course-repo\services\user-service
mvn test
cd ..\food-service
mvn test
cd ..\meal-service
mvn test
cd ..\..
```

### 小题 3 的截图

```powershell
cd C:\Users\31238\Documents\course-repo
docker compose ps
# 07：在 VSCode 里打开 compose.yaml，选中有 mysql volumes / services environment 的那几段
```

### 小题 4 的截图

```powershell
cd C:\Users\31238\Documents\course-repo

# 08：数据库隔离
#   先看三个账号的授权（口令在 .env 里）
docker exec hdt-mysql mysql -uroot -p你的root口令 -N -e "SHOW GRANTS FOR 'svc_meal'@'%'"
#   再证明读不到别人的库
docker exec hdt-mysql mysql -usvc_meal -p你的meal口令 -e "SELECT COUNT(*) FROM health_diet_user.app_user"
```

```bash
# 09：冒烟测试（在容器网络内运行，容器里有 curl + bash）
docker run --rm --network hdt-net \
  -v "${PWD}:/work:ro" -w /work \
  --entrypoint bash \
  -e USER_BASE=http://user-service:8081 \
  -e FOOD_BASE=http://food-service:8082 \
  -e MEAL_BASE=http://meal-service:8083 \
  user-service:week06 scripts/smoke-test.sh
```

> 提示：如果你装了 Git Bash，也可以在仓库根目录直接 `bash scripts/smoke-test.sh`，
> 那样用例 5（依赖服务不可用）会自动执行，不用手动停服务。
> 本机 `wsl` 里没有 bash，所以用上面的容器方式。

```powershell
# 10：跨服务成功
curl.exe -X POST http://localhost:8083/api/meals -H "Content-Type: application/json" -d '{\"userId\":1,\"mealType\":\"LUNCH\",\"eatenAt\":\"2026-10-29T12:30:00\",\"note\":\"截图用\",\"items\":[{\"foodId\":1,\"portionGrams\":200},{\"foodId\":3,\"portionGrams\":150}]}'

# 11：依赖不可用（分三屏，最好截在同一张里）
docker compose stop user-service
curl.exe -i -X POST http://localhost:8083/api/meals -H "Content-Type: application/json" -d '{\"userId\":1,\"mealType\":\"BREAKFAST\",\"eatenAt\":\"2026-10-28T08:00:00\",\"items\":[{\"foodId\":1,\"portionGrams\":100}]}'
docker compose start user-service
```

---

## 拍摄要点

- **每张截图带上命令本身**，让"命令 → 结果"的因果关系一目了然。
- `docker compose ps` 那张是小题 3 的得分关键：必须能同时看到 **四个容器**且都是 `healthy`。
- 数据库隔离那张（08）最有说服力的是那行 **`ERROR 1142 (42000): SELECT command denied`** ——
  它证明隔离不是靠代码自觉，而是数据库权限层强制。
- 冒烟测试输出较长，**建议截包含「用例 1」和「结果汇总」两端**，或分两张，
  但文件名要在 `index.md` 里同步调整。
- 503 那张要能看出三件事：状态码 503、`DEPENDENCY_UNAVAILABLE`、消息里明确写了 `user-service`。

## 课内实践（15 分）另需

课内实践不出现在本目录，直接在作业系统提交：

| 内容 | 说明 |
| :--- | :--- |
| 座位号 | 课堂实际座位号 |
| 代码截图 | 能看出独立服务、端口或服务边界实现（1 张） |
| 运行结果截图 | 能看出服务启动或健康检查结果（1 张） |

要求**课堂完成**，且明确"不要求 GitHub 仓库、Git 提交、课后文档或测试报告"。
