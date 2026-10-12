package com.zjgsu.hdt.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.function.Function;

/**
 * 一组明细的营养合计工具（保留 2 位小数、四舍五入）。
 *
 * <p>单独抽出来的原因：{@link MealRecord} 与
 * {@link com.zjgsu.hdt.dto.DailySummaryResponse} 都要做"把一串金额加起来"这件事，
 * 求和规则（精度、空集合、null 处理）只应存在于一处。
 */
final class NutritionTotals {

    private static final int SCALE = 2;

    private NutritionTotals() {
    }

    /**
     * 把一串明细按给定取数函数求和。
     *
     * @param items  明细集合
     * @param mapper 从明细取出一项营养值的函数
     * @return 合计值，保留 2 位小数；集合为空或元素取值为 null 时按 0 处理
     */
    static BigDecimal sum(Collection<MealItem> items, Function<MealItem, BigDecimal> mapper) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (MealItem item : items) {
            BigDecimal value = mapper.apply(item);
            if (value != null) {
                total = total.add(value);
            }
        }
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
