package com.zjgsu.hdt.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.zjgsu.hdt.model.MealRecord;
import com.zjgsu.hdt.model.MealType;

/**
 * 饮食记录的响应体。
 *
 * <p>营养合计由服务端算好直接返回，这是本项目的既定约定：
 * 换算口径（每 100 克基准、保留 2 位小数）只应存在于服务端一处，
 * 否则 Web、小程序、导出脚本各算一遍，迟早算出不一致的数字。
 *
 * @param id            记录 ID
 * @param eater         用餐人
 * @param mealType      餐次枚举名，例如 {@code BREAKFAST}
 * @param mealTypeLabel 餐次中文名，例如 {@code 早餐}
 * @param eatenAt       用餐时间
 * @param note          备注
 * @param items         食物明细
 * @param totalCalories 本次用餐总热量（kcal）
 * @param totalProtein  蛋白质合计（g）
 * @param totalFat      脂肪合计（g）
 * @param totalCarbs    碳水合计（g）
 * @param totalGrams    食物总重量（g）
 */
public record MealRecordResponse(
        Long id,
        String eater,
        MealType mealType,
        String mealTypeLabel,
        LocalDateTime eatenAt,
        String note,
        List<Item> items,
        BigDecimal totalCalories,
        BigDecimal totalProtein,
        BigDecimal totalFat,
        BigDecimal totalCarbs,
        int totalGrams
) {

    /**
     * 食物明细项。
     *
     * @param foodId      食物 ID
     * @param name        食物名称
     * @param category    食物分类
     * @param portionGrams 份量（克）
     * @param calories    该份量下的热量（kcal）
     * @param protein     该份量下的蛋白质（g）
     * @param fat         该份量下的脂肪（g）
     * @param carbs       该份量下的碳水（g）
     */
    public record Item(
            Long foodId,
            String name,
            String category,
            int portionGrams,
            BigDecimal calories,
            BigDecimal protein,
            BigDecimal fat,
            BigDecimal carbs
    ) {
    }

    /**
     * 由实体装配响应体，同时完成全部营养换算。
     *
     * @param record 已持久化的饮食记录
     * @return 可直接序列化为 JSON 的响应体
     */
    public static MealRecordResponse from(MealRecord record) {
        List<Item> items = record.getItems().stream()
                .map(item -> new Item(
                        item.getFood().getId(),
                        item.getFood().getName(),
                        item.getFood().getCategory(),
                        item.getPortionGrams(),
                        item.calories(),
                        item.protein(),
                        item.fat(),
                        item.carbs()
                ))
                // 明细顺序固定按食物 ID 排序：同一份数据每次返回的 JSON 完全一致，便于截图与比对
                .sorted(Comparator.comparing(Item::foodId))
                .toList();

        return new MealRecordResponse(
                record.getId(),
                record.getEater(),
                record.getMealType(),
                record.getMealType().getLabel(),
                record.getEatenAt(),
                record.getNote(),
                items,
                record.totalCalories(),
                record.totalProtein(),
                record.totalFat(),
                record.totalCarbs(),
                record.totalGrams()
        );
    }

    /** 便于测试与排查：明细的"食物名 → 份量"映射。 */
    public java.util.Map<String, Integer> portionByName() {
        return items.stream().collect(java.util.stream.Collectors.toMap(
                Item::name, Item::portionGrams, (a, b) -> a, java.util.LinkedHashMap::new));
    }
}
