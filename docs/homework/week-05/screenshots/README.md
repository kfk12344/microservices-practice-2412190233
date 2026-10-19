# 截图目录说明（Week 05）

第 05 周大作业要求 4 项证明材料，共 8–9 张截图，图片放在本目录下。

`index.md` 中已按下列文件名引用图片。**文件名必须与下表完全一致**，
否则 GitHub 页面上图片会显示成破图。

| 文件名 | 拍摄内容 | 对应小题 |
| :--- | :--- | :--- |
| `01-docker-build.png` | `docker build -t health-diet-tracker:week05 ./monolith` 的输出，要能看到 **两个阶段**（`[builder N/M]`、`[stage-1 N/M]`）与最后 `naming to ...:week05` | 1 镜像构建 |
| `02-docker-images.png` | `docker images health-diet-tracker` 的结果，能看到镜像大小 | 1 镜像构建 |
| `03-compose-up.png` | `docker compose up -d --build` 的完成输出 | 2 Compose 启动 |
| `04-compose-ps.png` | `docker compose ps`，**两个服务都是 running**，mysql 为 healthy | 2 Compose 启动 |
| `05-api-health.png` | `curl.exe http://localhost:8080/actuator/health` 返回 `"status":"UP"` | 3 API 验证 |
| `06-api-create-201.png` | POST 新增一餐返回 **201** 与响应体（含 `totalCalories`） | 3 API 验证 |
| `07-api-query.png` | `curl.exe "http://localhost:8080/api/meals?eater=docker-user"` 查到刚创建的记录 | 3 API 验证 |
| `08-data-before-down.png` | `docker compose down` **之前**查询到的记录（记下 ID） | 4 数据持久化 |
| `09-data-after-up.png` | `docker compose down` → `docker compose up -d` **之后**再查，同一条记录仍在、ID 不变 | 4 数据持久化 |

## 完整操作顺序

```powershell
cd C:\Users\31238\Documents\course-repo

# 0) 前提：Docker Desktop 已启动（鲸鱼图标变绿）
docker info --format '{{.ServerVersion}}'

# 1) 准备环境变量
Copy-Item .env.example .env
notepad .env        # 把两个 change-me-... 改成自己的口令

# 2) 拍 01：构建镜像（首次要下载基础镜像，比较慢）
docker build -t health-diet-tracker:week05 ./monolith

# 3) 拍 02：查看镜像
docker images health-diet-tracker

# 4) 拍 03 + 04：启动与状态
docker compose up -d --build
docker compose ps

# 5) 等应用就绪（约 20-40 秒），然后拍 05/06/07
curl.exe http://localhost:8080/actuator/health

curl.exe -X POST http://localhost:8080/api/meals -H "Content-Type: application/json" -d '{\"eater\":\"docker-user\",\"mealType\":\"LUNCH\",\"eatenAt\":\"2026-10-20T12:30:00\",\"note\":\"容器内创建\",\"items\":[{\"foodId\":1,\"portionGrams\":200},{\"foodId\":3,\"portionGrams\":150}]}'

curl.exe "http://localhost:8080/api/meals?eater=docker-user"

# 6) 拍 08：记下当前记录（注意 ID）

# 7) 拍 09：删容器但保留数据卷，再起
docker compose down
docker compose up -d
curl.exe "http://localhost:8080/api/meals?eater=docker-user"
```

## 拍摄要点

- **截图里带上命令本身**，让"命令 → 结果"的因果关系一目了然。
- `docker compose ps` 那张是得分关键：要能同时看到 **app 和 mysql 两个服务**，且状态为 `running`
  （mysql 还会显示 `(healthy)`）。
- 持久化那两张（08、09）要能对比出 **ID 相同** —— 这是"数据仍在"的直接证据。
- **不要用 `docker compose down -v`** 做持久化验证：`-v` 会删数据卷，数据本就该没了，
  验证不出任何东西。
- 首次 `docker build` 需要下载 `maven:3.9-eclipse-temurin-25`、`eclipse-temurin:25-jre` 两个基础镜像，
  视网络可能几分钟；`docker compose up` 还要拉 `mysql:8.4.11`。**不要中断**。

## 如果拉镜像超时

本机到 Docker Hub 的直连会超时，需要在 `~/.docker/daemon.json` 里配置镜像加速：

```json
{
  "registry-mirrors": ["https://docker.1panel.live"]
}
```

改完重启 Docker Desktop，用 `docker info` 确认 `Registry Mirrors` 生效。
详细说明见 `docs/homework/week-05/index.md` 第五节 5.1。

## 课内实践（15 分）另需两张

课内实践不出现在本目录，是直接在作业系统提交：

| 内容 | 说明 |
| :--- | :--- |
| 座位号 | 课堂实际座位号 |
| 容器运行成功截图 | 能看出应用与 MySQL 容器均成功运行 |
| API 测试成功截图 | 能看出请求地址、状态码与响应内容 |

这部分要求**课堂完成**、且明确说"不要求 GitHub 仓库、Git 提交、课后文档或测试报告"，
因此不需要放进本仓库。
