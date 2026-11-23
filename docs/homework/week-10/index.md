# 第 10 周 · 配置中心与配置管理（课内实践 15 分 + 大作业实践 35 分）

> 相关代码：`services/{user,food,meal,gateway}-service`（四个服务接入 Nacos Config）、`compose.yaml`、`scripts/config-test.sh`
> 配置清单：`docs/configuration.md`；截图目录：`docs/homework/week-10/screenshots/`（拍法见同目录 `README.md`）
> 公开仓库：<https://github.com/kfk12344/microservices-practice-2412190233.git>

---

## 0. 本周交付清单

| 要求 | 交付物 | 状态 |
|---|---|---|
| 多服务配置集中管理 | 4 个服务均引入 Nacos Config Starter 并通过 `spring.config.import` 导入配置 | 已完成 |
| dev / test 环境隔离 | 8 个 Data ID（`服务名-{dev\|test}.yaml`），`APP_ENV` 切换 | 已完成 |
| 动态配置验证 | `@RefreshScope` 的 `MealRules` + `scripts/config-test.sh` 自动改值/观察/恢复 | 已完成 |
| 不泄露密钥 | `.env.example` 占位符、真实口令只在 `.env`、Nacos 仅存非敏感参数 | 已完成 |
| 可观察证据 | 4 个服务统一的 `GET /api/config-probe`，截图见 `screenshots/` | 已完成 |
| 课内实践（15 分） | 1 张代码截图 + 1 张 Nacos 控制台/刷新结果截图 | 见本文 §5 |

---

## 1. 配置中心（大作业 · 30%）

### 1.1 实现思路

四个微服务统一加入 `spring-cloud-starter-alibaba-nacos-config`（版本由
Spring Cloud Alibaba 2025.1.0.0 BOM 管理，不写死版本号），并在各自的
`application.yml` 末尾用 2025.x 唯一支持的方式导入配置：

```yaml
spring:
  config:
    activate:
      on-profile: "!test"
    import: "optional:nacos:${spring.application.name}-${APP_ENV:dev}.yaml?group=${NACOS_GROUP:PERSONAL_GROUP}&refreshEnabled=true"
```

- `optional:`：Nacos 尚未就绪时服务仍能启动，用本地兜底值；
- `refreshEnabled=true`：开启配置变更监听；
- `!test`：单元测试不连 Nacos，`mvn test` 不需要外部依赖；
- 迁移进 Nacos 的都是**非敏感**参数：日志级别、JWT 有效期、单餐热量上限、创建开关、配置来源标记。

### 1.2 运行命令

```bash
# 准备环境变量（仅首次）
cp .env.example .env
# 编辑 .env 填入真实口令（不要提交）

# 启动整套系统
docker compose up -d --build

# 一键发布配置并验证接入
bash scripts/config-test.sh
# Windows: powershell -ExecutionPolicy Bypass -File scripts\config-test.ps1
```

### 1.3 结果

四个服务都能回报 `configSource=nacos`，且 Data ID 与各自服务名一致：

```bash
curl http://localhost:8081/api/config-probe   # user-service
curl http://localhost:8082/api/config-probe   # food-service
curl http://localhost:8083/api/config-probe   # meal-service
curl http://localhost:8080/api/config-probe   # gateway-service
```

```json
{"service":"meal-service","environment":"dev","configSource":"nacos",
 "dataId":"meal-service-dev.yaml","group":"PERSONAL_GROUP",
 "maxKcalPerMeal":1500,"mealCreateEnabled":true}
```

### 1.4 作业系统填写文本（可直接复制，≥ 20 字符）

> 四个微服务已统一接入 Nacos Config，通过 spring.config.import 导入各自 Data ID，日志级别、JWT 有效期与业务阈值等非敏感配置实现集中管理；可通过 /api/config-probe 观察到 configSource 为 nacos，证明配置确实来自配置中心。

---

## 2. 多环境配置（大作业 · 20%）

### 2.1 实现思路

环境由 `compose.yaml` 注入的 `APP_ENV` 决定，默认 `dev`，可设为 `test`：

| 环境 | Data ID 后缀 | 用途 | 典型取值差异 |
|---|---|---|---|
| `dev` | `-dev.yaml` | 本地开发 | 单餐上限 1500 kcal |
| `test` | `-test.yaml` | 自动测试 / 演示 | 单餐上限 1200 kcal |

两个环境的配置放在同一个 Group（`PERSONAL_GROUP`）、同一个 Namespace（`public`），
靠 **Data ID 后缀**隔离；服务启动后 `/api/config-probe` 会回报当前
`environment` 与实际读取的 `dataId`，可直接核对。完整的 8 个 Data ID 清单
与用途见 **[docs/configuration.md](../../configuration.md)**。

### 2.2 运行命令

```bash
# dev 环境（默认）
docker compose up -d --build
bash scripts/config-test.sh

# 切到 test 环境
APP_ENV=test docker compose up -d --force-recreate
APP_ENV_OVERRIDE=test bash scripts/config-test.sh
```

### 2.3 结果

`APP_ENV=test` 时四个服务的探针均返回
`"environment":"test"` 与 `"dataId":"<服务名>-test.yaml"`；
其中 meal-service 的阈值变为 1200，与 dev 的 1500 形成可观察差异。

### 2.4 作业系统填写文本（可直接复制，≥ 50 字符）

> 多环境配置采用「服务名 + 环境后缀」的 Data ID 命名方式，dev 与 test 各有一套独立配置，统一放在 PERSONAL_GROUP 分组下。compose.yaml 通过 APP_ENV 注入环境标识，服务启动时读取对应的 -dev.yaml 或 -test.yaml。实测切换到 test 后，四个服务的 /api/config-probe 均返回 environment=test，meal-service 的单餐热量上限从 dev 的 1500 变为 1200，说明环境边界清楚、互不干扰。Data ID、Group 与用途已完整记录在 docs/configuration.md。

---

## 3. 动态配置验证（大作业 · 20%）

### 3.1 实现思路

把业务规则从硬编码常量抽成独立 Bean：

```java
@Component
@RefreshScope
public class MealRules {
    @Value("${app.meal.max-kcal-per-meal:1500}")
    private int maxKcalPerMeal;
}
```

`MealRecordService` 注入 `MealRules`，规则判断使用 `mealRules.maxKcalPerMeal()`。
`@RefreshScope` 让 Nacos 配置变更时该 Bean 被重建，`@Value` 随之拿到新值；
`MealRecordService` 与控制器不需要重启或重新打包。
`create-enabled` 业务开关同理，可用于临时关闭写接口。

### 3.2 运行命令

```bash
# 完整流程：发布配置 → 观察 → 改值 → 等待刷新 → 恢复
bash scripts/config-test.sh

# 手工验证（脚本之外）
curl http://localhost:8083/api/config-probe
# 在 Nacos 控制台把 meal-service-dev.yaml 的 max-kcal-per-meal 改成 1200
sleep 3
curl http://localhost:8083/api/config-probe   # 应显示 1200
```

### 3.3 结果

`scripts/config-test.sh` 的「动态配置验证」段落输出：

```
[INFO] 刷新前 maxKcalPerMeal=1500
[PASS] 未重启/未重新打包，阈值自动变为 1200（动态刷新生效）
[PASS] 已恢复原阈值 1500
```

脚本无论成功与否都会把配置恢复原值，不会留下脏数据。

### 3.4 作业系统填写文本（可直接复制，≥ 20 字符）

> 将单餐热量上限抽成带 @RefreshScope 的 MealRules Bean，通过脚本修改 Nacos 配置后，/api/config-probe 在数秒内由 1500 变为 1200，全程未重启容器也未重新打包，随后脚本自动恢复原值，动态刷新生效证据完整。

---

## 4. 安全配置（大作业 · 15%）

### 4.1 实现思路

- **不提交真实密钥**：`.gitignore` 排除 `.env` 与 `.env.*`，仅白名单 `.env.example`；
- **示例占位**：`.env.example` 中所有口令均为 `change-me-*` 占位符，并写明生成方法；
- **配置中心只放非敏感参数**：数据库口令、`JWT_SECRET`、`GATEWAY_SECRET`、
  Nacos 管理员口令继续从 `.env` 注入，Nacos 配置里没有任何密钥字段；
- **探针不回显密钥**：`ConfigProbeController` 只返回 TTL、日志级别等非敏感项；
- **README 说明**：见主 README 的「6.10 配置中心（Week 10 · Nacos Config）」。

### 4.2 运行命令

```bash
git check-ignore -v .env
git grep -n -E 'JWT_SECRET=|DB_PASSWORD=|NACOS_PASSWORD=' -- . ':!.env.example'
```

第一条应显示 `.env` 被忽略；第二条不应出现任何真实值。

### 4.3 结果

仓库中只有 `.env.example` 模板；所有服务探针响应均不含密钥字段。

### 4.4 作业系统填写文本（可直接复制，≥ 50 字符）

> 安全配置遵循「密钥不落配置中心、不落仓库」的原则：.env 被 .gitignore 排除，仓库只保留全部为 change-me 占位符的 .env.example，README 中说明了本地复制与填写方法。Nacos 的 8 个 Data ID 只包含日志级别、业务阈值和 JWT 有效期等非敏感参数，数据库口令与签名密钥仍由 compose 从 .env 注入。探针端点也刻意不回显任何密钥，实测 git check-ignore 可确认 .env 未被跟踪。

---

## 5. 课内实践：配置中心（15 分）

> 提交说明：课内实践不要求 GitHub 提交、课后文档或测试报告，直接在作业系统上传
> **1 张代码/配置截图（6 分）** 与 **1 张 Nacos 控制台或刷新结果截图（9 分）**。

### 5.1 代码截图（6 分）

建议截 `services/meal-service/src/main/resources/application.yml` 的末尾导入段，
屏幕上要同时看到文件名、Data ID 命名规则和 `refreshEnabled=true`：

```yaml
spring:
  config:
    activate:
      on-profile: "!test"
    import: "optional:nacos:${spring.application.name}-${APP_ENV:dev}.yaml?group=${NACOS_GROUP:PERSONAL_GROUP}&refreshEnabled=true"
```

备选：截 `MealRules.java`（能看到 `@RefreshScope` 与 `@Value`）。

### 5.2 运行结果截图（9 分）

二选一即可，推荐同时拍两张提高辨识度：

1. Nacos 控制台 → 配置管理 → 配置列表，筛选 `PERSONAL_GROUP`，
   能看到 `meal-service-dev.yaml` 等 Data ID；
2. 浏览器打开 <http://localhost:8083/api/config-probe>，
   显示 `"configSource":"nacos"` 与 `"dataId":"meal-service-dev.yaml"`。

拍摄命令与摆放顺序见 `docs/homework/week-10/screenshots/README.md`。

---

## 6. 截图清单

| 序号 | 文件 | 证明内容 |
|---|---|---|
| 01 | `01-nacos-service-list.png` | Nacos 服务列表：4 个服务均在 `PERSONAL_GROUP` |
| 02 | `02-meal-probe-dev.png` | meal-service 从 Nacos 读取 dev 配置（1500） |
| 03 | `03-probe-refresh-before.png` | 刷新前阈值 1500 |
| 04 | `04-probe-refresh-after.png` | 刷新后阈值 1200（服务未重启） |
| 08 | `08-security-check.png` | `.env` 被 gitignore、无密钥泄露的终端验证 |

> 截图保存在 `docs/homework/week-10/screenshots/`。上述 5 张为当前实际保留的运行证据，
> 作业系统中按题目分别上传即可。
>
> **课内实践（15 分）**：运行结果截图用 `04-probe-refresh-after.png`；
> 代码截图需按 `screenshots/README.md` §07 现场补拍（截 `application.yml` 末尾导入段）。
> 大作业第 2、3 题的 test 环境与自动脚本输出截图也可按 README 步骤补拍。

---

## 7. 已知边界

- `optional:nacos:` 表示 Nacos 不可达时服务用本地兜底值启动；
  要证明配置真正来自 Nacos，必须看 `configSource=nacos`，不能只看服务能启动。
- `APP_ENV` 改完需要重建业务容器才会重新读取对应 Data ID：
  `APP_ENV=test docker compose up -d --force-recreate`。
- 动态刷新只对 `@RefreshScope` Bean 中的 `@Value` 生效；
  普通单例 Bean 及注解里的常量（如 `@Max(5000)`）不受影响。
- Nacos 控制台首次启动需要初始化管理员密码，并把同一密码写入 `.env` 的 `NACOS_PASSWORD`。
- 本次已在 Docker 环境中实际运行并采集全部截图（见 `screenshots/`，共 10 张 PNG）；
  如需重拍，先执行 `docker compose up -d --build` 与 `bash scripts/config-test.sh`。
