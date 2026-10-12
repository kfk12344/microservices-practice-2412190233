package com.zjgsu.hdt.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * 饮食记录服务 —— Week 04 全部业务规则的唯一实现处。
 *
 * <p>Controller 只负责 HTTP 输入输出，不写任何数据访问与规则判断，规则全部收在这里，
 * 好处是：规则可以被单元测试直接调用（不必起 Web 容器），也不会出现"两个接口各自实现了半条规则"。
 *
 * <h2>关键路径：记录一餐（{@link #create}）</h2>
 * <ol>
 *   <li>把请求体里的食物 ID 批量查出实体，一次性报出所有不存在的 ID（R1）；</li>
 *   <li>逐条检查份量与种类数上限（R1、R4）；</li>
 *   <li>检查用餐时间与餐次是否相符（R2）；</li>
 *   <li>检查同一时间是否已有同餐次记录（R2 的重复提交保护）；</li>
 *   <li>换算总热量，超过单餐上限则拒绝（R3）；</li>
 *   <li>检查当天餐次数量上限（R5）；</li>
 *   <li>全部通过后落库，并把算好的营养合计返回。</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class MealRecordService {

    private final MealRecordRepository mealRecordRepository;
    private final FoodRepository foodRepository;

    public MealRecordService(MealRecordRepository mealRecordRepository, FoodRepository foodRepository) {
        this.mealRecordRepository = mealRecordRepository;
        this.foodRepository = foodRepository;
    }

    /**
     * 记录一餐：校验全部业务规则后持久化。
     *
     * @param request 创建请求
     * @return 落库后的记录（含服务端算好的营养合计）
     * @throws BusinessRuleException 任一业务规则不满足
     */
    @Transactional
    public MealRecordResponse create(MealRecordRequest request) {
        // 规则 R1：食物必须存在，且份量为正且在合理范围内
        Map<Long, Food> foods = loadFoods(request.items());
        int totalPortion = 0;
        for (MealItemRequest item : request.items()) {
            if (item.portionGrams() > BusinessLimits.MAX_PORTION_GRAMS) {
                throw new BusinessRuleException("R1",
                        "食物「%s」的份量 %d 克超过单种食物上限 %d 克，请确认是否多打了一个 0"
                                .formatted(foods.get(item.foodId()).getName(),
                                        item.portionGrams(), BusinessLimits.MAX_PORTION_GRAMS));
            }
            totalPortion += item.portionGrams();
        }

        // 规则 R4：一次用餐的食物种类数上限
        if (request.items().size() > BusinessLimits.MAX_FOOD_VARIETIES_PER_MEAL) {
            throw new BusinessRuleException("R4",
                    "一次用餐最多包含 %d 种食物，当前为 %d 种，建议拆成多条记录"
                            .formatted(BusinessLimits.MAX_FOOD_VARIETIES_PER_MEAL, request.items().size()));
        }

        // 规则 R2：用餐时间必须落在所选餐次的时间窗内
        LocalTime clock = request.eatenAt().toLocalTime();
        if (!request.mealType().covers(clock)) {
            throw new BusinessRuleException("R2",
                    "用餐时间 %s 不在「%s」的合理时间窗 %s 内，请改用与时间相符的餐次"
                            .formatted(clock, request.mealType().getLabel(), request.mealType().getWindowText()));
        }

        // 规则 R2 延伸：完全相同的"人 + 餐次 + 时间"视为重复提交
        long duplicated = mealRecordRepository.countByEaterAndMealTypeAndEatenAt(
                request.eater(), request.mealType(), request.eatenAt());
        if (duplicated > 0) {
            throw new BusinessRuleException("R2",
                    "%s 在 %s 已有一条「%s」记录，请勿重复提交；如需修改请使用已有记录"
                            .formatted(request.eater(), request.eatenAt(), request.mealType().getLabel()));
        }

        // 组装实体
        MealRecord record = new MealRecord(
                request.eater(), request.mealType(), request.eatenAt(), request.note());
        for (MealItemRequest item : request.items()) {
            record.addItem(foods.get(item.foodId()), item.portionGrams());
        }

        // 规则 R3：单次用餐热量上限
        var calories = record.totalCalories();
        if (calories.compareTo(java.math.BigDecimal.valueOf(BusinessLimits.MAX_CALORIES_PER_MEAL)) > 0) {
            throw new BusinessRuleException("R3",
                    "本次用餐热量 %s kcal 超过单餐上限 %d kcal，请调整份量或拆分为多条记录"
                            .formatted(calories, BusinessLimits.MAX_CALORIES_PER_MEAL));
        }

        // 规则 R5：当天餐次数量上限
        LocalDate day = request.eatenAt().toLocalDate();
        long todayCount = mealRecordRepository
                .countByEaterAndEatenAtGreaterThanEqualAndEatenAtLessThan(
                        request.eater(), day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        if (todayCount >= BusinessLimits.MAX_MEALS_PER_DAY) {
            throw new BusinessRuleException("R5",
                    "%s 在 %s 已有 %d 条用餐记录，达到每日上限 %d 条"
                            .formatted(request.eater(), day, todayCount, BusinessLimits.MAX_MEALS_PER_DAY));
        }

        return MealRecordResponse.from(mealRecordRepository.save(record));
    }

    /**
     * 查询某人的全部饮食记录，最新的在前。
     *
     * @param eater 用餐人
     * @return 记录列表
     */
    public List<MealRecordResponse> findByEater(String eater) {
        return mealRecordRepository.findByEaterOrderByEatenAtDesc(eater).stream()
                .map(MealRecordResponse::from)
                .toList();
    }

    /**
     * 按 ID 查询一条记录。
     *
     * @param id 记录 ID
     * @return 记录
     * @throws ResourceNotFoundException 记录不存在（由全局异常处理映射为 404）
     */
    public MealRecordResponse findById(Long id) {
        MealRecord record = mealRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("找不到 ID 为 " + id + " 的饮食记录"));
        return MealRecordResponse.from(record);
    }

    /**
     * 查询某人指定日期的全部记录（内部方法，供日用汇总复用）。
     *
     * @param eater 用餐人
     * @param date  日期
     * @return 记录列表，时间新的在前
     */
    List<MealRecord> findEntitiesByDate(String eater, LocalDate date) {
        return mealRecordRepository.findByEaterAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtDesc(
                eater, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    /**
     * 按 ID 批量加载食物，缺失的 ID 一次性报出，避免逐个查询导致 N+1。
     *
     * @param items 请求里的食物项
     * @return 食物 ID 到实体的映射
     * @throws BusinessRuleException 存在不存在的食物 ID
     */
    private Map<Long, Food> loadFoods(List<MealItemRequest> items) {
        List<Long> ids = items.stream().map(MealItemRequest::foodId).distinct().toList();
        Map<Long, Food> found = foodRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Food::getId, food -> food));

        List<Long> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("R1",
                    "食物库中不存在以下 ID：%s，请先通过 GET /api/foods 确认可用食物".formatted(missing));
        }
        return found;
    }

    /** 暴露给测试与文档的规则说明。 */
    public static Map<String, String> rulebook() {
        return Map.of(
                "R1", "食物必须存在于食物库，份量为正且不超过 " + BusinessLimits.MAX_PORTION_GRAMS + " 克",
                "R2", "用餐时间必须落在所选餐次的时间窗内，且同一时间不允许重复提交同餐次记录",
                "R3", "单次用餐热量不得超过 " + BusinessLimits.MAX_CALORIES_PER_MEAL + " kcal",
                "R4", "单次用餐最多包含 " + BusinessLimits.MAX_FOOD_VARIETIES_PER_MEAL + " 种食物",
                "R5", "同一天最多记录 " + BusinessLimits.MAX_MEALS_PER_DAY + " 餐"
        );
    }

    /** 供 Controller 与文档使用的餐次时间窗说明。 */
    public static Map<String, String> mealTypeWindows() {
        Map<String, String> windows = new java.util.LinkedHashMap<>();
        for (MealType type : MealType.values()) {
            windows.put(type.getLabel(), type.getWindowText());
        }
        return windows;
    }
}
