package com.zjgsu.hdt.meal.model;

import java.util.Arrays;

/**
 * 餐次枚举。
 *
 * <p>与第 04 周单体版本保持一致；数据库层有 CHECK 约束兜底，
 * 应用层解析失败时抛出明确异常，而不是静默回退成某个默认值。
 */
public enum MealType {

    BREAKFAST("早餐"),
    LUNCH("午餐"),
    DINNER("晚餐"),
    SNACK("加餐");

    private final String label;

    MealType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static MealType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("餐次不能为空");
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "未知的餐次：" + raw + "，可选值：BREAKFAST / LUNCH / DINNER / SNACK"));
    }
}
