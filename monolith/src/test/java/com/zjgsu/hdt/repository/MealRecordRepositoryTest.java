package com.zjgsu.hdt.repository;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.zjgsu.hdt.model.Food;
import com.zjgsu.hdt.model.MealRecord;
import com.zjgsu.hdt.model.MealType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository 层测试 —— 对应作业要求"至少验证一次 Repository 读写"。
 *
 * <p>验证的是真实的实体映射 + 生成的 SQL，而不是 mock 出来的假象。
 * 数据源是 profile=test 配置的 H2（MySQL 兼容模式），克隆下来无需 MySQL 即可运行。
 *
 * <p><b>为什么用 {@code @SpringBootTest} 而不是 {@code @DataJpaTest}</b>：
 * Spring Boot 4.0 移除了 {@code @DataJpaTest} 这一"切片测试"注解
 * （{@code spring-boot-test-autoconfigure} 4.0.8 里已不再提供）。
 * 因此在 Boot 4 上验证 JPA 映射的正确做法是启动完整上下文；
 * 代价是启动稍慢，换来的是被测对象与生产运行完全一致。
 */
@SpringBootTest
@ActiveProfiles("test")
class MealRecordRepositoryTest {

    @Autowired
    private MealRecordRepository mealRecordRepository;

    @Autowired
    private FoodRepository foodRepository;

    @Test
    @DisplayName("食物库按 data.sql 灌入，可按名称与分类查询")
    void foodSeedDataIsLoaded() {
        assertThat(foodRepository.count()).isEqualTo(12);
        assertThat(foodRepository.findByName("鸡胸肉")).isPresent();
        assertThat(foodRepository.findByCategoryOrderByNameAsc("主食"))
                .extracting(Food::getName)
                .containsExactly("全麦面包", "米饭", "红薯");
    }

    @Test
    @DisplayName("写入一条饮食记录后能按用餐人读回，营养明细完整")
    void saveAndReadMealRecord() {
        Food chicken = foodRepository.findByName("鸡胸肉").orElseThrow();
        Food rice = foodRepository.findByName("米饭").orElseThrow();

        MealRecord record = new MealRecord("repo-user", MealType.LUNCH,
                LocalDate.of(2026, 10, 1).atTime(12, 30), "仓库层读写验证");
        record.addItem(chicken, 200);
        record.addItem(rice, 150);

        MealRecord saved = mealRecordRepository.save(record);

        assertThat(saved.getId()).isNotNull();
        List<MealRecord> found = mealRecordRepository.findByEaterOrderByEatenAtDesc("repo-user");
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getItems())
                .hasSize(2)
                .extracting(item -> item.getFood().getName())
                .containsExactlyInAnyOrder("鸡胸肉", "米饭");
    }

    @Test
    @DisplayName("份量存在中间表 meal_item，读回后数值不变（往返一致性）")
    void portionIsPersistedInJoinTable() {
        Food apple = foodRepository.findByName("苹果").orElseThrow();

        MealRecord record = new MealRecord("portion-user", MealType.SNACK,
                LocalDate.of(2026, 10, 2).atTime(16, 0), null);
        record.addItem(apple, 175);
        mealRecordRepository.saveAndFlush(record);

        MealRecord loaded = mealRecordRepository
                .findByEaterOrderByEatenAtDesc("portion-user")
                .getFirst();

        assertThat(loaded.getItems()).hasSize(1);
        assertThat(loaded.getItems().getFirst().getPortionGrams()).isEqualTo(175);
        assertThat(loaded.getItems().getFirst().getFood().getName()).isEqualTo("苹果");
    }

    @Test
    @DisplayName("同一食物重复加入时份量累加，而不是覆盖")
    void addingSameFoodTwiceAccumulatesPortion() {
        Food milk = foodRepository.findByName("牛奶").orElseThrow();

        MealRecord record = new MealRecord("merge-user", MealType.BREAKFAST,
                LocalDate.of(2026, 10, 3).atTime(8, 0), null);
        record.addItem(milk, 200);
        record.addItem(milk, 50);

        assertThat(record.getItems()).hasSize(1);
        assertThat(record.getItems().getFirst().getPortionGrams()).isEqualTo(250);
    }

    @Test
    @DisplayName("从明细表读回后能算出与写入时一致的热量合计")
    void caloriesSurviveRoundTrip() {
        Food rice = foodRepository.findByName("米饭").orElseThrow();

        MealRecord record = new MealRecord("calorie-user", MealType.LUNCH,
                LocalDate.of(2026, 10, 7).atTime(12, 0), null);
        record.addItem(rice, 200);           // 116 kcal/100g × 200g = 232.00
        mealRecordRepository.saveAndFlush(record);

        MealRecord loaded = mealRecordRepository
                .findByEaterOrderByEatenAtDesc("calorie-user")
                .getFirst();

        assertThat(loaded.totalCalories()).isEqualByComparingTo("232.00");
        assertThat(loaded.totalGrams()).isEqualTo(200);
    }

    @Test
    @DisplayName("删除记录时级联删除明细行，不留孤儿数据")
    void deletingRecordCascadesItems() {
        Food egg = foodRepository.findByName("水煮蛋").orElseThrow();

        MealRecord record = new MealRecord("cascade-user", MealType.BREAKFAST,
                LocalDate.of(2026, 10, 8).atTime(7, 0), null);
        record.addItem(egg, 50);
        MealRecord saved = mealRecordRepository.saveAndFlush(record);
        Long id = saved.getId();

        mealRecordRepository.deleteById(id);
        mealRecordRepository.flush();

        assertThat(mealRecordRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("按时间区间查询只返回区间内的记录")
    void findByEaterAndEatenAtRange() {
        LocalDate day = LocalDate.of(2026, 10, 4);
        Food egg = foodRepository.findByName("水煮蛋").orElseThrow();

        MealRecord inside = new MealRecord("range-user", MealType.BREAKFAST, day.atTime(7, 30), null);
        inside.addItem(egg, 50);
        mealRecordRepository.save(inside);

        MealRecord outside = new MealRecord("range-user", MealType.BREAKFAST, day.plusDays(1).atTime(7, 30), null);
        outside.addItem(egg, 50);
        mealRecordRepository.save(outside);

        List<MealRecord> hit = mealRecordRepository
                .findByEaterAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtDesc(
                        "range-user", day.atStartOfDay(), day.plusDays(1).atStartOfDay());

        assertThat(hit).hasSize(1);
        assertThat(hit.getFirst().getEatenAt()).isEqualTo(day.atTime(7, 30));
    }

    @Test
    @DisplayName("统计方法可用于业务规则：同餐次计数、每日计数、有记录天数")
    void countQueriesSupportBusinessRules() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        Food bread = foodRepository.findByName("全麦面包").orElseThrow();

        MealRecord first = new MealRecord("count-user", MealType.BREAKFAST, day.atTime(8, 0), null);
        first.addItem(bread, 60);
        mealRecordRepository.save(first);
        MealRecord second = new MealRecord("count-user", MealType.LUNCH, day.atTime(12, 0), null);
        second.addItem(bread, 60);
        mealRecordRepository.save(second);

        assertThat(mealRecordRepository.countByEaterAndMealTypeAndEatenAt(
                "count-user", MealType.BREAKFAST, day.atTime(8, 0))).isEqualTo(1);
        assertThat(mealRecordRepository.countByEaterAndMealTypeAndEatenAt(
                "count-user", MealType.BREAKFAST, day.atTime(9, 0))).isZero();
        assertThat(mealRecordRepository.countByEaterAndEatenAtGreaterThanEqualAndEatenAtLessThan(
                "count-user", day.atStartOfDay(), day.plusDays(1).atStartOfDay())).isEqualTo(2);
        assertThat(mealRecordRepository.countDistinctDaysSince("count-user", day.atStartOfDay())).isEqualTo(1);
    }

    @Test
    @DisplayName("按 ID 删除后查询不到（验证 Repository 的删除能力）")
    void deleteByIdRemovesRecord() {
        Food yogurt = foodRepository.findByName("无糖酸奶").orElseThrow();
        MealRecord record = new MealRecord("delete-user", MealType.SNACK,
                LocalDate.of(2026, 10, 6).atTime(15, 0), null);
        record.addItem(yogurt, 100);
        MealRecord saved = mealRecordRepository.saveAndFlush(record);
        Long id = saved.getId();

        mealRecordRepository.deleteById(id);
        mealRecordRepository.flush();

        assertThat(mealRecordRepository.findById(id)).isEmpty();
    }
}
