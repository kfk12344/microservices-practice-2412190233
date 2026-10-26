package com.zjgsu.hdt.meal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 饮食记录服务启动类。
 *
 * <p>本服务拥有 {@code health_diet_meal} 库（{@code meal_record} 与 {@code meal_item}），
 * 并在"记录一餐"这条业务流里跨服务调用 user-service 与 food-service 做校验。
 *
 * <p>跨服务只保存对方的 ID（{@code user_id}、{@code food_id}），
 * 不建跨库外键，也不访问对方的表。
 */
@SpringBootApplication
public class MealServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MealServiceApplication.class, args);
    }
}
