package com.zjgsu.hdt.meal.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 跨服务调用的 RestTemplate 配置。
 *
 * <p>为什么必须显式配超时：{@code RestTemplate} 的默认超时是<strong>无限</strong>的。
 * 一旦 user-service 或 food-service 卡住不响应，meal-service 的请求线程会一直被占着，
 * 并发上来之后线程池被耗尽，一个依赖变慢会拖垮整个服务 —— 这是拆分后最典型的连锁故障。
 *
 * <p>超时值可通过环境变量覆盖，便于在演示"依赖服务不可用"场景时把超时调小。
 */
@Configuration
public class HttpClientConfig {

    /**
     * 供 user-service / food-service 调用共用的 RestTemplate。
     *
     * @param connectTimeoutMs 建立连接超时（毫秒）
     * @param readTimeoutMs    读取响应超时（毫秒）
     */
    @Bean
    public RestTemplate restTemplate(
            @Value("${services.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${services.read-timeout-ms:3000}") long readTimeoutMs) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return new RestTemplate(factory);
    }
}
