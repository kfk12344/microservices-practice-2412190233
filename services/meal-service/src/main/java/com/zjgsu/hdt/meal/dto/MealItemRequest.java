package com.zjgsu.hdt.meal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 一条明细的请求：吃的是哪种食物、吃了多少克。
 *
 * @param foodId       食物 ID（会在 food-service 校验存在性）
 * @param portionGrams 份量（克）
 */
public record MealItemRequest(
        @NotNull(message = "foodId 不能为空")
        Long foodId,

        @NotNull(message = "份量不能为空")
        @Min(value = 1, message = "份量至少 1 克")
        @Max(value = 5000, message = "单种食物份量不能超过 5000 克")
        Integer portionGrams
) {
}
