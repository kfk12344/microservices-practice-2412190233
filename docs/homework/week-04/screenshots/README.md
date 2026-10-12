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
| `05-mysql-tables.png` | mysql 客户端里 `SHOW TABLES;` 与 `DESC meal_item;` 的结果，能看到两个外键与唯一约束 | 3 数据库持久化 |
| `06-idempotent-scripts.png` | 连续两次执行 `schema.sql` 与 `data.sql`，并用 `SELECT COUNT(*)` 证明行数未增加 | 3 数据库持久化 |
| `07-mvnw-test.png` | `./mvnw test` 结尾输出，能看到 `Tests run: 75, Failures: 0, Errors: 0` 与 `BUILD SUCCESS` | 4 测试与验证 |
| `08-invalid-parameter-type-400.png` | `/api/meals/abc` 返回 **400** 且 `code` 为 `INVALID_PARAMETER_TYPE` | 4 测试与验证 |

## 拍摄前准备

**若本机已装 MySQL**（截图 05、06 需要）：先建库并导入脚本。

```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS health_diet DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
cd monolith
mysql -u root -p health_diet < src/main/resources/schema.sql
mysql -u root -p health_diet < src/main/resources/data.sql
```

**若本机暂无 MySQL**：截图 02、03、04、07、08 都可以用演示配置完成，
只有 05、06 确实需要 MySQL。

```powershell
cd monolith
$env:SPRING_PROFILES_ACTIVE = "demo"
./mvnw spring-boot:run
```

## 拍摄建议

- 截图里尽量带上**命令本身**，不要只截结果。
- `./mvnw test` 建议滚到输出末尾，确保 `Tests run: 75, Failures: 0, Errors: 0` 与 `BUILD SUCCESS` 都入镜。
- 截图 04、08 是"错误响应"，注意把 HTTP 状态码（422 / 400）一起截进来，这是得分点。
- 完整响应文本也有一份可复现的存档：`验证脚本/验证输出-接口响应.txt`（15 条请求），
  可在应用运行时用 `python 验证脚本/collect_api_transcript.py` 重新生成。
