package com.zjgsu.hdt.meal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.zjgsu.hdt.meal.model.MealItem;
import com.zjgsu.hdt.meal.model.MealRecord;

/**
 * 一条饮食记录的响应体，含明细与合计。
 *
 * @param id             记录 ID
 * @param userId         用户 ID（跨服务引用，不返回用户名 —— 那属于 user-service）
 * @param mealType       餐次枚举名
 * @param mealTypeLabel  餐次中文名
 * @param eatenAt        用餐时间
 * @param note           备注
 * @param items          明细
 * @param totalCalories  合计热量（kcal）
 * @param totalProtein   合计蛋白质（g）
 * @param totalFat       合计脂肪（g）
 * @param totalCarbs     合计碳水（g）
 * @param totalGrams     合计份量（g）
 */
public record MealResponse(
        Long id,
        Long userId,
        String mealType,
        String mealTypeLabel,
        LocalDateTime eatenAt,
        String note,
        List<MealItemResponse> items,
        BigDecimal totalCalories,
        BigDecimal totalProtein,
        BigDecimal totalFat,
        BigDecimal totalCarbs,
        int totalGrams
) {

    public static MealResponse from(MealRecord record) {
        List<MealItem> items = record.getItems();

        BigDecimal calories = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        int grams = 0;

        for (MealItem item : items) {
            calories = calories.add(item.getCalories());
            protein = protein.add(item.getProtein());
            fat = fat.add(item.getFat());
            carbs = carbs.add(item.getCarbs());
            grams += item.getPortionGrams();
        }

        return new MealResponse(
                record.getId(),
                record.getUserId(),
                record.getMealType().name(),
                record.getMealType().getLabel(),
                record.getEatenAt(),
                record.getNote(),
                items.stream().map(MealItemResponse::from).toList(),
                calories,
                protein,
                fat,
                carbs,
                grams
        );
    }
}
