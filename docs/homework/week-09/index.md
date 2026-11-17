# 第 09 周 · API 网关与 JWT 认证（35 分）

> 相关代码：`services/gateway-service`（新建）、`services/user-service`（签发 Token）、`compose.yaml`（统一入口）、`scripts/auth-test.sh`（自动验证）
> 截图目录：`docs/homework/week-09/screenshots/`（拍法见同目录 `README.md`）

---

## 0. 本周交付清单

| 要求 | 交付物 | 状态 |
|---|---|---|
| 新建独立网关工程，根目录 `compose.yaml` 接入 | `services/gateway-service/` + `compose.yaml` 的 `gateway-service` 段 | 已完成 |
| 至少 3 条路由，目标使用服务名 | 5 条路由，全部 `lb://服务名` | 已完成 |
| 健康入口 `/api/health` 与 `/actuator/health` | 网关 `HealthController` + Actuator | 已完成 |
| 登录接口返回 JWT | `POST /api/auth/login`（user-service） | 已完成 |
| 至少 2 种角色/权限 | `USER` / `NUTRITIONIST` / `ADMIN` 三种 | 已完成 |
| 口令安全哈希存储 | BCrypt（成本因子 10），库中无明文 | 已完成 |
| 未登录 401、登录后 200、角色校验 | 网关 `JwtAuthenticationFilter` | 已完成 |
| 覆盖伪造身份请求头 | 网关无条件清洗并重写 `X-User-*`；`auth-test.sh` 有 2 条用例 | 已完成 |
| 说明下游如何避免绕过网关 | 本文 §3.4 + `docs/security.md` | 已完成 |
| 自动测试脚本 | `scripts/auth-test.sh`（+ Windows 启动器 `auth-test.ps1`） | 已完成 |

---

## 1. 独立网关工程与路由

### 1.1 工程结构

```
services/gateway-service/
├── Dockerfile                     # 与其它服务一致的多阶段构建
├── pom.xml                        # Spring Cloud Gateway 5.0（WebFlux）
└── src/main/
    ├── java/com/zjgsu/hdt/gateway/
    │   ├── GatewayApplication.java
    │   ├── config/JwtConfig.java             # NimbusJwtDecoder（HS256，只验签）
    │   ├── controller/HealthController.java  # /api/health
    │   └── filter/JwtAuthenticationFilter.java # 清洗 → 验签 → 授权 → 注入身份
    └── resources/application.yml             # 路由表
```

技术选型说明：

- 用 **Spring Cloud Gateway 5.0 的 WebFlux 版本**（`spring-cloud-starter-gateway-server-webflux`）。
  5.0 起 artifact 改名，旧的 `spring-cloud-starter-gateway` 已不推荐；配置前缀也变成
  `spring.cloud.gateway.server.webflux.*`。
- 网关自己注册到 Nacos，并用 **LoadBalancer + 服务名** 转发，
  不写死任何 IP/端口，扩容缩容不用改配置。

### 1.2 路由表（5 条，全部使用服务名）

| 路由 ID | 路径断言 | 目标 | 网关过滤器行为 |
|---|---|---|---|
| `auth-route` | `/api/auth/**` | `lb://user-service` | **白名单**：不要求 Token |
| `user-route` | `/api/users/**` | `lb://user-service` | 要求有效 JWT |
| `admin-route` | `/api/admin/**` | `lb://user-service` | 要求有效 JWT **且 `role=ADMIN`** |
| `food-route` | `/api/foods/**` | `lb://food-service` | 要求有效 JWT |
| `meal-route` | `/api/meals/**` | `lb://meal-service` | 要求有效 JWT |

配置位置：`services/gateway-service/src/main/resources/application.yml`。
服务启动参数（端口、Nacos 地址、密钥）在根目录 `compose.yaml` 的 `gateway-service` 段，
密钥从 `.env` 注入，不写进仓库。

### 1.3 统一入口与健康检查

```bash
curl http://localhost:8080/api/health
# {"status":"UP","service":"gateway-service","timestamp":"...",
#  "registeredServices":["food-service","gateway-service","meal-service","user-service"]}

curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

`/api/health` 额外返回 Nacos 里当前注册的服务名，用来证明"网关确实能发现下游"；
`/actuator/health` 给探针用，只回答进程是否存活。

> 端口提醒：单体版本（`monolith` profile）也监听 8080，两者不能同时启动。

---

## 2. 登录、JWT 与角色

### 2.1 登录接口

```
POST /api/auth/login
Content-Type: application/json

{"username":"demo-user","password":"Demo@1234"}
```

响应（节选）：

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6IlVTRVIi...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": 1,
  "username": "demo-user",
  "displayName": "演示用户",
  "role": "USER"
}
```

后续请求带 `Authorization: Bearer <token>` 即可，**不需要**自己传用户 ID 或角色。

### 2.2 Token 里放了什么

| 声明 | 值 | 用途 |
|---|---|---|
| `sub` | 用户 ID | 网关注入 `X-User-Id` |
| `username` | 登录名 | 日志排查 |
| `role` | `USER` / `NUTRITIONIST` / `ADMIN` | 网关做接口级授权 |
| `iss` | `user-service` | 标明签发方 |
| `iat` / `exp` | 签发/过期时间 | 过期后验签直接失败 |

**刻意不放**手机号、邮箱、口令哈希：JWT 只签名不加密，任何人 Base64 解码就能看到内容。

签名算法 **HS256**：user-service 与 gateway 共享一把 ≥32 字节的 HMAC 密钥
（`JWT_SECRET`，由 `.env` 注入）。选它是因为只有"一个签发方 + 一个验签方"，
结构最简单；代价是验签方也能签发，所以密钥绝不能进仓库，生产环境应换 RS256。

### 2.3 口令存储

- 建表语句里字段是 `password_hash VARCHAR(100) NOT NULL`，**没有明文列**。
- 创建用户时 `BCryptPasswordEncoder.encode()` 后立即落库，明文不写日志、不返回。
- 种子数据 `data.sql` 里也只有 BCrypt 哈希。
- 登录失败时"用户不存在"和"口令错误"返回**同一个** 401，避免账号枚举。

测试账号（仅用于本地演示）：

| 用户名 | 口令 | 角色 | 用途 |
|---|---|---|---|
| `demo-user` | `Demo@1234` | `USER` | 验证"能登录，但访问管理接口 403" |
| `alice` | `Diet@1234` | `NUTRITIONIST` | 第二种角色 |
| `bob` | `Admin@1234` | `ADMIN` | 验证管理接口 200 |

> 这三个口令只写在文档和验证脚本里，数据库里存的是哈希。
> 它们不是"提交进代码的真实密钥"，生产环境必须删除或改密。

---

## 3. 网关鉴权规则

`JwtAuthenticationFilter` 是 `GlobalFilter`，`getOrder() = -100`，早于路由转发，
因此下游收到的请求头一定是网关处理过的。执行顺序：

1. **清洗**：无条件删除客户端带来的 `X-User-Id`、`X-User-Role`、`X-User-Name`、`X-Gateway-Secret`；
2. **放行白名单**：`/api/auth/login`、`/api/health`、`/actuator/**`；
3. **验签**：其余路径必须带 `Authorization: Bearer ...`，缺失/格式错/签名错/过期 → **401**；
4. **授权**：`/api/admin/**` 且 `role != ADMIN` → **403**；
5. **注入身份**：把 `sub` / `role` / `username` 写入 `X-User-Id` / `X-User-Role` / `X-User-Name`，
   并附加内部口令 `X-Gateway-Secret`。

### 3.1 未登录

```bash
curl -i http://localhost:8080/api/users/1
# HTTP/1.1 401
# {"status":401,"error":"Unauthorized","code":"UNAUTHORIZED",
#  "message":"缺少 Bearer Token：请先调用 POST /api/auth/login 获取 Token","path":"/api/users/1"}
```

### 3.2 登录后

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo-user","password":"Demo@1234"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/users/1
# HTTP/1.1 200
```

### 3.3 角色校验

| 请求 | 期望 | 原因 |
|---|---|---|
| `GET /api/admin/users`，USER Token | **403** | 已认证但无权限 |
| `GET /api/admin/users`，ADMIN Token | **200** | 通过 |
| `GET /api/admin/users`，无 Token | **401** | 未认证优先于授权 |

### 3.4 伪造身份头与"绕过网关直连"

**攻击方式**：客户端直接加 `X-User-Role: ADMIN` 之类的头，赌下游会信任它。

**网关侧**：`JwtAuthenticationFilter` 在判断前就 `headers.remove(...)`，
再把验签得到的真实角色 `set` 进去。客户端写的值不可能留下来，同名头只可能有一个值。

**下游侧**（防止请求根本不经过网关）：

1. `AdminController` 先校验 `X-Gateway-Secret` 是否等于 `GATEWAY_SECRET`，
   不匹配直接 403（`GATEWAY_REQUIRED`）。这个口令只有网关持有，
   直连者即使猜到端口、伪造 `X-User-Role: ADMIN`，也过不了第一关；
2. 校验顺序是"先来自网关、再角色"，否则伪造角色头就能绕过；
3. 比较用 `MessageDigest.isEqual`，避免按响应时间猜口令。

自动脚本覆盖了两条伪造用例：

| 请求 | 期望 |
|---|---|
| 无 Token + `X-User-Role: ADMIN` | 401 |
| USER Token + `X-User-Role: ADMIN` 访问管理接口 | 403 |

> 本周为便于联调，compose 仍把 8081/8082/8083 映射到了宿主机。
> 生产部署应只发布网关的 8080，其余服务只 `expose`，让"只能走网关"成为网络层事实，
> 而不是只靠应用层口令（见 `docs/security.md` §5）。

---

## 4. 自动验证脚本

```powershell
# 仓库根目录；首次或代码变更后加 --build
docker compose up -d --build

# 如果是第 08 周留下来的数据库卷（app_user 还没有认证字段），先执行一次迁移：
Get-Content db\migrations\09-auth.sql -Raw | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot health_diet_user'

# 自动验证（Windows PowerShell）
powershell -ExecutionPolicy Bypass -File scripts\auth-test.ps1
```

Linux / macOS / Git Bash 里等价于 `bash scripts/auth-test.sh`。

脚本做 12 项断言，覆盖：

| # | 场景 | 期望 |
|---|---|---|
| 0 | 网关 `/api/health` 就绪 | 200 |
| 1 | 无 Token 访问 `/api/users/1` | 401 |
| 1 | 仅伪造 `X-User-Role`（无 Token） | 401 |
| 2 | `demo-user` 正确口令登录 | 200 且 Token 为三段 |
| 2 | 错误口令登录 | 401，响应无 Token |
| 3 | 带 Token 访问 `/api/users/1` | 200 |
| 4 | USER 访问 `/api/admin/users` | 403 |
| 4 | USER + 伪造 ADMIN 头访问管理接口 | 403 |
| 4 | ADMIN 访问管理接口 | 200 |
| 5 | 篡改签名 / 篡改 payload | 401 |

退出码：`0` 全部通过，`1` 有失败项，`2` 前置条件不满足（网关没起、缺 curl）。

---

## 5. 截图清单

见 **[`screenshots/README.md`](screenshots/README.md)**，包含每张图的命令、
必须出现的文字和保存文件名。建议顺序：

1. 网关健康与注册服务
2. 未登录 401 → 登录 200
3. 带 Token 访问 200
4. USER 越权 403（含伪造头）
5. ADMIN 访问 200
6. `auth-test.sh` 全部通过的汇总
7. 数据库里只有 BCrypt 哈希（可选，但很有说服力）
8. Nacos 控制台里的 `gateway-service`（可选）

---

## 6. 已知边界

- **HS256 对称密钥**：验签方也能签发。适合"单签发方"的课程项目；多签发方应上 RS256。
- **无刷新/吊销**：Token 到期前一直有效，没有 refresh token、没有黑名单。
  若要做"退出即失效"，需引入 Redis 黑名单或短 TTL + 刷新机制。
- **没有 HTTPS**：口令与 Token 在本机明文传输。容器间是 Docker 内网，
  但生产必须在入口做 TLS 终止。
- **仅接口级授权**：只区分"能否访问某类接口"，没有做到"只能看自己的记录"这种数据级授权。
- **开发期端口暴露**：见 §3.4 的说明。
