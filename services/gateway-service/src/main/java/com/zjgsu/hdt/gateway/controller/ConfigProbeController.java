package com.zjgsu.hdt.gateway.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第 10 周：配置中心观察端点（非敏感字段）。
 *
 * <p>网关是 WebFlux 服务，但普通 {@code @RestController} 的写法与 WebMvc 一致；
 * 路径 {@code /api/config-probe} 已加入 JwtAuthenticationFilter 白名单。
 */
@RestController
@RequestMapping("/api/config-probe")
public class ConfigProbeController {

    private final String environment;
    private final String configSource;
    private final String group;

    public ConfigProbeController(
            @Value("${app.environment:dev}") String environment,
            @Value("${app.config.source:local}") String configSource,
            @Value("${spring.cloud.nacos.config.group:PERSONAL_GROUP}") String group) {
        this.environment = environment;
        this.configSource = configSource;
        this.group = group;
    }

    @GetMapping
    public Map<String, Object> probe() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "gateway-service");
        body.put("environment", environment);
        body.put("configSource", configSource);
        body.put("dataId", "gateway-service-" + environment + ".yaml");
        body.put("group", group);
        return body;
    }
}
