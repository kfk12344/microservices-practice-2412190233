package com.zjgsu.hdt.food.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zjgsu.hdt.food.model.Food;

/**
 * 食物 Repository。只操作本服务的 {@code food} 表。
 */
public interface FoodRepository extends JpaRepository<Food, Long> {

    Optional<Food> findByName(String name);

    boolean existsByName(String name);

    List<Food> findByCategoryOrderByIdAsc(String category);

    List<Food> findAllByOrderByIdAsc();
}
