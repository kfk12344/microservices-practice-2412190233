package com.zjgsu.hdt.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zjgsu.hdt.model.Food;

/**
 * 食物库的 Repository（Spring Data JPA）。
 */
public interface FoodRepository extends JpaRepository<Food, Long> {

    /**
     * 按名称精确查找食物（名称在库中唯一）。
     *
     * @param name 食物名称
     * @return 找到的食物
     */
    Optional<Food> findByName(String name);

    /**
     * 名称包含关键字（忽略大小写）的食物，按名称排序。
     *
     * @param keyword 关键字
     * @return 匹配列表
     */
    List<Food> findByNameContainingIgnoreCaseOrderByNameAsc(String keyword);

    /**
     * 按分类查找食物。
     *
     * @param category 分类名
     * @return 该分类下的食物列表
     */
    List<Food> findByCategoryOrderByNameAsc(String category);

    /** 判断名称是否已存在，用于新增食物时避免违反唯一约束。 */
    boolean existsByName(String name);
}
