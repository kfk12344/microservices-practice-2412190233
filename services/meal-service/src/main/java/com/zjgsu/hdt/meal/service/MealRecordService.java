package com.zjgsu.hdt.meal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zjgsu.hdt.meal.client.FoodClient;
import com.zjgsu.hdt.meal.client.FoodInfo;
import com.zjgsu.hdt.meal.client.UserClient;
import com.zjgsu.hdt.meal.dto.MealItemRequest;
import com.zjgsu.hdt.meal.dto.MealRequest;
import com.zjgsu.hdt.meal.dto.MealResponse;
import com.zjgsu.hdt.meal.exception.BusinessRuleException;
import com.zjgsu.hdt.meal.exception.ResourceNotFoundException;
import com.zjgsu.hdt.meal.model.MealItem;
import com.zjgsu.hdt.meal.model.MealRecord;
import com.zjgsu.hdt.meal.model.MealType;
import com.zjgsu.hdt.meal.repository.MealRecordRepository;

/**
 * 饮食记录业务逻辑 —— 本项目的<strong>跨服务业务流程</strong>就在这里。
 *
 * <p>处理顺序刻意设计成"先校验、后写库"，任何一步失败都不会留下本地数据：
 * <ol>
 *   <li>跨服务校验用户存在（user-service）</li>
 *   <li>跨服务逐个校验食物存在并取营养值（food-service）</li>
 *   <li>按份量折算营养值</li>
 *   <li>业务规则校验（单餐热量上限）</li>
 *   <li>全部通过后才写 {@code meal_record} / {@code meal_item}</li>
 * </ol>
 *
 * <p><strong>绝不"跳过校验继续写"</strong>：依赖服务不可用时直接抛
 * {@link com.zjgsu.hdt.meal.exception.DependencyUnavailableException}（503），
 * 依赖资源不存在时抛 {@link com.zjgsu.hdt.meal.exception.DependencyResourceNotFoundException}（400），
 * 两者都在写库之前中断。
 */
@Service
public class MealRecordService {

    /** 单次用餐热量上限（kcal），沿用第 04 周的业务规则 R3。 */
    public static final int MAX_SINGLE_MEAL_KCAL = 1500;

    /** 规则编号，写进错误信息便于对照文档。 */
    private static final String RULE_SINGLE_MEAL_KCAL = "R3";

    private final MealRecordRepository mealRecordRepository;
    private final UserClient userClient;
    private final FoodClient foodClient;

    public MealRecordService(MealRecordRepository mealRecordRepository,
                             UserClient userClient,
                             FoodClient foodClient) {
        this.mealRecordRepository = mealRecordRepository;
        this.userClient = userClient;
        this.foodClient = foodClient;
    }

    /**
     * 记录一餐（跨服务流程）。
     *
     * <p>注意：{@code @Transactional} 包住整个方法，但 JPA 的数据库连接是在<strong>首次</strong>
     * 访问数据库时才获取的，而这里的 HTTP 调用都发生在第一次 {@code save} 之前，
     * 因此不会"拿着数据库连接等网络"。
     */
    @Transactional
    public MealResponse create(MealRequest request) {
        MealType mealType = MealType.from(request.mealType());

        // ---- ① 跨服务校验用户；不存在 → 400，不可用 → 503，都不写本地 ----
        userClient.requireUser(request.userId());

        // ---- ② 跨服务逐个校验食物并取每 100 克营养值 ----
        List<ResolvedItem> resolved = new ArrayList<>(request.items().size());
        for (MealItemRequest itemRequest : request.items()) {
            FoodInfo food = foodClient.requireFood(itemRequest.foodId());
            resolved.add(new ResolvedItem(itemRequest, food));
        }

        // ---- ③ 按份量折算，并算出合计 ----
        List<MealItem> items = new ArrayList<>(resolved.size());
        BigDecimal totalCalories = BigDecimal.ZERO;
        for (ResolvedItem entry : resolved) {
            BigDecimal grams = BigDecimal.valueOf(entry.request().portionGrams());
            BigDecimal calories = scale(entry.food().caloriesPer100g(), grams);
            items.add(new MealItem(
                    entry.food().id(),
                    entry.food().name(),
                    entry.request().portionGrams(),
                    calories,
                    scale(entry.food().proteinPer100g(), grams),
                    scale(entry.food().fatPer100g(), grams),
                    scale(entry.food().carbsPer100g(), grams)
            ));
            totalCalories = totalCalories.add(calories);
        }

        // ---- ④ 业务规则：单餐热量上限 ----
        if (totalCalories.compareTo(BigDecimal.valueOf(MAX_SINGLE_MEAL_KCAL)) > 0) {
            throw new BusinessRuleException(RULE_SINGLE_MEAL_KCAL,
                    "本次用餐热量 " + totalCalories.toPlainString() + " kcal 超过单餐上限 "
                            + MAX_SINGLE_MEAL_KCAL + " kcal，请调整份量或拆分为多条记录");
        }

        // ---- ⑤ 全部校验通过，才写入本地库 ----
        MealRecord record = new MealRecord(
                request.userId(),
                mealType,
                request.eatenAt(),
                request.note(),
                LocalDateTime.now()
        );
        items.forEach(record::addItem);
        return MealResponse.from(mealRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public MealResponse findById(Long id) {
        MealRecord record = mealRecordRepository.findWithItemsById(id)
                .orElseThrow(() -> ResourceNotFoundException.mealRecord(id));
        return MealResponse.from(record);
    }

    @Transactional(readOnly = true)
    public List<MealResponse> findByUserId(Long userId) {
        return mealRecordRepository.findByUserIdOrderByEatenAtDesc(userId).stream()
                .map(MealResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countByUserIdOnDay(Long userId, LocalDateTime dayStart) {
        return mealRecordRepository.countByUserIdAndEatenAtBetween(
                userId, dayStart, dayStart.plusDays(1).minusNanos(1));
    }

    /** 每 100 克营养值 → 实际份量的营养值，四舍五入保留 2 位。 */
    private static BigDecimal scale(BigDecimal per100g, BigDecimal grams) {
        if (per100g == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return per100g.multiply(grams)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** 内部用的中间结构：请求里的明细 + 从 food-service 拿到的营养数据。 */
    private record ResolvedItem(MealItemRequest request, FoodInfo food) {
    }
}
