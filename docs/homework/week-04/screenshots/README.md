# 截图目录说明（Week 04）

本周作业的四个小题都需要上传截图（每小题 1–5 张），图片放在本目录下。

`index.md` 中已按下列文件名引用了 8 张图。**文件名必须与下表完全一致**，
否则 GitHub 页面上的图片会显示不出来（占位为破图）。

| 文件名 | 拍摄内容 | 对应小题 |
| :--- | :--- | :--- |
| `01-project-proposal.png` | `docs/project-proposal.md` 中本周新增的业务流程、实体关系与业务规则三节 | 1 业务建模 |
| `02-app-start.png` | `./mvnw spring-boot:run` 启动日志，能看到端口 8080 与 `Tomcat started` | 2 单体应用实现 |
| `03-post-meal-201.png` | curl 新增一餐的请求与 **201** 响应，响应体里有 `totalCalories: 431.5` | 2 单体应用实现 |
| `04-business-rule-422.png` | 提交 2000g 米饭返回 **422**，`details` 里能看到规则编号 R3 | 2 单体应用实现 |
| `05-mysql-tables.png` | 三张表 + `meal_item` 的字段与外键/唯一约束（`SHOW TABLES;` 与 `DESC meal_item;`） | 3 数据库持久化 |
| `06-idempotent-scripts.png` | 连续两次执行 `schema.sql` 与 `data.sql`，用行数证明脚本幂等 | 3 数据库持久化 |
| `07-mvnw-test.png` | `./mvnw test` 结尾输出，能看到 `Tests run: 75, Failures: 0, Errors: 0` 与 `BUILD SUCCESS` | 4 测试与验证 |
| `08-invalid-parameter-type-400.png` | `/api/meals/abc` 返回 **400** 且 `code` 为 `INVALID_PARAMETER_TYPE` | 4 测试与验证 |

## 拍摄前准备

### 路线 A：本机有 MySQL（最推荐）

```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS health_diet DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
cd monolith
mysql -u root -p health_diet < src/main/resources/schema.sql
mysql -u root -p health_diet < src/main/resources/data.sql

# 这张输出就是 05 号截图
mysql -u root -p health_diet -e "SHOW TABLES; DESC meal_item;"
```

### 路线 B：本机没有 MySQL，但能用 Docker（贴近生产）

用容器跑一个真 MySQL 8.4，命令见
[`docs/database.md` 第 9.4 节](../../../database.md#94-另一种选择用-docker-起一个真-mysql)。

### 路线 C：既没有 MySQL 也不能用 Docker（本机当前情况）

截图 05、06 用仓库自带的验证脚本完成，**一条命令**即可：

```powershell
cd <仓库根目录>
python 验证脚本/verify_schema_h2.py
```

它会用 H2 的命令行工具执行**同一份** `schema.sql` 与 `data.sql`，并打印：

- 三张表清单 → 拍进 **05**
- `meal_item` 的字段与类型 → 拍进 **05**
- 全部 10 条约束（含两个外键与唯一约束）→ 拍进 **05**
- 各表行数，以及**再执行一次后行数不变** → 拍进 **06**

完整输出也已存档，可直接对着 `验证脚本/验证输出-数据库.txt` 截图。

> 这条路线只换执行引擎，**建表与数据脚本没有变**，所以能证明 DDL 与数据确实按设计生效；
> 但它不等价于 MySQL 下的真实联调，有条件时应优先用路线 A。

### 与应用接口相关的截图（02、03、04、07、08）

这几张不需要 MySQL：

```powershell
cd monolith
$env:SPRING_PROFILES_ACTIVE = "demo"    # 用 H2 内存库
.\mvnw spring-boot:run
```

等出现 `Tomcat started on port 8080` 后另开终端执行 curl。
截图 07 则直接运行 `.\mvnw test`（测试自带 H2，不需要启动应用）。

## 拍摄建议

- 截图里尽量带上**命令本身**，不要只截结果。
- `./mvnw test` 建议滚到输出末尾，确保 `Tests run: 75, Failures: 0, Errors: 0` 与 `BUILD SUCCESS` 都入镜。
- 截图 04、08 是"错误响应"，注意把 HTTP 状态码（422 / 400）一起截进来，这是得分点。
- 完整响应文本也有一份可复现的存档：`验证脚本/验证输出-接口响应.txt`（15 条请求），
  可在应用运行时用 `python 验证脚本/collect_api_transcript.py` 重新生成。
- 数据库输出同样有存档：`验证脚本/验证输出-数据库.txt`。
