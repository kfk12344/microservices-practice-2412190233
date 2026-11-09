package com.zjgsu.hdt.meal.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.zjgsu.hdt.meal.exception.DependencyResourceNotFoundException;
import com.zjgsu.hdt.meal.exception.DependencyUnavailableException;

import feign.FeignException;

/**
 * 调用 food-service 的门面类 —— 第 08 周起内部改用 <strong>OpenFeign</strong>。
 *
 * <p>结构与 {@link UserClient} 完全对称：Feign 接口负责发请求，
 * 这一层负责把 HTTP 失败翻译成业务异常并记录命中的实例标识。
 *
 * <p>记录一餐时，请求体里每个 {@code foodId} 都会调用一次这里，
 * 所以一次 3 种食物的餐会触发 3 次跨服务调用 —— 这些调用同样会被负载均衡分发。
 */
@Component
public class FoodClient {

    /** Nacos 里注册的服务名，必须与 food-service 的 spring.application.name 一致。 */
    public static final String SERVICE_NAME = FoodFeignApi.SERVICE_NAME;

    private static final Logger log = LoggerFactory.getLogger(FoodClient.class);

    private final FoodFeignApi api;
    private final InstanceHitRecorder recorder;

    public FoodClient(FoodFeignApi api, InstanceHitRecorder recorder) {
        this.api = api;
        this.recorder = recorder;
    }

    /**
     * 查询食物，不存在则抛 {@link DependencyResourceNotFoundException}。
     *
     * @param foodId 食物 ID
     * @return 食物信息（含每 100 克营养值与处理该请求的实例标识）
     */
    public FoodInfo requireFood(Long foodId) {
        try {
            FoodInfo food = api.getFood(foodId);
            if (food == null) {
                throw DependencyResourceNotFoundException.food(foodId);
            }
            recorder.record(SERVICE_NAME, food.servedBy());
            return food;
        } catch (DependencyResourceNotFoundException ex) {
            throw ex;
        } catch (FeignException.NotFound ex) {
            log.warn("food-service 返回 404：foodId={}", foodId);
            throw DependencyResourceNotFoundException.food(foodId);
        } catch (FeignException ex) {
            log.error("调用 food-service 失败：foodId={}，status={}", foodId, ex.status(), ex);
            throw new DependencyUnavailableException(SERVICE_NAME, ex);
        } catch (Exception ex) {
            log.error("调用 food-service 失败（未拿到有效响应）：foodId={}", foodId, ex);
            throw new DependencyUnavailableException(SERVICE_NAME, ex);
        }
    }
}
