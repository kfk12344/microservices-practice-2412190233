package com.zjgsu.hdt.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行状态验证接口（Week 03）。
 *
 * <p>存在的意义只有一个：用一条最简单的 GET 请求证明 Spring 容器、Web MVC 与配置都已经就绪。
 * 它不承载任何业务逻辑，业务接口从 Week 04 起放在 {@code /api/meals}、{@code /api/foods} 下。
 */
@RestController
@RequestMapping("/api")
public class HelloController {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String applicationName;

    public HelloController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * GET /api/hello —— 返回项目名称与问候消息，用于确认服务已启动。
     *
     * @return 包含项目名、问候语和服务器时间的 JSON
     */
    @GetMapping("/hello")
    public Map<String, Object> hello() {
        // 用 LinkedHashMap 固定字段顺序，截图和 curl 输出更稳定、便于比对
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", applicationName);
        body.put("message", "个人健康饮食记录管理系统已启动");
        body.put("timestamp", LocalDateTime.now().format(TIMESTAMP));
        return body;
    }
}
