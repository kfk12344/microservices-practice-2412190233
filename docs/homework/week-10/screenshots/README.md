# 第 10 周 · 截图指南

> 目标：为「课内实践（15 分）」和「大作业实践（35 分）」采集可复现的运行证据。
> 图片保存到本目录，命名与 `docs/homework/week-10/index.md` §6 的清单一致。

---

## 一、准备

```bash
# 仓库根目录
cp .env.example .env        # Windows: Copy-Item .env.example .env
# 编辑 .env：填入真实口令，并把 Nacos 管理员密码与控制台初始化时设置的一致

docker compose up -d --build
bash scripts/config-test.sh
# Windows: powershell -ExecutionPolicy Bypass -File scripts\config-test.ps1
```

常用地址：

| 用途 | 地址 |
|---|---|
| Nacos 控制台 | <http://localhost:18080/> |
| Nacos API（3.x） | <http://localhost:8848/nacos/v3/client/cs/config> |
| meal 服务探针 | <http://localhost:8083/api/config-probe> |
| user 服务探针 | <http://localhost:8081/api/config-probe> |
| food 服务探针 | <http://localhost:8082/api/config-probe> |
| 网关探针 | <http://localhost:8080/api/config-probe> |

> 课内实践只需 **01 或 04** 中任选一张作为「运行结果截图」，
> 再加一张代码截图即可拿满 15 分。大作业建议主编号 01-07 全拍。
>
> **当前实际保留 5 张 PNG**：`01-nacos-service-list.png`（Nacos 服务列表）、
> `02-meal-probe-dev.png`、`03-probe-refresh-before.png`、
> `04-probe-refresh-after.png`、`08-security-check.png`。
> 代码截图（07）、配置列表页、test 环境（05）、脚本输出（06）可按下面步骤补拍。

---

## 二、逐张拍摄

### 01 · Nacos 配置列表（大作业 1 / 课内运行结果）

打开 <http://localhost:18080/> → 配置管理 → 配置列表 → 选择分组 `PERSONAL_GROUP`。
**必须出现**：`user-service-dev.yaml`、`food-service-dev.yaml`、`meal-service-dev.yaml`、
`gateway-service-dev.yaml`（以及对应的 `-test.yaml`）。

保存为 `01-nacos-config-list.png`。

### 02 · meal-service 读取 dev 配置（大作业 1）

浏览器打开 <http://localhost:8083/api/config-probe>。
**必须出现**：`"configSource":"nacos"`、`"dataId":"meal-service-dev.yaml"`、
`"environment":"dev"`、`"maxKcalPerMeal":1500`。

保存为 `02-meal-probe-dev.png`。

### 03 · 刷新前阈值（大作业 3）

保持 02 的页面不动，先拍一张 1500 的基线。

保存为 `03-probe-refresh-before.png`。

### 04 · 动态刷新后阈值（大作业 3 / 课内运行结果）

**不要重启任何容器**，在 Nacos 控制台编辑 `meal-service-dev.yaml`，
把 `max-kcal-per-meal` 从 `1500` 改成 `1200` 并发布；等待约 3 秒后刷新探针页面。

**必须出现**：页面仍是同一个服务、容器未重启，`maxKcalPerMeal` 已变成 `1200`。
若想更有说服力，可在同一截图里带上 Nacos 编辑页与探针页两个窗口。

保存为 `04-probe-refresh-after.png`。

> 拍完记得把配置改回 `1500`，或重新运行 `bash scripts/config-test.sh` 让脚本恢复。

### 05 · 切换到 test 环境（大作业 2）

```bash
APP_ENV=test docker compose up -d --force-recreate
APP_ENV_OVERRIDE=test bash scripts/config-test.sh
curl http://localhost:8083/api/config-probe
```

**必须出现**：`"environment":"test"`、`"dataId":"meal-service-test.yaml"`、
`"maxKcalPerMeal":1200`（与 dev 的 1500 不同）。

保存为 `05-probe-test-env.png`。

> 拍完执行 `APP_ENV=dev docker compose up -d --force-recreate` 恢复默认环境。

### 06 · 自动验证输出（大作业 3/5）

在终端运行 `bash scripts/config-test.sh`，滚到末尾汇总。
**必须出现**：`验证结束：PASS=... FAIL=0`，以及
`未重启/未重新打包，阈值自动变为 ...（动态刷新生效）`。

保存为 `06-config-test-output.png`。

### 辅助 · 另外三个服务的探针

`probe-user-service.png`、`probe-food-service.png`、`probe-gateway-service.png`
分别对应 user-service(8081)、food-service(8082)、gateway-service(8080)
的 `GET /api/config-probe`，三者均显示 `configSource=nacos`，
与 `02-meal-probe-dev.png` 合起来就是「多服务集中管理」的完整证据。

### 07 · 代码截图（课内实践 6 分）

在编辑器打开
`services/meal-service/src/main/resources/application.yml`，
滚动到文件末尾的 `spring.config.import` 段，让**文件名**和下面内容同时可见：

```yaml
spring:
  config:
    activate:
      on-profile: "!test"
    import: "optional:nacos:${spring.application.name}-${APP_ENV:dev}.yaml?group=${NACOS_GROUP:PERSONAL_GROUP}&refreshEnabled=true"
```

备选：打开 `MealRules.java`，让 `@RefreshScope` 与 `@Value("${app.meal.max-kcal-per-meal:1500}")` 同屏。

保存为 `07-code-nacos-config.png`，作为课内实践的「代码截图」上传。

---

## 三、自查清单

- [ ] 每张图都能看到浏览器地址栏或编辑器文件名（证明不是凭空截图）
- [ ] Nacos 图里有 `PERSONAL_GROUP` 与完整 Data ID
- [ ] 探针图里 `configSource` 明确为 `nacos`
- [ ] 刷新前后两张图阈值不同，且期间没有重启容器
- [ ] test 环境图的 `dataId` 后缀是 `-test.yaml`
- [ ] 所有图片都不包含真实口令、Token 或密钥
- [ ] 课内实践已单独准备：1 张代码截图 + 1 张运行截图
