package com.zjgsu.hdt.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zjgsu.hdt.dto.DailySummaryResponse;
import com.zjgsu.hdt.dto.MealRecordResponse;
import com.zjgsu.hdt.model.BusinessLimits;
import com.zjgsu.hdt.model.MealRecord;
import com.zjgsu.hdt.model.MealType;

/**
 * 日用汇总服务 —— 把一天的多条饮食记录合并成"热量收支"视图。
 *
 * <p>对应 README 里描述的核心业务流程最后一步："查看今日热量收支"。
 *
 * <p>聚合在内存完成而不是写复杂的 JPQL：一次用餐的食物映射
 * （{@code Map<Food, Integer>}）键是实体，JPQL 要取该键需借助 {@code KEY()} 语义，
 * 强凑 SQL 反而更难读、更难改。日粒度数据量很小，内存聚合的代价可以忽略。
 */
@Service
@Transactional(readOnly = true)
public class DailySummaryService {

    private final MealRecordService mealRecordService;

    public DailySummaryService(MealRecordService mealRecordService) {
        this.mealRecordService = mealRecordService;
    }

    /**
     * 汇总某人某天的饮食情况。
     *
     * @param eater 用餐人
     * @param date  统计日期；为空时取今天
     * @return 当日汇总；当天没有记录时各项合计为 0，而不是抛错——"今天还没吃"是正常状态
     */
    public DailySummaryResponse summarize(String eater, LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        List<MealRecord> records = mealRecordService.findEntitiesByDate(eater, target);

        List<MealRecordResponse> meals = records.stream().map(MealRecordResponse::from).toList();

        BigDecimal totalCalories = NutritionCalculator.sumAll(
                meals.stream().map(MealRecordResponse::totalCalories).toList());
        BigDecimal totalProtein = NutritionCalculator.sumAll(
                meals.stream().map(MealRecordResponse::totalProtein).toList());
        BigDecimal totalFat = NutritionCalculator.sumAll(
                meals.stream().map(MealRecordResponse::totalFat).toList());
        BigDecimal totalCarbs = NutritionCalculator.sumAll(
                meals.stream().map(MealRecordResponse::totalCarbs).toList());
        int totalGrams = meals.stream().mapToInt(MealRecordResponse::totalGrams).sum();

        return new DailySummaryResponse(
                target,
                eater,
                meals.size(),
                totalCalories,
                totalProtein,
                totalFat,
                totalCarbs,
                totalGrams,
                breakdown(meals),
                BusinessLimits.MAX_CALORIES_PER_MEAL,
                meals
        );
    }

    /**
     * 按餐次拆分小计。按枚举声明顺序（早/午/晚/加餐）输出，且只列出当天有记录的餐次，
     * 这样 JSON 结构固定、便于对照截图。
     *
     * @param meals 当天记录
     * @return 各餐次小计
     */
    private List<DailySummaryResponse.MealTypeBreakdown> breakdown(List<MealRecordResponse> meals) {
        Map<MealType, List<MealRecordResponse>> grouped = new LinkedHashMap<>();
        for (MealType type : MealType.values()) {
            grouped.put(type, new ArrayList<>());
        }
        for (MealRecordResponse meal : meals) {
            grouped.get(meal.mealType()).add(meal);
        }

        List<DailySummaryResponse.MealTypeBreakdown> result = new ArrayList<>();
        grouped.forEach((type, list) -> {
            if (!list.isEmpty()) {
                result.add(new DailySummaryResponse.MealTypeBreakdown(
                        type.name(),
                        type.getLabel(),
                        list.size(),
                        NutritionCalculator.sumAll(list.stream().map(MealRecordResponse::totalCalories).toList())
                ));
            }
        });
        return result;
    }
}
