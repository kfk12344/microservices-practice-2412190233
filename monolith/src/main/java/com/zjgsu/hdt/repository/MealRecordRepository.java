package com.zjgsu.hdt.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zjgsu.hdt.model.MealRecord;
import com.zjgsu.hdt.model.MealType;

/**
 * 饮食记录的 Repository（Spring Data JPA）。
 *
 * <p>只声明查询意图，不写任何数据访问实现；方法名即查询语义，
 * 复杂一些的日用汇总用 {@code @Query} 显式表达。
 */
public interface MealRecordRepository extends JpaRepository<MealRecord, Long> {

    /**
     * 查询某人在一段时间内的全部用餐记录，按时间倒序。
     *
     * <p>日用汇总接口（{@code GET /api/meals/daily-summary}）主要依赖它。
     *
     * @param eater 用餐人
     * @param start 区间起点（含）
     * @param end   区间终点（不含）
     * @return 记录列表，时间新的在前
     */
    List<MealRecord> findByEaterAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtDesc(
            String eater, LocalDateTime start, LocalDateTime end);

    /**
     * 查询某人全部记录，最新在前。
     *
     * @param eater 用餐人
     * @return 记录列表
     */
    List<MealRecord> findByEaterOrderByEatenAtDesc(String eater);

    /**
     * 统计某人在某时间点的同餐次记录数，用于业务规则 R2 的重复记录检查。
     *
     * @param eater    用餐人
     * @param mealType 餐次
     * @param eatenAt  用餐时间
     * @return 已存在条数
     */
    long countByEaterAndMealTypeAndEatenAt(String eater, MealType mealType, LocalDateTime eatenAt);

    /**
     * 统计某人在一段时间内的记录数，用于业务规则 R5 的每日餐次上限检查。
     *
     * @param eater 用餐人
     * @param start 区间起点（含）
     * @param end   区间终点（不含）
     * @return 记录条数
     */
    long countByEaterAndEatenAtGreaterThanEqualAndEatenAtLessThan(
            String eater, LocalDateTime start, LocalDateTime end);

    /**
     * 统计某人在指定时间之后有多少个"有记录的日期"（按日期去重）。
     *
     * <p>这里演示 {@code @Query} 的用法：在数据库侧用 {@code DISTINCT} + 日期函数完成去重统计，
     * 避免把明细全部加载到内存。日用汇总的热量合计则在 Service 里用内存聚合完成——
     * 因为 {@code Map<Food, Integer>} 的键是实体，JPQL 里取该键需要 {@code KEY()} 语义，
     * 写成原生 SQL 或用内存聚合都比强凑 JPQL 更清晰、更好维护。
     *
     * @param eater 用餐人
     * @param since 起始时间（含）
     * @return 有记录的天数
     */
    @Query("""
            SELECT COUNT(DISTINCT FUNCTION('DATE', r.eatenAt))
            FROM MealRecord r
            WHERE r.eater = :eater AND r.eatenAt >= :since
            """)
    long countDistinctDaysSince(@Param("eater") String eater, @Param("since") LocalDateTime since);
}
