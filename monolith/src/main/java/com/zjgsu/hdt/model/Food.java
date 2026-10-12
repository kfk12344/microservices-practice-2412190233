package com.zjgsu.hdt.model;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 食物（食物库条目）—— Week 04 的核心实体之一。
 *
 * <p>营养数据一律按 <b>每 100 克</b> 存储（{@link #nutrientsPer100g}），
 * 实际摄入量 = 每 100 克营养值 × 份量 / 100，由
 * {@link com.zjgsu.hdt.service.NutritionCalculator} 统一换算。
 * 这样设计的原因：食物库只需维护一份基准数据，任何份量都能算出来，
 * 不必为每种份量预存一行。
 *
 * <p>与 {@link MealRecord} 是多对多关系，份量存放在中间表 {@code meal_item} 中。
 */
@Entity
@Table(
        name = "food",
        uniqueConstraints = @UniqueConstraint(name = "uk_food_name", columnNames = "name")
)
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 食物名称，全库唯一，避免同一食物出现多条口径不一的记录。 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 分类：主食 / 蛋白质 / 蔬菜 / 水果 / 饮品 / 零食 等，便于按类查询。 */
    @Column(name = "category", nullable = false, length = 32)
    private String category;

    /** 每 100 克热量（kcal）。 */
    @Column(name = "calories_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal caloriesPer100g;

    /** 每 100 克蛋白质（g）。 */
    @Column(name = "protein_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal proteinPer100g;

    /** 每 100 克脂肪（g）。 */
    @Column(name = "fat_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal fatPer100g;

    /** 每 100 克碳水化合物（g）。 */
    @Column(name = "carbs_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal carbsPer100g;

    /** JPA 要求的无参构造器。 */
    protected Food() {
    }

    public Food(String name, String category,
                BigDecimal caloriesPer100g, BigDecimal proteinPer100g,
                BigDecimal fatPer100g, BigDecimal carbsPer100g) {
        this.name = name;
        this.category = category;
        this.caloriesPer100g = caloriesPer100g;
        this.proteinPer100g = proteinPer100g;
        this.fatPer100g = fatPer100g;
        this.carbsPer100g = carbsPer100g;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getCaloriesPer100g() {
        return caloriesPer100g;
    }

    public void setCaloriesPer100g(BigDecimal caloriesPer100g) {
        this.caloriesPer100g = caloriesPer100g;
    }

    public BigDecimal getProteinPer100g() {
        return proteinPer100g;
    }

    public void setProteinPer100g(BigDecimal proteinPer100g) {
        this.proteinPer100g = proteinPer100g;
    }

    public BigDecimal getFatPer100g() {
        return fatPer100g;
    }

    public void setFatPer100g(BigDecimal fatPer100g) {
        this.fatPer100g = fatPer100g;
    }

    public BigDecimal getCarbsPer100g() {
        return carbsPer100g;
    }

    public void setCarbsPer100g(BigDecimal carbsPer100g) {
        this.carbsPer100g = carbsPer100g;
    }

    /**
     * 实体相等性只按主键判断，且主键为空（尚未持久化）的两个对象互不相等。
     * 这一点对放在 {@code Map<Food, Integer>} 里做键很关键。
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Food food)) {
            return false;
        }
        return id != null && id.equals(food.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }

    @Override
    public String toString() {
        return "Food{id=" + id + ", name='" + name + "', category='" + category + "'}";
    }
}
