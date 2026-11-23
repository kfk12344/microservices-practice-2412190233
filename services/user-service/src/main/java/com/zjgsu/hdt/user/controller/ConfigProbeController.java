package com.zjgsu.hdt.user.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第 10 周：配置中心观察端点（非敏感字段）。
 *
 * <p>多服务接入证据：user / food / meal / gateway 四个服务都提供同一路径，
 * 通过 {@code GET /api/config-probe} 可以看到各自读到的 Data ID、
 * Group、环境与配置来源。密钥类字段一律不返回。
 */
@RestController
@RequestMapping("/api/config-probe")
public class ConfigProbeController {

    private final String environment;
    private final String configSource;
    private final String group;
    private final long jwtTtlSeconds;

    public ConfigProbeController(
            @Value("${app.environment:dev}") String environment,
            @Value("${app.config.source:local}") String configSource,
            @Value("${spring.cloud.nacos.config.group:PERSONAL_GROUP}") String group,
            @Value("${app.jwt.ttl-seconds:3600}") long jwtTtlSeconds) {
        this.environment = environment;
        this.configSource = configSource;
        this.group = group;
        this.jwtTtlSeconds = jwtTtlSeconds;
    }

    @GetMapping
    public Map<String, Object> probe() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "user-service");
        body.put("environment", environment);
        body.put("configSource", configSource);
        body.put("dataId", "user-service-" + environment + ".yaml");
        body.put("group", group);
        // 只暴露有效期，不暴露 JWT_SECRET
        body.put("jwtTtlSeconds", jwtTtlSeconds);
        return body;
    }
}
