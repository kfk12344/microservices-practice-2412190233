package com.zjgsu.hdt.meal.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import com.zjgsu.hdt.meal.exception.DependencyUnavailableException;

/**
 * 基于 Spring Cloud {@link DiscoveryClient} 的实例寻址实现 —— 生产环境用这个。
 *
 * <p>第 07 周接入 Nacos 后，{@code DiscoveryClient} 的实际实现由
 * {@code spring-cloud-starter-alibaba-nacos-discovery} 提供，
 * 它从 Nacos 客户端维护的本地缓存里返回 {@code PERSONAL_GROUP} 分组的实例列表。
 *
 * <p><strong>为什么加 {@code @ConditionalOnProperty}</strong>：
 * 单元测试要求"不依赖真实 Nacos"，测试剖面会把本 Bean 关掉，
 * 改由 {@code TestDiscoveryConfig} 提供一个固定返回假实例的 {@link ServiceInstanceResolver}。
 * 这样做还顺带避免了一个麻烦 —— Spring Cloud 在关闭 Nacos 时会同时注册
 * {@code compositeDiscoveryClient} 与 {@code simpleDiscoveryClient} 两个 primary 的
 * {@code DiscoveryClient}，直接注入会有歧义；本类在测试剖面下根本不创建，问题自然消失。
 */
@Component
@ConditionalOnProperty(name = "meal.discovery.enabled", havingValue = "true", matchIfMissing = true)
public class DiscoveryServiceInstanceResolver implements ServiceInstanceResolver {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryServiceInstanceResolver.class);

    private final DiscoveryClient discoveryClient;

    public DiscoveryServiceInstanceResolver(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @Override
    public String resolveBaseUrl(String serviceName) {
        // 每次调用都查一次，不缓存地址
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);

        if (instances == null || instances.isEmpty()) {
            log.error("注册中心里查不到 {} 的可用实例", serviceName);
            throw new DependencyUnavailableException(serviceName,
                    "Nacos 中查不到可用实例（服务可能已下线或从未注册）");
        }

        // 本周只要求取一个实例完成调用，不做轮询 / 权重 / 重试 / 熔断
        ServiceInstance instance = instances.get(0);
        log.debug("服务发现 {} -> {}:{}（共 {} 个实例）",
                serviceName, instance.getHost(), instance.getPort(), instances.size());

        String baseUrl = instance.getUri().toString();
        // 去掉可能的结尾斜杠，避免拼出 //api/xxx
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
