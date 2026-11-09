package com.zjgsu.hdt.meal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 饮食记录服务启动类。
 *
 * <p>本服务拥有 {@code health_diet_meal} 库（{@code meal_record} 与 {@code meal_item}），
 * 并在"记录一餐"这条业务流里跨服务调用 user-service 与 food-service 做校验。
 *
 * <p>跨服务只保存对方的 ID（{@code user_id}、{@code food_id}），
 * 不建跨库外键，也不访问对方的表。
 *
 * <p>第 08 周：跨服务调用改为 <strong>OpenFeign</strong>。
 * {@code @EnableFeignClients} 会扫描 {@code client} 包下带 {@code @FeignClient} 的接口，
 * 生成代理实现；接口里写的是服务名，不是 IP ——
 * 由 Spring Cloud LoadBalancer 在每次调用时挑一个健康实例。
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.zjgsu.hdt.meal.client")
public class MealServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MealServiceApplication.class, args);
    }
}
