package com.zjgsu.hdt.meal.client;

import java.math.BigDecimal;

/**
 * food-service 返回的食物信息（本服务用它折算份量营养）。
 *
 * <p>只取需要的字段；营养值按每 100 克，折算在 meal-service 侧完成。
 *
 * @param servedBy <strong>实际处理这次请求的实例标识</strong>（由 food-service 写入），
 *                 仅用于验证负载均衡分发，不参与营养计算。
 */
public record FoodInfo(
        Long id,
        String name,
        String category,
        BigDecimal caloriesPer100g,
        BigDecimal proteinPer100g,
        BigDecimal fatPer100g,
        BigDecimal carbsPer100g,
        String servedBy
) {
}
