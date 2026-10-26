package com.zjgsu.hdt.food.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增食物的请求体。
 *
 * @param name            名称，唯一
 * @param category        分类
 * @param caloriesPer100g 每 100 克热量（kcal），不超过 900（纯油脂约 900）
 * @param proteinPer100g  每 100 克蛋白质（g）
 * @param fatPer100g      每 100 克脂肪（g）
 * @param carbsPer100g    每 100 克碳水（g）
 */
public record FoodRequest(
        @NotBlank(message = "食物名称不能为空")
        @Size(max = 64, message = "食物名称长度不能超过 64")
        String name,

        @NotBlank(message = "分类不能为空")
        @Size(max = 32, message = "分类长度不能超过 32")
        String category,

        @NotNull(message = "热量不能为空")
        @DecimalMin(value = "0.0", message = "热量不能为负")
        @DecimalMax(value = "900.0", message = "每 100 克热量不能超过 900 kcal")
        BigDecimal caloriesPer100g,

        @NotNull(message = "蛋白质不能为空")
        @DecimalMin(value = "0.0", message = "蛋白质不能为负")
        BigDecimal proteinPer100g,

        @NotNull(message = "脂肪不能为空")
        @DecimalMin(value = "0.0", message = "脂肪不能为负")
        BigDecimal fatPer100g,

        @NotNull(message = "碳水不能为空")
        @DecimalMin(value = "0.0", message = "碳水不能为负")
        BigDecimal carbsPer100g
) {
}
