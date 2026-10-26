package com.zjgsu.hdt.meal.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.zjgsu.hdt.meal.model.MealRecord;

/**
 * 饮食记录 Repository。只操作本服务的 {@code meal_record} / {@code meal_item}。
 *
 * <p>用 {@code @EntityGraph} 一次性把明细抓出来，避免 N+1 查询
 * （否则 N 条记录会额外产生 N 条取明细的 SQL）。
 */
public interface MealRecordRepository extends JpaRepository<MealRecord, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<MealRecord> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    List<MealRecord> findByUserIdOrderByEatenAtDesc(Long userId);

    @EntityGraph(attributePaths = "items")
    List<MealRecord> findByUserIdAndEatenAtBetweenOrderByEatenAtAsc(
            Long userId, LocalDateTime from, LocalDateTime to);

    long countByUserIdAndEatenAtBetween(Long userId, LocalDateTime from, LocalDateTime to);
}
