package com.zjgsu.hdt.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Map;

import com.zjgsu.hdt.model.Food;

/**
 * 营养换算工具：把"每 100 克的营养值"折算成"实际份量下的营养值"。
 *
 * <p>做成无状态工具类的理由：这套换算被多处复用（单条记录、每日汇总、接口响应），
 * 且不依赖任何 Spring 容器能力，所以不需要做成 Bean，纯函数更易测试。
 *
 * <p>所有金额/营养值统一保留 2 位小数、四舍五入，避免不同接口给出精度不一致的结果。
 */
public final class NutritionCalculator {

    /** 营养数据的基准份量：每 100 克。 */
    public static final BigDecimal BASIS_GRAMS = BigDecimal.valueOf(100);

    /** 统一的小数位数。 */
    private static final int SCALE = 2;

    private NutritionCalculator() {
    }

    /**
     * 按份量折算某一项营养值。
     *
     * @param per100g      每 100 克的营养值
     * @param portionGrams 实际份量（克）
     * @return 折算结果，保留 2 位小数；任一人参为空或份量为 0 时返回 {@code 0.00}
     */
    public static BigDecimal scale(BigDecimal per100g, Integer portionGrams) {
        if (per100g == null || portionGrams == null || portionGrams <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return per100g
                .multiply(BigDecimal.valueOf(portionGrams))
                .divide(BASIS_GRAMS, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 计算一批食物（食物 → 份量）的某一项营养合计。
     *
     * @param items   食物与份量
     * @param extract 从食物上取出"每 100 克营养值"的函数
     * @return 合计值，保留 2 位小数；入参为空时返回 {@code 0.00}
     */
    public static BigDecimal total(Map<Food, Integer> items, java.util.function.Function<Food, BigDecimal> extract) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<Food, Integer> entry : items.entrySet()) {
            sum = sum.add(scale(extract.apply(entry.getKey()), entry.getValue()));
        }
        return sum.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** 合计热量（kcal）。 */
    public static BigDecimal totalCalories(Map<Food, Integer> items) {
        return total(items, Food::getCaloriesPer100g);
    }

    /** 合计蛋白质（g）。 */
    public static BigDecimal totalProtein(Map<Food, Integer> items) {
        return total(items, Food::getProteinPer100g);
    }

    /** 合计脂肪（g）。 */
    public static BigDecimal totalFat(Map<Food, Integer> items) {
        return total(items, Food::getFatPer100g);
    }

    /** 合计碳水化合物（g）。 */
    public static BigDecimal totalCarbs(Map<Food, Integer> items) {
        return total(items, Food::getCarbsPer100g);
    }

    /**
     * 把多份合计值再求和（用于把多条用餐记录汇总成日总计）。
     *
     * @param values 待求和的数值，允许包含 null（按 0 处理）
     * @return 合计值，保留 2 位小数
     */
    public static BigDecimal sumAll(Collection<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value != null) {
                sum = sum.add(value);
            }
        }
        return sum.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** 份量合计（克），用于每日汇总里的总摄入重量。 */
    public static int totalGrams(Map<Food, Integer> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        return items.values().stream().filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
    }
}
