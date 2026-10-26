package com.zjgsu.hdt.food;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 食物服务启动类。
 *
 * <p>本服务只拥有食物库数据（{@code health_diet_food.food}），
 * 为 meal-service 提供"食物是否存在"与"每 100 克营养值"的查询能力。
 */
@SpringBootApplication
public class FoodServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoodServiceApplication.class, args);
    }
}
