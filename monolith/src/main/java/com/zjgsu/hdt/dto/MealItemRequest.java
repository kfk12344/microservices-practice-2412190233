package com.zjgsu.hdt.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 创建饮食记录时，"一种食物 + 份量"的请求项。
 *
 * @param foodId       食物 ID，须存在于食物库
 * @param portionGrams 份量（克），必须为正数
 */
public record MealItemRequest(
        @NotNull(message = "食物 ID 不能为空")
        @Positive(message = "食物 ID 必须为正数")
        Long foodId,

        @NotNull(message = "份量不能为空")
        @Positive(message = "份量必须为正整数（克）")
        Integer portionGrams
) {
}
