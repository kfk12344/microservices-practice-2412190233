package com.zjgsu.hdt.food.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第 10 周：配置中心观察端点（非敏感字段）。
 *
 * <p>与 user / meal / gateway 保持同一路径，便于脚本一次性收集多服务证据。
 */
@RestController
@RequestMapping("/api/config-probe")
public class ConfigProbeController {

    private final String environment;
    private final String configSource;
    private final String group;
    private final String logLevel;

    public ConfigProbeController(
            @Value("${app.environment:dev}") String environment,
            @Value("${app.config.source:local}") String configSource,
            @Value("${spring.cloud.nacos.config.group:PERSONAL_GROUP}") String group,
            @Value("${logging.level.com.zjgsu.hdt.food:INFO}") String logLevel) {
        this.environment = environment;
        this.configSource = configSource;
        this.group = group;
        this.logLevel = logLevel;
    }

    @GetMapping
    public Map<String, Object> probe() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "food-service");
        body.put("environment", environment);
        body.put("configSource", configSource);
        body.put("dataId", "food-service-" + environment + ".yaml");
        body.put("group", group);
        body.put("logLevel", logLevel);
        return body;
    }
}
