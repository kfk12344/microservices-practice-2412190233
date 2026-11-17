# 第 09 周 · 截图指南（纯 PowerShell 版）

> 目标：每张图都能"一眼看出命令 + 结果 + 状态码"，而不是只截一个结果。
> 保存到本目录：`docs/homework/week-09/screenshots/`，文件名用下面表格里的名字。
> 以下命令全部在 **Windows PowerShell**（开始菜单搜 "Windows PowerShell"）里执行，
> 不需要 Git Bash；每条都已在 PowerShell 5.1 实测通过。

---

## 一、准备

1. 启动 Docker Desktop，等左下角鲸鱼图标变绿。
2. 仓库根目录执行（首次或改过代码要 `--build`）：

   ```powershell
   Set-Location c:\Users\31238\Documents\course-repo
   docker compose up -d --build
   docker compose ps
   ```

   等到 `hdt-gateway` 与三个业务服务都是 `running (healthy)`。

3. **PowerShell 三个坑（务必照下面的写法）**：

   - `curl` 是 `Invoke-WebRequest` 的别名 → 必须写 **`curl.exe`**。
   - PowerShell 会把内联 JSON 里的双引号吃掉（实测后端直接 400 `MALFORMED_REQUEST_BODY`）
     → JSON 先写进临时文件，再用 `-d "@文件路径"` 传。
   - `$TOKEN` 这类变量只在**当前窗口**有效 → 03、04 必须在同一个窗口里连续执行，
     不要关掉窗口后从 04 开始。

4. 每条请求都加 `-i`，这样截图里能看到 `HTTP/1.1 401` / `403` / `200` 状态行。

5. 截图工具：`Win + Shift + S` 框选，自动进剪贴板；
   然后粘贴到画图/微信并另存到本目录（PNG）。
   建议把 PowerShell 窗口拉宽、字号调到 14 左右，保证长 JSON 不折行。

---

## 二、逐张拍摄

### 01 · 网关健康与已注册服务

**必须出现**：两个接口都是 200；`/api/health` 的 `registeredServices` 里能看到
`user-service`、`food-service`、`meal-service`、`gateway-service`。

```powershell
curl.exe -i http://localhost:8080/api/health
curl.exe -i http://localhost:8080/actuator/health
```

保存为 `01-gateway-health.png`。

---

### 02 · 未登录 401，登录后拿到 Token

**必须出现**：第一条是 `HTTP/1.1 401` 和 `"code":"UNAUTHORIZED"`；
第二条是 `HTTP/1.1 200` 和返回值里的 `token` / `role`。

```powershell
# ① 未登录访问受保护接口：期望 401
curl.exe -i http://localhost:8080/api/users/1

# ② 登录：先把 JSON 写进临时文件（PowerShell 内联 JSON 会丢引号）
Set-Content "$env:TEMP\login-demo.json" '{"username":"demo-user","password":"Demo@1234"}' -Encoding ASCII
curl.exe -i -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "@$env:TEMP\login-demo.json"
```

保存为 `02-login-401-200.png`。

---

### 03 · 带 Token 访问成功

**必须出现**：`HTTP/1.1 200`，响应体里 `demo-user`、`"role":"USER"`、`servedBy`。

> 必须和 02 在**同一个 PowerShell 窗口**里执行，`$TOKEN` 才有值。

```powershell
$login = curl.exe -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "@$env:TEMP\login-demo.json" | ConvertFrom-Json
$TOKEN = $login.token
$TOKEN.Substring(0, 40) + "..."     # 回显前 40 个字符，确认变量非空

curl.exe -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/users/1
```

保存为 `03-token-access-200.png`。

---

### 04 · 普通用户越权 403（含伪造头）

**必须出现**：前两条都是 `HTTP/1.1 403`、`"code":"FORBIDDEN"`，
消息为 `该接口需要 ADMIN 角色，当前角色：USER`；
第二条请求明明带了 `X-User-Role: ADMIN`，仍然被拒；
第三条无 Token 但伪造角色头，是 `401`。

```powershell
curl.exe -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/admin/users

curl.exe -i -H "Authorization: Bearer $TOKEN" -H "X-User-Role: ADMIN" http://localhost:8080/api/admin/users

curl.exe -i -H "X-User-Role: ADMIN" http://localhost:8080/api/admin/users
```

保存为 `04-forbidden-403.png`。

---

### 05 · 管理员访问成功

**必须出现**：`HTTP/1.1 200`，返回用户数组，能看到 `bob` 的 `"role":"ADMIN"`。

```powershell
Set-Content "$env:TEMP\login-admin.json" '{"username":"bob","password":"Admin@1234"}' -Encoding ASCII
$ADMIN_TOKEN = (curl.exe -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "@$env:TEMP\login-admin.json" | ConvertFrom-Json).token

curl.exe -i -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/admin/users
```

保存为 `05-admin-200.png`。

---

### 06 · 自动验证脚本全部通过

**必须出现**：每条 `[PASS]`，最后是 `通过 12 项，失败 0 项` 和绿色
`全部通过：未授权被拦截、登录可用、角色越权被阻止、伪造头无效`。

```powershell
powershell -ExecutionPolicy Bypass -File scripts\auth-test.ps1
```

保存为 `06-auth-test-all-pass.png`。
如果终端一屏放不下，就截**最后 15 行**（从 "4. 角色校验" 到汇总），
前面部分可以再截一张命名 `06b-auth-test-top.png`。

---

### 07 · 数据库里只有 BCrypt 哈希（可选，强烈建议）

**必须出现**：`password_hash` 列是 `$2a$10$...` 开头的 60 字符字符串，
没有任何明文口令。

```powershell
Set-Content "$env:TEMP\hash.sql" 'SELECT id, username, role, LEFT(password_hash,20) AS hash_prefix FROM app_user;'
Get-Content "$env:TEMP\hash.sql" -Raw | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot health_diet_user'
```

期望输出三行，`hash_prefix` 都是以 `$2a$10$` 开头：

```
id  username   role           hash_prefix
1   demo-user  USER           $2a$10$mDTfXICj7NQd.
2   alice      NUTRITIONIST   $2a$10$5jW8olHnwChqt
3   bob        ADMIN          $2a$10$WZFzsayyQd5Mv
```

保存为 `07-db-bcrypt-hash.png`。

---

### 08 · Nacos 控制台（可选）

浏览器打开 <http://localhost:18080/>（首次需初始化管理员密码，填进 `.env` 的 `NACOS_PASSWORD`），
在「服务管理 → 服务列表」中截取：

- 服务名包含 `gateway-service`、`user-service`、`food-service`、`meal-service`；
- `user-service` 的实例数是 **2**（第 08 周的多实例仍然有效）。

保存为 `08-nacos-services.png`。

---

## 三、自查清单

交作业前逐项确认：

- [ ] 01 里四个服务名都出现在 `registeredServices` 中
- [ ] 02 的第一条确实是 401，而不是 200/403
- [ ] 03 的响应体是 `demo-user`，不是别人的数据
- [ ] 04 前两条都是 403，且第二条带了伪造头；第三条是 401
- [ ] 05 是 200，能看到 `ADMIN` 角色
- [ ] 06 的汇总行是 "失败 0 项"
- [ ] 图片放进 `docs/homework/week-09/screenshots/`，文件名与上面一致
- [ ] 截图里不要出现 `.env` 里的真实 `JWT_SECRET` / `GATEWAY_SECRET`

> 如果某一步失败，先看 `docker compose logs --tail=100 gateway-service`，
> 常见原因是：Docker 没启动、`.env` 缺 `JWT_SECRET`、或者网关比下游先起（等 30 秒重试）。
> 若 `curl.exe` 报 `Could not resolve host`，说明 Docker Desktop 还没起来或 8080 没映射。
