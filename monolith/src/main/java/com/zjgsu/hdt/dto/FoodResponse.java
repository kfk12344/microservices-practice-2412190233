package com.zjgsu.hdt.dto;

import java.math.BigDecimal;

import com.zjgsu.hdt.model.Food;

/**
 * 食物库条目的响应体。
 *
 * <p>字段名带上 {@code per100g} 后缀，是为了让调用方一眼看清数值口径，
 * 避免把"每 100 克"误当成"每份"。
 *
 * @param id                  食物 ID
 * @param name                名称
 * @param category            分类
 * @param caloriesPer100g     每 100 克热量（kcal）
 * @param proteinPer100g      每 100 克蛋白质（g）
 * @param fatPer100g          每 100 克脂肪（g）
 * @param carbsPer100g        每 100 克碳水（g）
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

    /**
     * 由实体装配响应体。
     *
     * @param food 食物实体
     * @return 响应体
     */
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
