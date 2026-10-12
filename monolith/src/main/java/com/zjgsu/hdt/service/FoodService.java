package com.zjgsu.hdt.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zjgsu.hdt.dto.FoodResponse;
import com.zjgsu.hdt.model.Food;
import com.zjgsu.hdt.repository.FoodRepository;

/**
 * 食物库服务 —— 提供食物查询，供记录饮食时挑选，也是接口层 {@code /api/foods} 的实现。
 *
 * <p>Week 04 只做查询：食物库属于管理员维护的基准数据，
 * 增删改会牵动历史记录的营养依据（中间表有外键约束），因此留到后续"管理员模块"再实现。
 */
@Service
@Transactional(readOnly = true)
public class FoodService {

    private final FoodRepository foodRepository;

    public FoodService(FoodRepository foodRepository) {
        this.foodRepository = foodRepository;
    }

    /**
     * 查询食物库。
     *
     * @param keyword  名称关键字，为空则不过滤
     * @param category 分类，为空则不过滤
     * @return 食物列表，按名称排序
     */
    public List<FoodResponse> search(String keyword, String category) {
        List<Food> foods;
        if (keyword != null && !keyword.isBlank()) {
            foods = foodRepository.findByNameContainingIgnoreCaseOrderByNameAsc(keyword.trim());
        } else if (category != null && !category.isBlank()) {
            foods = foodRepository.findByCategoryOrderByNameAsc(category.trim());
        } else {
            foods = foodRepository.findAll().stream()
                    .sorted(java.util.Comparator.comparing(Food::getName))
                    .toList();
        }
        return foods.stream().map(FoodResponse::from).toList();
    }

    /**
     * 查询全部食物分类（去重后按字典序），用于前端筛选下拉框。
     *
     * @return 分类名列表
     */
    public List<String> listCategories() {
        return foodRepository.findAll().stream()
                .map(Food::getCategory)
                .distinct()
                .sorted()
                .toList();
    }
}
