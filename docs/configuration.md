# 配置中心说明（第 10 周 · Nacos Config）

> 适用版本：Spring Boot 4.0.8 / Spring Cloud 2025.1.0 / Spring Cloud Alibaba 2025.1.0.0
> 相关代码：四个微服务的 `application.yml` + `spring-cloud-starter-alibaba-nacos-config`
> 自动验证：`bash scripts/config-test.sh`（Windows：`scripts/config-test.ps1`）

---

## 1. 为什么这样设计

1. **只迁移非敏感配置**：日志级别、Feign 超时、业务阈值、JWT 有效期这类参数适合集中管理；
   数据库口令、`JWT_SECRET`、`GATEWAY_SECRET`、Nacos 管理员口令一律留在 `.env`，
   既不写入 Nacos 配置，也不写入仓库。
2. **按服务 + 环境隔离**：每个服务在每个环境有独立 Data ID，
   避免"改一个参数影响所有服务"，也让 dev/test 的边界可以直接在 Nacos 控制台看到。
3. **配置与代码解耦**：2025.x 已移除 bootstrap 机制，统一用
   `spring.config.import=nacos:<dataId>?group=<group>&refreshEnabled=true` 导入。
   `optional:` 前缀让 Nacos 暂时不可达时服务仍能启动（使用本地兜底值）；
   `test` profile 下不导入 Nacos，并在 `application-test.yml` 中关闭
   `spring.cloud.nacos.config.import-check`（否则 Config Starter 会因缺少 nacos 导入项拒绝启动）。
4. **可观察**：每个服务提供 `GET /api/config-probe`，
   返回 `environment` / `configSource` / `dataId` / `group` 及该服务关键参数，
   只含非敏感字段，可直接截图作为证据。

---

## 2. Data ID、Group 与用途清单

**Namespace**：默认 `public`（`NACOS_NAMESPACE` 留空）。自定义时填命名空间 **ID**，不是名称。
**Group**：`PERSONAL_GROUP`（`NACOS_GROUP`），与课堂示例分组隔离。

| # | Data ID | Group | Namespace | 环境 | 用途 |
|---|---|---|---|---|---|
| 1 | `user-service-dev.yaml` | `PERSONAL_GROUP` | `public` | dev（本地开发） | JWT 有效期、日志级别、配置来源标记 |
| 2 | `user-service-test.yaml` | `PERSONAL_GROUP` | `public` | test（自动测试/演示） | 同上，测试环境取值可不同 |
| 3 | `food-service-dev.yaml` | `PERSONAL_GROUP` | `public` | dev | 日志级别、配置来源标记 |
| 4 | `food-service-test.yaml` | `PERSONAL_GROUP` | `public` | test | 同上 |
| 5 | `meal-service-dev.yaml` | `PERSONAL_GROUP` | `public` | dev | **单餐热量上限**、创建开关、日志级别、配置来源标记 |
| 6 | `meal-service-test.yaml` | `PERSONAL_GROUP` | `public` | test | 同上（默认阈值 1200，便于演示差异） |
| 7 | `gateway-service-dev.yaml` | `PERSONAL_GROUP` | `public` | dev | 日志级别、配置来源标记 |
| 8 | `gateway-service-test.yaml` | `PERSONAL_GROUP` | `public` | test | 同上 |

命名规则：

```
<spring.application.name>-<APP_ENV>.yaml
```

其中 `APP_ENV` 由 `compose.yaml` 注入，默认 `dev`；设为 `test` 时服务读取 `-test.yaml`。
四个服务的导入语句形如：

```yaml
spring:
  config:
    activate:
      on-profile: "!test"     # 单元测试不连 Nacos
    import: "optional:nacos:${spring.application.name}-${APP_ENV:dev}.yaml?group=${NACOS_GROUP:PERSONAL_GROUP}&refreshEnabled=true"
```

---

## 3. 配置内容示例

### 3.1 `meal-service-dev.yaml`（动态刷新观察点）

```yaml
# Nacos Config · meal-service · dev
# 只放非敏感业务参数；口令与密钥仍由 .env 注入
app:
  environment: dev
  config:
    source: nacos
  meal:
    max-kcal-per-meal: 1500
    create-enabled: true
logging:
  level:
    com.zjgsu.hdt.meal: INFO
```

`meal-service-test.yaml` 结构相同，只是 `environment: test`、`max-kcal-per-meal: 1200`。
`user-service-*.yaml` 额外包含 `app.jwt.ttl-seconds: 3600`（**只放有效期，不放密钥**）；
`food-service-*.yaml` 与 `gateway-service-*.yaml` 主要是日志级别与来源标记。

### 3.2 本地兜底值

每个服务 `application.yml` 都保留了同名键的本地默认值（例如
`app.meal.max-kcal-per-meal: ${MEAL_MAX_KCAL_PER_MEAL:1500}`）。
当 Nacos 中没有对应配置或暂时不可达时，服务使用这些默认值，`configSource=local`；
配置存在时 Nacos 的值优先级更高，`configSource=nacos`。

---

## 4. 安全边界

| 类别 | 位置 | 说明 |
|---|---|---|
| 数据库口令 | `.env` → 容器环境变量 | 不进 Nacos、不进 Git；`.gitignore` 已排除 `.env` |
| JWT 签名密钥 | `.env` → `JWT_SECRET` | 只存在于 user-service / gateway-service 运行时 |
| 网关内部口令 | `.env` → `GATEWAY_SECRET` | 同上 |
| Nacos 管理员口令 | `.env` → `NACOS_PASSWORD` | 客户端登录用，不写入配置内容 |
| Nacos 服务端 Token | `.env` → `NACOS_AUTH_TOKEN` | 仅 Nacos 容器使用 |
| 可集中管理参数 | Nacos Data ID | 日志级别、业务阈值、超时、JWT 有效期等 |

`.env.example` 只提供 `change-me-*` 占位符；提交仓库前请确认：

```bash
git check-ignore -v .env          # 必须显示被 .gitignore 排除
git grep -n -E 'JWT_SECRET=|DB_PASSWORD=|NACOS_PASSWORD=' -- . ':!.env.example'
# 结果中不应出现任何真实值
```

---

## 5. 动态刷新机制

```
Nacos 控制台 / Open API 修改配置
        ↓ 长轮询推送
Spring Cloud Alibaba 刷新 Environment
        ↓
@RefreshScope 的 MealRules Bean 被重建
        ↓
MealRecordService 读到新的 maxKcalPerMeal / createEnabled
        ↓
GET /api/config-probe 立即反映新值（无需重新打包、无需重启）
```

关键代码：

- `services/meal-service/.../config/MealRules.java`：`@RefreshScope` + `@Value`
- `services/meal-service/.../service/MealRecordService.java`：注入 `MealRules`，
  单餐热量上限从 `mealRules.maxKcalPerMeal()` 读取，原来的硬编码常量已移除
- `scripts/config-test.sh`：自动把阈值从 1500 改成 1200、观察端点变化、再恢复

> 注意：`@Value` 只有在 **`@RefreshScope` Bean** 里才会跟着刷新；
> 普通单例 Bean 需要重启。这正是本次把规则抽成独立 Bean 的原因。

---

## 6. 运行与验证

```bash
# 1) 准备环境变量（首次）
cp .env.example .env
# 编辑 .env，填入真实口令

# 2) 启动
docker compose up -d --build

# 3) 一键验证（发布配置 + 多服务读取 + 动态刷新 + 恢复）
bash scripts/config-test.sh

# 4) 切到 test 环境后重新验证
APP_ENV=test docker compose up -d --force-recreate
APP_ENV_OVERRIDE=test bash scripts/config-test.sh
```

单点观察（不跑完整脚本）：

```bash
curl http://localhost:8083/api/config-probe
curl http://localhost:8081/api/config-probe
curl http://localhost:8082/api/config-probe
curl http://localhost:8080/api/config-probe
```

Nacos 控制台：<http://localhost:18080/>（配置管理 → 配置列表 → 切换 `PERSONAL_GROUP`）。
