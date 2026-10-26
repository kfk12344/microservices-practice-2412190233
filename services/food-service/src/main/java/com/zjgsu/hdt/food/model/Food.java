package com.zjgsu.hdt.food.model;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 食物库条目 —— food-service 拥有的唯一业务表 {@code food}。
 *
 * <p>营养值一律按"每 100 克"存储，具体份量的折算由调用方（meal-service）完成。
 * 这样本服务不需要知道"份量"这个概念，职责更单纯。
 */
@Entity
@Table(name = "food")
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 食物名称，全库唯一。 */
    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** 分类：主食 / 蛋白质 / 蔬菜 / 水果 / 饮品 / 零食。 */
    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "calories_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal caloriesPer100g;

    @Column(name = "protein_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal proteinPer100g;

    @Column(name = "fat_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal fatPer100g;

    @Column(name = "carbs_per_100g", nullable = false, precision = 8, scale = 2)
    private BigDecimal carbsPer100g;

    /** JPA 要求的无参构造器。 */
    protected Food() {
    }

    public Food(String name, String category, BigDecimal caloriesPer100g,
                BigDecimal proteinPer100g, BigDecimal fatPer100g, BigDecimal carbsPer100g) {
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Food that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Food{id=" + id + ", name='" + name + "', category='" + category + "'}";
    }
}
