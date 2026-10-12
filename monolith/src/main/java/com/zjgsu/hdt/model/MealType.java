package com.zjgsu.hdt.model;

import java.time.LocalTime;

/**
 * 餐次类型，并携带各自的"合理用餐时间窗"。
 *
 * <p>时间窗不是装饰性的常量：Week 04 的业务规则 R2 就用它校验
 * "记录的用餐时间是否与所选餐次相符"，例如把一顿饭标成"早餐"却记在 20:00 会被拒绝。
 */
public enum MealType {

    /** 早餐：05:00 – 10:59 */
    BREAKFAST("早餐", LocalTime.of(5, 0), LocalTime.of(10, 59)),

    /** 午餐：11:00 – 14:59 */
    LUNCH("午餐", LocalTime.of(11, 0), LocalTime.of(14, 59)),

    /** 晚餐：17:00 – 21:59 */
    DINNER("晚餐", LocalTime.of(17, 0), LocalTime.of(21, 59)),

    /** 加餐：其余时间（含下午茶与夜宵） */
    SNACK("加餐", LocalTime.MIDNIGHT, LocalTime.of(23, 59, 59));

    private final String label;
    private final LocalTime windowStart;
    private final LocalTime windowEnd;

    MealType(String label, LocalTime windowStart, LocalTime windowEnd) {
        this.label = label;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    /** 中文名，用于提示信息与文档。 */
    public String getLabel() {
        return label;
    }

    public LocalTime getWindowStart() {
        return windowStart;
    }

    public LocalTime getWindowEnd() {
        return windowEnd;
    }

    /** 时间窗的可读形式，例如 {@code 05:00-10:59}。 */
    public String getWindowText() {
        return windowStart + "-" + windowEnd;
    }

    /**
     * 判断某个时刻是否落在该餐次的时间窗内。
     *
     * <p>边界按闭区间处理：正好 11:00 算午餐，正好 10:59 仍算早餐。
     *
     * @param time 待判断的用餐时刻
     * @return 落在窗口内返回 {@code true}
     */
    public boolean covers(LocalTime time) {
        if (time == null) {
            return false;
        }
        if (this == SNACK) {
            // 加餐兜底：只要不属于其它三餐的时间窗就算加餐，避免出现"无处可放"的时刻
            return !BREAKFAST.coversStrict(time) && !LUNCH.coversStrict(time) && !DINNER.coversStrict(time);
        }
        return coversStrict(time);
    }

    private boolean coversStrict(LocalTime time) {
        return !time.isBefore(windowStart) && !time.isAfter(windowEnd);
    }
}
