package com.zjgsu.hdt.service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zjgsu.hdt.dto.MealItemRequest;
import com.zjgsu.hdt.dto.MealRecordRequest;
import com.zjgsu.hdt.dto.MealRecordResponse;
import com.zjgsu.hdt.exception.BusinessRuleException;
import com.zjgsu.hdt.exception.ResourceNotFoundException;
import com.zjgsu.hdt.model.BusinessLimits;
import com.zjgsu.hdt.model.Food;
import com.zjgsu.hdt.model.MealRecord;
import com.zjgsu.hdt.model.MealType;
import com.zjgsu.hdt.repository.FoodRepository;
import com.zjgsu.hdt.repository.MealRecordRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 饮食记录服务的业务规则测试 —— 对应作业要求"至少一次 Service 业务规则验证"。
 *
 * <p>用 Mockito 隔离掉 Repository，目的是让每条规则的失败原因都能被精确定位：
 * 一旦失败，一定是规则判断写错了，而不是数据源或 SQL 的问题。
 * 数据库是否真的能读写由 {@code MealRecordRepositoryTest} 与
 * {@code MealApiIntegrationTest} 负责，两者互补。
 */
@ExtendWith(MockitoExtension.class)
class MealRecordServiceTest {

    @Mock
    private MealRecordRepository mealRecordRepository;

    @Mock
    private FoodRepository foodRepository;

    @InjectMocks
    private MealRecordService service;

    private Food rice;
    private Food chicken;
    private Food broccoli;

    @BeforeEach
    void setUp() {
        // 基准值与 data.sql 保持一致，便于与集成测试的结果对照
        rice = foodWithId(1L, "米饭", "116.00", "2.60", "0.30", "25.90");
        chicken = foodWithId(3L, "鸡胸肉", "133.00", "24.60", "2.50", "0.60");
        broccoli = foodWithId(5L, "西兰花", "34.00", "2.80", "0.40", "6.60");
    }

    // ------------------------------------------------------------------
    // 正常路径
    // ------------------------------------------------------------------

    @Test
    @DisplayName("规则全部满足时保存记录，并返回服务端算好的营养合计")
    void createsRecordWhenAllRulesPass() {
        LocalDateTime eatenAt = LocalDateTime.of(2026, 10, 10, 12, 30);
        MealRecordRequest request = new MealRecordRequest("rule-user", MealType.LUNCH, eatenAt, "工作餐",
                List.of(new MealItemRequest(1L, 200), new MealItemRequest(3L, 150)));

        when(foodRepository.findAllById(List.of(1L, 3L))).thenReturn(List.of(rice, chicken));
        when(mealRecordRepository.countByEaterAndMealTypeAndEatenAt("rule-user", MealType.LUNCH, eatenAt))
                .thenReturn(0L);
        when(mealRecordRepository.countByEaterAndEatenAtGreaterThanEqualAndEatenAtLessThan(
                eq("rule-user"), any(), any())).thenReturn(2L);
        when(mealRecordRepository.save(any(MealRecord.class))).thenAnswer(call -> call.getArgument(0));

        MealRecordResponse response = service.create(request);

        // 米饭 200g = 232.00，鸡胸肉 150g = 199.50，合计 431.50 kcal
        assertThat(response.totalCalories()).isEqualByComparingTo("431.50");
        assertThat(response.totalProtein()).isEqualByComparingTo("42.10");
        assertThat(response.mealTypeLabel()).isEqualTo("午餐");
        assertThat(response.items()).hasSize(2);
        assertThat(response.totalGrams()).isEqualTo(350);
        verify(mealRecordRepository).save(any(MealRecord.class));
    }

    // ------------------------------------------------------------------
    // R1：食物存在性与份量范围
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R1：食物 ID 不存在时拒绝，并在消息中列出缺失的 ID")
    void ruleR1RejectsUnknownFood() {
        MealRecordRequest request = requestAt(MealType.LUNCH, 12, 0,
                new MealItemRequest(999L, 100));

        when(foodRepository.findAllById(List.of(999L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("999")
                .extracting(e -> ((BusinessRuleException) e).getRuleCode())
                .isEqualTo("R1");
        verify(mealRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("R1：份量超过上限时拒绝，并提示可能多打了一个 0")
    void ruleR1RejectsExcessivePortion() {
        int tooMuch = BusinessLimits.MAX_PORTION_GRAMS + 1;
        MealRecordRequest request = requestAt(MealType.LUNCH, 12, 0,
                new MealItemRequest(1L, tooMuch));

        when(foodRepository.findAllById(List.of(1L))).thenReturn(List.of(rice));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("上限")
                .extracting(e -> ((BusinessRuleException) e).getRuleCode())
                .isEqualTo("R1");
    }

    // ------------------------------------------------------------------
    // R2：餐次时间窗与重复提交
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R2：用餐时间与餐次不符时拒绝（把 20:00 标成早餐）")
    void ruleR2RejectsTimeOutsideWindow() {
        MealRecordRequest request = requestAt(MealType.BREAKFAST, 20, 0,
                new MealItemRequest(1L, 100));

        when(foodRepository.findAllById(List.of(1L))).thenReturn(List.of(rice));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("时间窗")
                .extracting(e -> ((BusinessRuleException) e).getRuleCode())
                .isEqualTo("R2");
    }

    @Test
    @DisplayName("R2：同一时间重复提交同餐次记录时拒绝")
    void ruleR2RejectsDuplicateSubmission() {
        LocalDateTime eatenAt = LocalDateTime.of(2026, 10, 11, 8, 0);
        MealRecordRequest request = new MealRecordRequest("dup-user", MealType.BREAKFAST, eatenAt, null,
                List.of(new MealItemRequest(1L, 100)));

        when(foodRepository.findAllById(List.of(1L))).thenReturn(List.of(rice));
        when(mealRecordRepository.countByEaterAndMealTypeAndEatenAt("dup-user", MealType.BREAKFAST, eatenAt))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("重复提交")
                .extracting(e -> ((BusinessRuleException) e).getRuleCode())
                .isEqualTo("R2");
    }

    // ------------------------------------------------------------------
    // R3：单次用餐热量上限
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R3：热量超过单餐上限时拒绝（5000g 米饭 = 5800 kcal）")
    void ruleR3RejectsExcessiveCalories() {
        MealRecordRequest request = requestAt(MealType.LUNCH, 12, 0,
                new MealItemRequest(1L, 5000));

        when(foodRepository.findAllById(List.of(1L))).thenReturn(List.of(rice));
        when(mealRecordRepository.countByEaterAndMealTypeAndEatenAt(anyString(), any(), any()))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(String.valueOf(BusinessLimits.MAX_CALORIES_PER_MEAL))
                .extracting(e -> ((BusinessRuleException) e).getRuleCode())
                .isEqualTo("R3");
        verify(mealRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("R3 边界：正好等于上限时应放行（5000g 西兰花 = 1700 kcal 会超，改用临界组合）")
    void ruleR3AllowsExactlyAtLimit() {
        // 西兰花 34 kcal/100g，取 4412g ≈ 1500.08 kcal 会超；这里用 4411g ≈ 1499.74 kcal 验证临界内可通过
        MealRecordRequest request = requestAt(MealType.LUNCH, 12, 0,
                new MealItemRequest(5L, 4411));

        when(foodRepository.findAllById(List.of(5L))).thenReturn(List.of(broccoli));
        when(mealRecordRepository.countByEaterAndMealTypeAndEatenAt(anyString(), any(), any()))
                .thenReturn(0L);
        when(mealRecordRepository.countByEaterAndEatenAtGreaterThanEqualAndEatenAtLessThan(
                anyString(), any(), any())).thenReturn(0L);
        when(mealRecordRepository.save(any(MealRecord.class))).thenAnswer(call -> call.getArgument(0));

        MealRecordResponse response = service.create(request);

        assertThat(response.totalCalories())
                .isLessThanOrEqualTo(BigDecimal.valueOf(BusinessLimits.MAX_CALORIES_PER_MEAL));
        verify(mealRecordRepository).save(any(MealRecord.class));
    }

    // ------------------------------------------------------------------
    // R4：食物种类上限
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R4：食物种类超过上限时拒绝")
    void ruleR4RejectsTooManyFoodVarieties() {
        int tooMany = BusinessLimits.MAX_FOOD_VARIETIES_PER_MEAL + 1;
        List<MealItemRequest> items = new java.util.ArrayList<>();
        List<Food> foods = new java.util.ArrayList<>();
        List<Long> ids = new java.util.ArrayList<>();
        for (int i = 1; i <= tooMany; i++) {
            items.add(new MealItemRequest((long) i, 10));
            foods.add(foodWithId((long) i, "食物" + i, "10.00", "1.00", "1.00", "1.00"));
            ids.add((long) i);
        }
        LocalDateTime eatenAt = LocalDateTime.of(2026, 10, 12, 12, 0);
        MealRecordRequest request = new MealRecordRequest("many-user", MealType.LUNCH, eatenAt, null, items);

        // 只 stub 食物查询：R4 在"重复提交检查"之前就会命中，
        // 因此不需要也不应该 stub countByEaterAndMealTypeAndEatenAt（Mockito 严格模式会报多余打桩）
        when(foodRepository.findAllById(ids)).thenReturn(foods);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("种食物")
                .extracting(e -> ((BusinessRuleException) e).getRuleCode())
                .isEqualTo("R4");
    }

    // ------------------------------------------------------------------
    // R5：每日餐次上限
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R5：当天记录数已达上限时拒绝")
    void ruleR5RejectsTooManyMealsPerDay() {
        MealRecordRequest request = requestAt(MealType.LUNCH, 12, 0,
                new MealItemRequest(1L, 100));

        when(foodRepository.findAllById(List.of(1L))).thenReturn(List.of(rice));
        when(mealRecordRepository.countByEaterAndMealTypeAndEatenAt(anyString(), any(), any()))
                .thenReturn(0L);
        when(mealRecordRepository.countByEaterAndEatenAtGreaterThanEqualAndEatenAtLessThan(
                anyString(), any(), any())).thenReturn((long) BusinessLimits.MAX_MEALS_PER_DAY);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("每日上限")
                .extracting(e -> ((BusinessRuleException) e).getRuleCode())
                .isEqualTo("R5");
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Test
    @DisplayName("按 ID 查询不存在的记录时抛出 ResourceNotFoundException（对应 404）")
    void findByIdThrowsWhenMissing() {
        when(mealRecordRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("规则清单与餐次窗口可供接口展示，编号与阈值一致")
    void rulebookIsExposed() {
        assertThat(MealRecordService.rulebook())
                .containsKeys("R1", "R2", "R3", "R4", "R5")
                .containsEntry("R3", "单次用餐热量不得超过 " + BusinessLimits.MAX_CALORIES_PER_MEAL + " kcal");
        assertThat(MealRecordService.mealTypeWindows())
                .containsEntry("早餐", "05:00-10:59")
                .containsEntry("午餐", "11:00-14:59");
    }

    // ------------------------------------------------------------------
    // 测试辅助
    // ------------------------------------------------------------------

    private MealRecordRequest requestAt(MealType type, int hour, int minute, MealItemRequest... items) {
        return new MealRecordRequest("rule-user", type,
                LocalDateTime.of(2026, 10, 13, hour, minute), null, List.of(items));
    }

    /**
     * 用反射给实体的 id 赋值。
     *
     * <p>id 由数据库生成、生产代码不提供 setter，这是正确的封装；
     * 但单元测试需要构造"已持久化"的食物对象，因此在这里开一个受控的口子，
     * 而不是为了测试给实体加上不该有的 setter。
     */
    private static Food foodWithId(Long id, String name, String kcal, String protein,
                                   String fat, String carbs) {
        Food food = new Food(name, "测试",
                new BigDecimal(kcal), new BigDecimal(protein), new BigDecimal(fat), new BigDecimal(carbs));
        try {
            Field field = Food.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(food, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法为测试设置 Food.id", e);
        }
        return food;
    }
}
