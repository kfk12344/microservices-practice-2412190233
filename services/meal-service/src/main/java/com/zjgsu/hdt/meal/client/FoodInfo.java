package com.zjgsu.hdt.meal.client;

import java.math.BigDecimal;

/**
 * food-service 返回的食物信息（本服务用它折算份量营养）。
 *
 * <p>只取需要的字段；营养值按每 100 克，折算在 meal-service 侧完成。
 */
public record FoodInfo(
        Long id,
        String name,
        String category,
        BigDecimal caloriesPer100g,
        BigDecimal proteinPer100g,
        BigDecimal fatPer100g,
        BigDecimal carbsPer100g
) {
}
