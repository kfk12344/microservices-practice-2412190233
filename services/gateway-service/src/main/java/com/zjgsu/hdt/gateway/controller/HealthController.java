package com.zjgsu.hdt.gateway.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网关健康入口 —— 对应作业要求的 {@code /api/health} 与 {@code /actuator/health}。
 *
 * <p>两者用途不同：
 * <ul>
 *   <li>{@code /actuator/health} 给容器编排/探针用，只回答"网关进程活着吗"；</li>
 *   <li>{@code /api/health} 给人看，额外返回 Nacos 里当前注册了哪些服务，
 *       便于确认"网关能发现下游"这件事是否成立。</li>
 * </ul>
 */
@RestController
public class HealthController {

    private final DiscoveryClient discoveryClient;

    public HealthController(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        List<String> services;
        try {
            services = discoveryClient.getServices();
        } catch (RuntimeException ex) {
            // 注册中心不可用时仍然返回 200：网关自己活着，只是暂时发现不了下游
            services = List.of();
        }
        return Map.of(
                "status", "UP",
                "service", "gateway-service",
                "timestamp", OffsetDateTime.now().toString(),
                "registeredServices", services
        );
    }
}
