package com.zjgsu.hdt.food.dto;

import java.math.BigDecimal;

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
 * @param servedBy        <strong>实际处理这次请求的实例标识</strong>（第 08 周新增）。
 *                        不是业务字段，只用于让调用方验证负载均衡是否真的分发了请求。
 */
public record FoodResponse(
        Long id,
        String name,
        String category,
        BigDecimal caloriesPer100g,
        BigDecimal proteinPer100g,
        BigDecimal fatPer100g,
        BigDecimal carbsPer100g,
        String servedBy
) {

    public static FoodResponse from(Food food) {
        return new FoodResponse(
                food.getId(),
                food.getName(),
                food.getCategory(),
                food.getCaloriesPer100g(),
                food.getProteinPer100g(),
                food.getFatPer100g(),
                food.getCarbsPer100g(),
                null
        );
    }

    /**
     * 补上实例标识，返回新对象（record 不可变）。
     *
     * @param instanceId 本实例标识，一般来自 {@code InstanceIdentity}
     * @return 带实例标识的响应体
     */
    public FoodResponse servedBy(String instanceId) {
        return new FoodResponse(id, name, category, caloriesPer100g,
                proteinPer100g, fatPer100g, carbsPer100g, instanceId);
    }
}
