package com.zjgsu.hdt.meal.model;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 一条记录里的一种食物及其份量。
 *
 * <p><strong>{@code foodId} 只是普通列，没有指向 food 表的外键</strong> ——
 * 食物数据在 food-service 的库里，物理上不可达。
 *
 * <p>同时保存 {@code foodName} 与四项营养值的<strong>快照</strong>：
 * 这样查询记录时不必再逐条调用 food-service（一条记录 N 种食物就是 N 次 HTTP），
 * 而且历史记录展示的应该是当时的营养值。代价是食物库改值后旧记录不跟着变 ——
 * 对"饮食记录"这个业务，这反而是正确的语义。
 */
@Entity
@Table(name = "meal_item")
public class MealItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_record_id", nullable = false)
    private MealRecord mealRecord;

    /** 指向 food-service 的食物 ID。 */
    @Column(name = "food_id", nullable = false)
    private Long foodId;

    /** 食物名称快照，避免查询时跨服务调用。 */
    @Column(name = "food_name", nullable = false, length = 64)
    private String foodName;

    /** 份量（克）。 */
    @Column(name = "portion_grams", nullable = false)
    private Integer portionGrams;

    /** 本份量的热量（kcal），已按份量折算并落库。 */
    @Column(name = "calories", nullable = false, precision = 10, scale = 2)
    private BigDecimal calories;

    @Column(name = "protein", nullable = false, precision = 10, scale = 2)
    private BigDecimal protein;

    @Column(name = "fat", nullable = false, precision = 10, scale = 2)
    private BigDecimal fat;

    @Column(name = "carbs", nullable = false, precision = 10, scale = 2)
    private BigDecimal carbs;

    /** JPA 要求的无参构造器。 */
    protected MealItem() {
    }

    public MealItem(Long foodId, String foodName, Integer portionGrams,
                    BigDecimal calories, BigDecimal protein, BigDecimal fat, BigDecimal carbs) {
        this.foodId = foodId;
        this.foodName = foodName;
        this.portionGrams = portionGrams;
        this.calories = calories;
        this.protein = protein;
        this.fat = fat;
        this.carbs = carbs;
    }

    public Long getId() {
        return id;
    }

    public MealRecord getMealRecord() {
        return mealRecord;
    }

    void setMealRecord(MealRecord mealRecord) {
        this.mealRecord = mealRecord;
    }

    public Long getFoodId() {
        return foodId;
    }

    public String getFoodName() {
        return foodName;
    }

    public Integer getPortionGrams() {
        return portionGrams;
    }

    public BigDecimal getCalories() {
        return calories;
    }

    public BigDecimal getProtein() {
        return protein;
    }

    public BigDecimal getFat() {
        return fat;
    }

    public BigDecimal getCarbs() {
        return carbs;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MealItem that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "MealItem{foodId=" + foodId + ", foodName='" + foodName
                + "', portionGrams=" + portionGrams + ", calories=" + calories + "}";
    }
}
