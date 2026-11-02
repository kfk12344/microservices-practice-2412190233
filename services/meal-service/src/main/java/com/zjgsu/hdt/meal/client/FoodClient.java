package com.zjgsu.hdt.meal.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.zjgsu.hdt.meal.exception.DependencyResourceNotFoundException;
import com.zjgsu.hdt.meal.exception.DependencyUnavailableException;

/**
 * 调用 food-service 的客户端 —— 第 07 周改为通过 Nacos 服务发现寻址。
 *
 * <p>记录一餐时对每个 {@code foodId} 调用一次：既校验食物存在，
 * 又拿到每 100 克营养值用于折算。地址每次现查，不缓存。
 */
@Component
public class FoodClient {

    /** Nacos 里注册的服务名，必须与 food-service 的 spring.application.name 一致。 */
    public static final String SERVICE_NAME = "food-service";

    private static final Logger log = LoggerFactory.getLogger(FoodClient.class);

    private final RestTemplate restTemplate;
    private final ServiceInstanceResolver instanceResolver;

    public FoodClient(RestTemplate restTemplate, ServiceInstanceResolver instanceResolver) {
        this.restTemplate = restTemplate;
        this.instanceResolver = instanceResolver;
    }

    /**
     * 查询食物，不存在则抛 {@link DependencyResourceNotFoundException}。
     */
    public FoodInfo requireFood(Long foodId) {
        String baseUrl = instanceResolver.resolveBaseUrl(SERVICE_NAME);
        String url = baseUrl + "/api/foods/" + foodId;

        try {
            FoodInfo food = restTemplate.getForObject(url, FoodInfo.class);
            if (food == null) {
                throw DependencyResourceNotFoundException.food(foodId);
            }
            return food;
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("food-service 返回 404：foodId={}", foodId);
            throw DependencyResourceNotFoundException.food(foodId);
        } catch (RestClientException ex) {
            log.error("调用 food-service 失败：url={}", url, ex);
            throw new DependencyUnavailableException(SERVICE_NAME, ex);
        }
    }
}
