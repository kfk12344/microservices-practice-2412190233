package com.zjgsu.hdt.meal.dto;

import java.math.BigDecimal;

import com.zjgsu.hdt.meal.model.MealItem;

/**
 * 明细响应。营养值是记录时落库的<strong>快照</strong>，不再回查 food-service。
 *
 * @param foodId       食物 ID（跨服务引用，可用于再去 food-service 查详情）
 * @param foodName     食物名称快照
 * @param portionGrams 份量（克）
 * @param calories     本份量热量（kcal）
 * @param protein      本份量蛋白质（g）
 * @param fat          本份量脂肪（g）
 * @param carbs        本份量碳水（g）
 */
public record MealItemResponse(
        Long foodId,
        String foodName,
        Integer portionGrams,
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal fat,
        BigDecimal carbs
) {

    public static MealItemResponse from(MealItem item) {
        return new MealItemResponse(
                item.getFoodId(),
                item.getFoodName(),
                item.getPortionGrams(),
                item.getCalories(),
                item.getProtein(),
                item.getFat(),
                item.getCarbs()
        );
    }
}
