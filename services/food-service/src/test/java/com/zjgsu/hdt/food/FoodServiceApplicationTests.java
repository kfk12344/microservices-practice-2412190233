package com.zjgsu.hdt.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.zjgsu.hdt.food.dto.FoodRequest;
import com.zjgsu.hdt.food.dto.FoodResponse;
import com.zjgsu.hdt.food.exception.ResourceNotFoundException;
import com.zjgsu.hdt.food.service.FoodService;

/**
 * food-service 的测试：验证上下文装配、种子数据、按 ID 查询与"不存在"分支，
 * 以及创建流程。其中"不存在抛 404"这一分支是 meal-service 跨服务校验的基础。
 *
 * <p>类上加 {@code @Transactional}：每个测试方法跑在自己的事务里，结束后回滚。
 * 否则"创建食物"那个测试插入的数据会残留，让后面的行数断言随执行顺序而失败。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FoodServiceApplicationTests {

    @Autowired
    private FoodService foodService;

    @Test
    @DisplayName("上下文能加载，且 data.sql 的 12 条食物已灌入")
    void contextLoads() {
        assertThat(foodService.listAll(null))
                .as("data.sql 应插入 12 种食物")
                .hasSize(12);
    }

    @Test
    @DisplayName("按 ID 查询：返回每 100 克营养值，供 meal-service 折算")
    void findByIdReturnsNutrients() {
        FoodResponse rice = foodService.findById(1L);

        assertThat(rice.name()).isEqualTo("米饭");
        assertThat(rice.category()).isEqualTo("主食");
        assertThat(rice.caloriesPer100g()).isEqualByComparingTo("116.00");
        assertThat(rice.proteinPer100g()).isEqualByComparingTo("2.60");
    }

    @Test
    @DisplayName("按 ID 查询：不存在时抛 ResourceNotFoundException")
    void findByIdThrowsWhenMissing() {
        assertThatThrownBy(() -> foodService.findById(999999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999999");
    }

    @Test
    @DisplayName("按分类过滤：主食应有 3 条")
    void listByCategory() {
        assertThat(foodService.listAll("主食"))
                .hasSize(3)
                .allSatisfy(food -> assertThat(food.category()).isEqualTo("主食"));
    }

    @Test
    @DisplayName("新增食物：能落库并可查回")
    void createFoodPersists() {
        FoodResponse created = foodService.create(new FoodRequest(
                "燕麦", "主食",
                new BigDecimal("389.00"), new BigDecimal("16.90"),
                new BigDecimal("6.90"), new BigDecimal("66.30")
        ));

        assertThat(created.id()).isNotNull();
        assertThat(foodService.findById(created.id()).name()).isEqualTo("燕麦");
        assertThat(foodService.listAll(null)).hasSize(13);
    }
}
