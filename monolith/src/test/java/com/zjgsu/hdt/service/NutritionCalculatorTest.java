package com.zjgsu.hdt.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.zjgsu.hdt.model.Food;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 营养换算单元测试 —— 业务规则 R3 的计算基础。
 *
 * <p>纯单元测试（不启动 Spring、不连数据库）：换算规则必须能被独立验证，
 * 否则一旦算错，热量上限规则就会在错误的数字上做判断，而接口测试很难发现这类问题。
 *
 * <p>用到的食物基准值（每 100 克）与 data.sql 保持一致：
 * <ul>
 *   <li>米饭：116 kcal / 2.6 蛋白 / 0.3 脂肪 / 25.9 碳水</li>
 *   <li>鸡胸肉：133 kcal / 24.6 / 2.5 / 0.6</li>
 * </ul>
 */
class NutritionCalculatorTest {

    private static Food food(String name, String kcal, String protein, String fat, String carbs) {
        return new Food(name, "测试",
                new BigDecimal(kcal), new BigDecimal(protein), new BigDecimal(fat), new BigDecimal(carbs));
    }

    private static final Food RICE = food("米饭", "116", "2.6", "0.3", "25.9");
    private static final Food CHICKEN = food("鸡胸肉", "133", "24.6", "2.5", "0.6");

    @Test
    @DisplayName("按份量折算：200g 米饭 = 232.00 kcal")
    void scalesByPortion() {
        assertThat(NutritionCalculator.scale(RICE.getCaloriesPer100g(), 200))
                .isEqualByComparingTo("232.00");
    }

    @Test
    @DisplayName("折算结果统一保留 2 位小数并四舍五入")
    void roundsToTwoDecimals() {
        // 133 × 150 / 100 = 199.5；116 × 1 / 100 = 1.16
        assertThat(NutritionCalculator.scale(CHICKEN.getCaloriesPer100g(), 150))
                .isEqualByComparingTo("199.50");
        assertThat(NutritionCalculator.scale(RICE.getCaloriesPer100g(), 1))
                .isEqualByComparingTo("1.16");
    }

    @Test
    @DisplayName("份量为 0 或负数、营养值为空时返回 0.00，而不是抛异常")
    void returnsZeroForInvalidInput() {
        assertThat(NutritionCalculator.scale(RICE.getCaloriesPer100g(), 0))
                .isEqualByComparingTo("0.00");
        assertThat(NutritionCalculator.scale(RICE.getCaloriesPer100g(), -5))
                .isEqualByComparingTo("0.00");
        assertThat(NutritionCalculator.scale(null, 100)).isEqualByComparingTo("0.00");
        assertThat(NutritionCalculator.scale(RICE.getCaloriesPer100g(), null))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("多种食物的四项营养合计分别计算正确")
    void totalsFourNutrients() {
        Map<Food, Integer> items = new LinkedHashMap<>();
        items.put(RICE, 200);     // 232.00 / 5.20 / 0.60 / 51.80
        items.put(CHICKEN, 150);  // 199.50 / 36.90 / 3.75 / 0.90

        assertThat(NutritionCalculator.totalCalories(items)).isEqualByComparingTo("431.50");
        assertThat(NutritionCalculator.totalProtein(items)).isEqualByComparingTo("42.10");
        assertThat(NutritionCalculator.totalFat(items)).isEqualByComparingTo("4.35");
        assertThat(NutritionCalculator.totalCarbs(items)).isEqualByComparingTo("52.70");
    }

    @Test
    @DisplayName("空集合的合计为 0.00（当天没吃饭不应报错）")
    void emptyItemsTotalZero() {
        assertThat(NutritionCalculator.totalCalories(Map.of())).isEqualByComparingTo("0.00");
        assertThat(NutritionCalculator.totalCalories(null)).isEqualByComparingTo("0.00");
        assertThat(NutritionCalculator.totalGrams(Map.of())).isZero();
    }

    @Test
    @DisplayName("份量合计为各食物克数之和")
    void totalsGrams() {
        Map<Food, Integer> items = Map.of(RICE, 200, CHICKEN, 150);
        assertThat(NutritionCalculator.totalGrams(items)).isEqualTo(350);
    }

    @Test
    @DisplayName("sumAll 忽略 null 并按 2 位小数取整")
    void sumAllHandlesNulls() {
        assertThat(NutritionCalculator.sumAll(java.util.Arrays.asList(
                new BigDecimal("1.005"), null, new BigDecimal("2.005"))))
                .isEqualByComparingTo("3.01");
    }
}
