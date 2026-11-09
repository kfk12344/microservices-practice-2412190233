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
bash scripts/e2e-test.sh
```

**图里必须出现**：

- 命令 `bash scripts/e2e-test.sh`
- 第 2 段那行统计：`{"food-service":{...},"user-service":{...}}`
- 第 3 段的 `[PASS] 依赖不可用时返回 503`
- 最后两行：`通过 13　失败 0　跳过 0` 和 `端到端验证通过`

> 一屏放不下就**滚到能看到汇总的地方**再截，或者上下各截一张拼起来（那样文件名加 `-1`、`-2`）。

---

### 图 2 · `08-02-负载均衡分发证据.png`（对应小题 3「负载均衡验证」）

**分两步，一张图里要有两个证据**：

```powershell
# ① 先打几轮，让两个实例都接到请求
for ($i=1; $i -le 8; $i++) {
  curl.exe -s -o NUL -X POST http://localhost:8083/api/meals `
    -H "Content-Type: application/json" `
    --data-binary "@$env:TEMP\meal.json"
}
```

> 上面的 `@$env:TEMP\meal.json` 需要先准备好请求体文件：

```powershell
$json = '{"userId":1,"mealType":"LUNCH","eatenAt":"2026-11-20T12:30:00","note":"截图用","items":[{"foodId":1,"portionGrams":100},{"foodId":3,"portionGrams":100}]}'
[System.IO.File]::WriteAllText("$env:TEMP\meal.json", $json, (New-Object System.Text.UTF8Encoding($false)))
```

```powershell
# ② 看分发统计
curl.exe -sS http://localhost:8083/api/meals/instances

# ③ 看两个实例确实都在跑、且 2 号没占宿主端口
docker ps --format "{{.Names}}  {{.Status}}  {{.Ports}}" | Select-String "user-service|food-service"
```

**图里必须出现**：

- `curl -sS http://localhost:8083/api/meals/instances`
- 返回的 JSON 里 **两个实例都出现**，例如
  `{"food-service":{"food-service-1":8,"food-service-2":8},"user-service":{"user-service-1":4,"user-service-2":4}}`
- `docker ps` 那行：`hdt-user-service-2` 的端口列只有 `8081/tcp`，**没有** `0.0.0.0:8081->8081`

> 第 3 点很关键：它就是作业要求的"避免实例绑定同一个宿主机端口"。把这一行框进图里。

**如果统计里只有一个实例**：说明另一台没注册上。先在
`http://localhost:18080/next/`（分组选 `PERSONAL_GROUP`）确认 `user-service` 有 2 个实例，
或者多打几轮（轮询需要次数才看得到分布）。

---

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
| `bash` 找不到 | 本机 bash 在 `D:\Git\bin\bash.exe`，用完整路径，或把 `D:\Git\bin` 加进 PATH |
| curl 返回 400 `MALFORMED_REQUEST_BODY` | PowerShell 里 `-d "{\"...\"}"` 的引号会被吃掉。用 `--data-binary "@文件"` 的方式（上面给的写法） |
| curl 返回 400 `portionGrams 不能为空` | 明细字段名是 `portionGrams`，不是 `grams` |
| 统计里只有一个实例 | 打轮次数太少（轮询要看分布），或另一台没注册上 |
| 停实例后没变成 503 | 没等够 45 秒；Nacos 摘除有延迟，不是立即生效 |
| 截出来一片乱码 | 终端编码问题：换成 Windows Terminal，或在 `bash` 前先 `chcp 65001` |
