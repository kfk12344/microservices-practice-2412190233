package com.zjgsu.hdt.meal.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * food-service 的声明式客户端（OpenFeign）。
 *
 * <p>同样是按<strong>服务名</strong>调用。记录一餐时，请求体里每个 {@code foodId}
 * 都会走一次这里 —— 既校验食物存在，又把每 100 克营养值取回来做折算。
 */
@FeignClient(name = FoodFeignApi.SERVICE_NAME)
public interface FoodFeignApi {

    /** Nacos 里注册的服务名，必须与 food-service 的 spring.application.name 一致。 */
    String SERVICE_NAME = "food-service";

    /**
     * 按 ID 查询食物。对方不存在时返回 404，Feign 会抛 {@code FeignException.NotFound}。
     *
     * @param id 食物 ID
     * @return 食物信息（含每 100 克营养值与处理该请求的实例标识）
     */
    @GetMapping("/api/foods/{id}")
    FoodInfo getFood(@PathVariable("id") Long id);
}
