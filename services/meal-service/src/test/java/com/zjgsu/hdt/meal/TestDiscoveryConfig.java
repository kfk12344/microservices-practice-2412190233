package com.zjgsu.hdt.meal;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.zjgsu.hdt.meal.client.ServiceInstanceResolver;
import com.zjgsu.hdt.meal.exception.DependencyUnavailableException;

/**
 * 测试用的服务寻址桩 —— <strong>不连接真实 Nacos</strong>。
 *
 * <p>为什么需要它：第 07 周之后 {@code UserClient} / {@code FoodClient} 不再持有固定地址，
 * 而是每次调用前通过 {@link ServiceInstanceResolver} 查实例。测试里如果真去连 Nacos，
 * 就变成"跑单元测试要先启动中间件"，这与作业要求的"测试不依赖真实 Nacos"冲突。
 *
 * <p>这个桩固定把 {@code user-service} 解析到本地 18081、{@code food-service} 到 18082，
 * 于是 {@code MockRestServiceServer} 能按
 * {@code http://localhost:18081/api/users/1} 这样的地址匹配请求 ——
 * 测试真正验证到的是"发现出来的地址被正确用于拼接业务路径"。
 *
 * <p>生产实现 {@code DiscoveryServiceInstanceResolver} 在测试剖面下被
 * {@code meal.discovery.enabled=false} 关掉，因此这里通常是唯一的
 * {@code ServiceInstanceResolver}；标 {@code @Primary} 只是双保险。
 *
 * <p>实例列表为空的场景不在这里处理，而是在测试方法里就地构造一个
 * "解析即抛 503"的桩，让"没有实例"这个状态在测试代码里一眼可见。
 */
@TestConfiguration
public class TestDiscoveryConfig {

    /** 桩里 user-service 实例的基地址。 */
    public static final String USER_BASE_URL = "http://localhost:18081";
    /** 桩里 food-service 实例的基地址。 */
    public static final String FOOD_BASE_URL = "http://localhost:18082";

    @Bean
    @Primary
    public ServiceInstanceResolver stubInstanceResolver() {
        return serviceName -> switch (serviceName) {
            case "user-service" -> USER_BASE_URL;
            case "food-service" -> FOOD_BASE_URL;
            default -> throw new DependencyUnavailableException(serviceName,
                    "Nacos 中查不到可用实例（测试桩）");
        };
    }
}
