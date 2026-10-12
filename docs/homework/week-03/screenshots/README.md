# 截图目录说明（Week 03）

本周作业需要在作业系统上传 1–5 张截图，图片放在本目录下。

`index.md` 中已按下列文件名引用了 5 张图。**文件名必须与下表完全一致**，
否则 GitHub 页面上的图片会显示不出来（占位为破图）。

| 文件名 | 拍摄内容 | 对应作业要求 |
| :--- | :--- | :--- |
| `01-project-structure.png` | IDE 中 `monolith/` 的工程结构，能看到 `pom.xml`、启动类 `HealthDietTrackerApplication`、`application.yml` | 工程创建（小题 2） |
| `02-mvnw-test.png` | 在 `monolith/` 执行 `./mvnw test` 的结尾输出，能看到 `BUILD SUCCESS` | 启动测试（小题 3） |
| `03-app-started.png` | `./mvnw spring-boot:run` 的启动日志，能看到 `Tomcat started on port 8080` | 运行验证（小题 2） |
| `04-api-hello.png` | 访问 `http://localhost:8080/api/hello` 的返回 JSON | 运行验证（小题 2） |
| `05-actuator-health.png` | 访问 `http://localhost:8080/actuator/health` 返回 `{"status":"UP"}` | 运行验证（小题 2） |

拍摄建议：

- 截图里尽量带上**终端命令本身**，不要只截结果，这样"命令—结果"的对应关系一目了然。
- 分辨率不必很高，但文字要清晰可读；建议只截相关区域，不要整屏缩得很小。
- 若使用 Windows PowerShell，`./mvnw test` 也可以写成 `.\mvnw test`。
- 应用未启动时截图访问接口会失败，先确认 `Tomcat started on port 8080` 再截图。
