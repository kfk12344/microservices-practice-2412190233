package com.zjgsu.hdt.model;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 餐次时间窗判定测试 —— 业务规则 R2 的判定依据。
 *
 * <p>边界值是这类规则最容易写错的地方（{@code isBefore} 与 {@code !isAfter} 混用会漏掉或误收端点），
 * 所以这里把每个时间窗的起止点都覆盖到。
 */
class MealTypeTest {

    @ParameterizedTest(name = "{0} 的窗口 {1} 起点应被接受")
    @CsvSource({
            "BREAKFAST, 05:00",
            "LUNCH, 11:00",
            "DINNER, 17:00"
    })
    @DisplayName("时间窗起点按闭区间处理，应被接受")
    void windowStartIsInclusive(MealType type, String time) {
        assertThat(type.covers(LocalTime.parse(time))).isTrue();
    }

    @ParameterizedTest(name = "{0} 的窗口 {1} 终点应被接受")
    @CsvSource({
            "BREAKFAST, 10:59",
            "LUNCH, 14:59",
            "DINNER, 21:59"
    })
    @DisplayName("时间窗终点按闭区间处理，应被接受")
    void windowEndIsInclusive(MealType type, String time) {
        assertThat(type.covers(LocalTime.parse(time))).isTrue();
    }

    @ParameterizedTest(name = "{0} 不应接受 {1}")
    @CsvSource({
            "BREAKFAST, 04:59",
            "BREAKFAST, 11:00",
            "LUNCH, 10:59",
            "LUNCH, 15:00",
            "DINNER, 16:59",
            "DINNER, 22:00"
    })
    @DisplayName("窗口外的时间应被拒绝")
    void outsideWindowIsRejected(MealType type, String time) {
        assertThat(type.covers(LocalTime.parse(time))).isFalse();
    }

    @ParameterizedTest(name = "SNACK 应覆盖 {0}")
    @CsvSource({"04:00", "15:30", "16:30", "22:30", "23:59"})
    @DisplayName("加餐兜底覆盖三餐之外的所有时刻")
    void snackCoversGaps(LocalTime time) {
        assertThat(MealType.SNACK.covers(time)).isTrue();
    }

    @ParameterizedTest(name = "SNACK 不应覆盖 {0}")
    @CsvSource({"08:00", "11:30", "12:00", "19:00"})
    @DisplayName("加餐不覆盖三餐时间窗内的时刻（11:30 属于午餐时间窗）")
    void snackExcludesMainMealWindows(LocalTime time) {
        assertThat(MealType.SNACK.covers(time)).isFalse();
    }

    @Test
    @DisplayName("null 时间一律返回 false，不抛异常")
    void nullTimeIsRejected() {
        assertThat(MealType.BREAKFAST.covers(null)).isFalse();
        assertThat(MealType.SNACK.covers(null)).isFalse();
    }

    @Test
    @DisplayName("窗口文本格式为 HH:mm-HH:mm，供接口与文档展示")
    void windowTextFormat() {
        assertThat(MealType.BREAKFAST.getWindowText()).isEqualTo("05:00-10:59");
        assertThat(MealType.LUNCH.getLabel()).isEqualTo("午餐");
    }
}
