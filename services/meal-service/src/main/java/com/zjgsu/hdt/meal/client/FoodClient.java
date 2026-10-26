package com.zjgsu.hdt.meal.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.zjgsu.hdt.meal.exception.DependencyResourceNotFoundException;
import com.zjgsu.hdt.meal.exception.DependencyUnavailableException;

/**
 * 调用 food-service 的客户端。
 *
 * <p>记录一餐时对每个 {@code foodId} 调用一次，既能校验食物存在，
 * 又能拿到每 100 克营养值用于折算 —— 一次调用同时完成校验与取数。
 */
@Component
public class FoodClient {

    private static final Logger log = LoggerFactory.getLogger(FoodClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public FoodClient(RestTemplate restTemplate,
                      @Value("${services.food-service.url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 查询食物，不存在则抛 {@link DependencyResourceNotFoundException}。
     */
    public FoodInfo requireFood(Long foodId) {
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
            throw new DependencyUnavailableException("food-service", ex);
        }
    }
}
