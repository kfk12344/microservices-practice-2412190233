package com.zjgsu.hdt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.zjgsu.hdt.model.BusinessLimits;

/**
 * 某一天的饮食汇总（热量收支仪表盘的数据来源）。
 *
 * @param date            统计日期
 * @param eater           用餐人
 * @param mealCount       当天记录条数
 * @param totalCalories   当天总摄入热量（kcal）
 * @param totalProtein    蛋白质合计（g）
 * @param totalFat        脂肪合计（g）
 * @param totalCarbs      碳水合计（g）
 * @param totalGrams      食物总重量（g）
 * @param byMealType      按餐次拆分的明细
 * @param singleMealLimit 单次用餐热量上限（kcal），即业务规则 R3 的阈值
 * @param meals           当天的记录列表
 */
public record DailySummaryResponse(
        LocalDate date,
        String eater,
        int mealCount,
        BigDecimal totalCalories,
        BigDecimal totalProtein,
        BigDecimal totalFat,
        BigDecimal totalCarbs,
        int totalGrams,
        List<MealTypeBreakdown> byMealType,
        int singleMealLimit,
        List<MealRecordResponse> meals
) {

    /**
     * 按餐次拆分的小计。
     *
     * @param mealType      餐次枚举名
     * @param mealTypeLabel 餐次中文名
     * @param count         该餐次记录条数
     * @param calories      该餐次热量合计（kcal）
     */
    public record MealTypeBreakdown(
            String mealType,
            String mealTypeLabel,
            int count,
            BigDecimal calories
    ) {
    }

    /** 便于 Service 构造时统一引用规则阈值。 */
    public static int defaultSingleMealLimit() {
        return BusinessLimits.MAX_CALORIES_PER_MEAL;
    }
}
