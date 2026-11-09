# 第 08 周截图指南（自己动手版）

> 本文件告诉你怎么**自己截**这 4 张图，以及每张图里必须出现什么。
> 目录里现有的 4 张 png 是我把真实运行输出渲染成的图（内容是真的，但不是屏幕截图）。
> **你截图后直接用同样的文件名覆盖**，文档里的引用就不用改。

---

## 0. 先让环境跑起来

截图前必须先把服务起来，否则命令全是空的。

```powershell
# ① 确认 Docker Desktop 已经开起来（托盘图标不再转圈）

# ② 在仓库根目录启动
cd C:\Users\31238\Documents\course-repo
docker compose up -d

# ③ 等 40~60 秒，看到 5 个业务容器都 healthy
docker compose ps
```

**验收标准**：`hdt-user-service`、`hdt-user-service-2`、`hdt-food-service`、`hdt-food-service-2`、
`hdt-meal-service` 都是 `Up (healthy)`。

> 如果 `meal-service` 还在 `health: starting`，再等 30 秒 —— 它要等 MySQL 与两个下游就绪。

---

## 1. 怎么截

| 方式 | 操作 |
|---|---|
| **推荐** | 用 **Windows Terminal** 跑命令（支持中文、颜色正常），按 `Win + Shift + S` → 选框 → 自动进剪贴板 → 存成 png |
| 或者 | 直接按 `Alt + PrtSc` 截当前窗口，粘到「画图」里另存 |

**三条硬要求**（不满足会被认为"看不出做了什么"）：

1. **命令行本身要在图里** —— 只有输出没有命令，看不出这条结果是怎么来的；
2. **不要把提示符裁掉**，`PS C:\Users\31238\Documents\course-repo>` 这一段要留着；
3. 字号别太小：Terminal 里 `Ctrl + 滚轮` 放大到能看清输出，再截。

---

## 2. 四张图分别怎么截

### 图 1 · `08-01-e2e-全流程通过.png`（对应小题 4「测试」+ 整体）

**这一步要 3 分钟左右**（脚本里有两次 45 秒等待，是在等 Nacos 摘除实例）。

```powershell
cd C:\Users\31238\Documents\course-repo
powershell -ExecutionPolicy Bypass -File scripts\e2e-test.ps1
```

> Linux / macOS / Git Bash 里直接用 `bash scripts/e2e-test.sh`（同一个脚本）。
> Windows 上如果 `bash` 指向 WSL 转发器会报 `execvpe(/bin/bash) failed`，那就用上面的 `.ps1`。

**图里必须出现**：

- 命令 `bash scripts/e2e-test.sh`
- 第 2 段那行统计：`{"food-service":{...},"user-service":{...}}`
- 第 3 段的 `[PASS] 依赖不可用时返回 503`
- 最后两行：`通过 13　失败 0　跳过 0` 和 `端到端验证通过`

> 一屏放不下就**滚到能看到汇总的地方**再截，或者上下各截一张拼起来（那样文件名加 `-1`、`-2`）。

---

### 图 2 · `08-02-负载均衡分发证据.png`（对应小题 3「负载均衡验证」）

**只需要一条命令**（我已经把它写成了脚本，会自动打 8 轮再把证据排版好）：

```powershell
cd C:\Users\31238\Documents\course-repo
powershell -ExecutionPolicy Bypass -File scripts\shot-lb.ps1
```

跑完屏幕上会依次出现 5 段（大约一屏放得下）：

```
0. 前置检查          → meal-service 状态：UP
1. 清空旧的命中统计   → DELETE /api/meals/instances -> 204（已清空）
2. 连续下单 8 次      → 成功 8 次，失败 0 次
3. 证据一：分发统计   → {"food-service":{"food-service-1":8,"food-service-2":8},
                        "user-service":{"user-service-1":4,"user-service-2":4}}
4. 证据二：docker ps  → hdt-user-service-2   8081/tcp          ← 只有容器内端口
                       hdt-user-service     0.0.0.0:8081->8081  ← 1 号才占了宿主端口
5. 结论             → [PASS] 请求确实被分发到两个实例上（不是只看注册列表）
```

**直接截这一屏就行**，`Win + Shift + S` → 框选 → 保存成
`08-02-负载均衡分发证据.png` 覆盖到本目录。

#### 为什么这张图能证明负载均衡

图里有**两个独立证据**，缺一不可：

| 证据 | 证明什么 |
|---|---|
| 第 3 段：两个实例的计数**都在增长** | 请求确实被分发到了两台实例（统计的是真实业务调用，不是注册列表） |
| 第 4 段：2 号实例**只 expose 没占宿主端口** | 满足作业"避免实例绑定同一个宿主机端口"的要求 |

> 只有第 3 段、没有第 4 段，会被质疑"两个实例是不是绑了同一个端口"；
> 只有第 4 段、没有第 3 段，就退回成"只看注册列表"，作业明确不算分。

#### 如果结论是 FAIL

说明只落到了一个实例，按顺序查：

1. 打开 `http://localhost:18080/next/`，分组选 `PERSONAL_GROUP`，
   确认 `user-service`、`food-service` **各有 2 个实例**；
2. 如果只有 1 个：`docker compose ps` 看 `hdt-user-service-2` / `hdt-food-service-2` 是否 healthy；
   没起来就 `docker compose up -d`；
3. 都健康但还是 FAIL：把 `-Rounds` 调大再试（轮询要看分布，次数太少可能碰巧只打到一台）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\shot-lb.ps1 -Rounds 12
```

### 图 3 · `08-03-依赖不可用503.png`（对应「运行结果截图」小题 + 小题 4 的一半）

```powershell
cd C:\Users\31238\Documents\course-repo

# ① 先记下当前记录数（截图时对比用）
(Invoke-RestMethod 'http://localhost:8083/api/meals?userId=1' -TimeoutSec 10).Count

# ② 停掉全部 user-service 实例
docker stop hdt-user-service hdt-user-service-2

# ③ 等 45 秒（心跳 5s + 判超时 15s + 摘除 30s），别急着发
Start-Sleep -Seconds 45

# ④ 再下一次单：应该 503
curl.exe -sS -X POST http://localhost:8083/api/meals `
  -H "Content-Type: application/json" --data-binary "@$env:TEMP\meal.json"

# ⑤ 再数一次记录数：应该和 ① 一样
(Invoke-RestMethod 'http://localhost:8083/api/meals?userId=1' -TimeoutSec 10).Count

# ⑥ 恢复
docker compose start user-service user-service-2
```

> ⚠️ 计数**不要**用 `curl | Select-String "id" | Measure-Object` ——
> 那数的是"含 id 的行数"，JSON 一整行只算 1。必须用 `Invoke-RestMethod` 解析成数组再 `.Count`
> （实测：前者恒为 1，后者才是真实条数）。

**图里必须出现**：

- `docker stop hdt-user-service hdt-user-service-2`
- 返回体里的 `"status":503` 和 `"code":"DEPENDENCY_UNAVAILABLE"`
- 前后两次记录数**相同**（证明没写脏数据）

> 恢复那步别忘，不然后面实验全失败。恢复后等 20~30 秒再继续。

---

### 图 4 · `08-04-OpenFeign客户端.png`（对应小题 2「使用 OpenFeign」的代码截图）

作业要求"截图应清楚显示**文件名**和本次完成的关键内容"，所以**文件名要在图里**。

两种截法，选一种：

**A. 在编辑器里截（推荐，文件名 + 高亮都有）**

用 VS Code / IDEA 打开这个文件：

```
C:\Users\31238\Documents\course-repo\services\meal-service\src\main\java\com\zjgsu\hdt\meal\client\UserFeignApi.java
```

截 `@FeignClient(name = ...)` 到 `@GetMapping` 那一段，**标签页上的文件名要留着**。

**B. 在终端里截（和我生成的版本一样）**

```powershell
cd C:\Users\31238\Documents\course-repo
Get-Content "services\meal-service\src\main\java\com\zjgsu\hdt\meal\client\UserFeignApi.java" | Select-Object -First 30
```

**图里必须出现**：

- 文件名 `UserFeignApi.java`
- `@FeignClient(name = UserFeignApi.SERVICE_NAME)` 这一行（**这是"按服务名调用"的证据**）
- `@GetMapping("/api/users/{id}")`

> 想再加一张的话，可以截 `UserClient.java` 里那段 try/catch ——
> 那是"失败处理"的证据（404 → 400、其余 → 503）。

---

## 3. 截完自检

```powershell
cd C:\Users\31238\Documents\course-repo
Get-ChildItem docs\homework\week-08\screenshots\*.png |
  Select-Object Name, @{n='KB';e={[int]($_.Length/1KB)}}, LastWriteTime
```

应该看到 4 个文件，时间是你刚截的：

```
08-01-e2e-全流程通过.png
08-02-负载均衡分发证据.png
08-03-依赖不可用503.png
08-04-OpenFeign客户端.png
```

然后提交：

```powershell
git add docs/homework/week-08/screenshots
git commit -m "docs: Week08 截图换成真实屏幕截图"
git push origin main
```

---

## 4. 上传作业系统时的对应关系

| 作业系统里的位置 | 传哪张 |
|---|---|
| 小题 2「使用 OpenFeign」→ 上传 1-5 张 | `08-04-OpenFeign客户端.png`（可加代码失败处理那张） |
| 小题 3「负载均衡验证」→ 上传 1-5 张 | `08-02-负载均衡分发证据.png` |
| 小题 4「测试」→ 上传 1-5 张 | `08-01-e2e-全流程通过.png`、`08-03-依赖不可用503.png` |
| 「运行结果截图」那一栏 | `08-03-依赖不可用503.png` |
| 「代码截图」那一栏 | `08-04-OpenFeign客户端.png` |

---

## 5. 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| `execvpe(/bin/bash) failed` | PATH 上的 `bash` 是 WSL 转发器（没装发行版）。用 `powershell -ExecutionPolicy Bypass -File scripts\e2e-test.ps1`，或直接 `D:\Git\bin\bash.exe scripts/e2e-test.sh` |
| curl 返回 400 `MALFORMED_REQUEST_BODY` | PowerShell 里 `-d "{\"...\"}"` 的引号会被吃掉。用 `--data-binary "@文件"` 的方式（上面给的写法） |
| curl 返回 400 `portionGrams 不能为空` | 明细字段名是 `portionGrams`，不是 `grams` |
| 统计里只有一个实例 | 打轮次数太少（轮询要看分布），或另一台没注册上 |
| 停实例后没变成 503 | 没等够 45 秒；Nacos 摘除有延迟，不是立即生效 |
| 截出来一片乱码 | 终端编码问题：换成 Windows Terminal，或在 `bash` 前先 `chcp 65001` |
