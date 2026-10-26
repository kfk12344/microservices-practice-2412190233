package com.zjgsu.hdt.food.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zjgsu.hdt.food.dto.FoodRequest;
import com.zjgsu.hdt.food.dto.FoodResponse;
import com.zjgsu.hdt.food.exception.ResourceNotFoundException;
import com.zjgsu.hdt.food.model.Food;
import com.zjgsu.hdt.food.repository.FoodRepository;

/**
 * 食物库业务逻辑。
 *
 * <p>只做"营养基准数据"的增查。不关心饮食记录，也不做份量折算 ——
 * 折算属于 meal-service 的职责，那边拿到每 100 克的值后自己乘。
 */
@Service
public class FoodService {

    private final FoodRepository foodRepository;

    public FoodService(FoodRepository foodRepository) {
        this.foodRepository = foodRepository;
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> listAll(String category) {
        List<Food> foods = (category == null || category.isBlank())
                ? foodRepository.findAllByOrderByIdAsc()
                : foodRepository.findByCategoryOrderByIdAsc(category);
        return foods.stream().map(FoodResponse::from).toList();
    }

    /**
     * 按 ID 查询食物。
     *
     * <p>meal-service 记录一餐时对每个 foodId 调用本方法：
     * 查不到就返回 404，让 meal-service 明确知道"食物不存在"，
     * 从而拒绝写入，而不是记一条只有 ID 的空记录。
     */
    @Transactional(readOnly = true)
    public FoodResponse findById(Long id) {
        Food food = foodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("食物不存在：id=" + id));
        return FoodResponse.from(food);
    }

    @Transactional
    public FoodResponse create(FoodRequest request) {
        if (foodRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("食物已存在：" + request.name());
        }
        Food food = new Food(
                request.name(),
                request.category(),
                request.caloriesPer100g(),
                request.proteinPer100g(),
                request.fatPer100g(),
                request.carbsPer100g()
        );
        return FoodResponse.from(foodRepository.save(food));
    }
}
