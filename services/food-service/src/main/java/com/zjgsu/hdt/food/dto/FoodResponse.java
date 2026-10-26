package com.zjgsu.hdt.food.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.zjgsu.hdt.food.model.Food;

/**
 * 食物响应体。
 *
 * <p>字段名 {@code caloriesPer100g} 等是 meal-service 折算营养时依赖的契约，
 * 改动会影响跨服务调用。
 *
 * @param id              食物 ID
 * @param name            名称
 * @param category        分类
 * @param caloriesPer100g 每 100 克热量（kcal）
 * @param proteinPer100g  每 100 克蛋白质（g）
 * @param fatPer100g      每 100 克脂肪（g）
 * @param carbsPer100g    每 100 克碳水（g）
 */
public record FoodResponse(
        Long id,
        String name,
        String category,
        BigDecimal caloriesPer100g,
        BigDecimal proteinPer100g,
        BigDecimal fatPer100g,
        BigDecimal carbsPer100g
) {

    public static FoodResponse from(Food food) {
        return new FoodResponse(
                food.getId(),
                food.getName(),
                food.getCategory(),
                food.getCaloriesPer100g(),
                food.getProteinPer100g(),
                food.getFatPer100g(),
                food.getCarbsPer100g()
        );
    }
}
