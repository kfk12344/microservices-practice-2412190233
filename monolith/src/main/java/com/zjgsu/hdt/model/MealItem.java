package com.zjgsu.hdt.model;

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
import jakarta.persistence.UniqueConstraint;

/**
 * 饮食记录明细 —— "某次用餐里的某种食物及其份量"，对应数据库表 {@code meal_item}。
 *
 * <h2>为什么把它建成实体，而不是用 {@code Map<Food, Integer>}</h2>
 * 中间表 {@code meal_item} 除两个外键外还带一列 {@code portion_grams}，
 * 这已经是一个<b>带业务属性的关联</b>，在领域模型里理应有自己的对象。
 * 直接写成 {@code Map<Food, Integer>} 时，Hibernate 7（Spring Boot 4 自带）
 * 会把它当作"值为实体的关联"并报
 * {@code Association 'MealRecord.items' targets the type 'java.lang.Integer' which is not an '@Entity' type}，
 * 因为 Map 的<b>值</b>位置不允许放基本类型再配 {@code @Column}。
 *
 * <h2>主键为什么用代理键，而不是 (记录ID, 食物ID) 复合主键</h2>
 * 业务约束是"同一次用餐里同一种食物只能有一行"。用复合主键虽能把该约束交给数据库，
 * 但在 JPA 里需要通过 {@code @EmbeddedId} + {@code @MapsId} 表达，
 * 而 {@code @MapsId} 在"被引用实体尚未持久化"的时序下会尝试给主键对象赋值而抛
 * {@code PropertyAccessException}（本机在 Hibernate 7.2.24 上实测）。
 * 因此这里改用自增代理主键 + {@link UniqueConstraint} 唯一约束：
 * <b>业务约束依然由数据库保证</b>，同时映射简单、时序上没有坑。
 */
@Entity
@Table(
        name = "meal_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_meal_item_record_food",
                columnNames = {"meal_record_id", "food_id"}
        )
)
public class MealItem {

    /** 代理主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属饮食记录。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_record_id", nullable = false)
    private MealRecord mealRecord;

    /** 食物。接口返回必然要带上食物名称与营养值，因此用 EAGER。 */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    /** 份量（克），必须为正数。 */
    @Column(name = "portion_grams", nullable = false)
    private Integer portionGrams;

    /** JPA 要求的无参构造器。 */
    protected MealItem() {
    }

    /**
     * 建立一条明细。调用方应通过 {@link MealRecord#addItem(Food, int)} 挂到记录上，
     * 以保持双向关系一致。
     *
     * @param mealRecord   所属记录
     * @param food         食物
     * @param portionGrams 份量（克），必须为正数
     */
    public MealItem(MealRecord mealRecord, Food food, Integer portionGrams) {
        if (food == null) {
            throw new IllegalArgumentException("食物不能为空");
        }
        if (portionGrams == null || portionGrams <= 0) {
            throw new IllegalArgumentException("份量必须为正整数（克），当前为 " + portionGrams);
        }
        this.mealRecord = mealRecord;
        this.food = food;
        this.portionGrams = portionGrams;
    }

    /** 本明细的热量（kcal）＝ 每 100 克热量 × 份量 ÷ 100。 */
    public BigDecimal calories() {
        return com.zjgsu.hdt.service.NutritionCalculator.scale(food.getCaloriesPer100g(), portionGrams);
    }

    /** 蛋白质（g）。 */
    public BigDecimal protein() {
        return com.zjgsu.hdt.service.NutritionCalculator.scale(food.getProteinPer100g(), portionGrams);
    }

    /** 脂肪（g）。 */
    public BigDecimal fat() {
        return com.zjgsu.hdt.service.NutritionCalculator.scale(food.getFatPer100g(), portionGrams);
    }

    /** 碳水化合物（g）。 */
    public BigDecimal carbs() {
        return com.zjgsu.hdt.service.NutritionCalculator.scale(food.getCarbsPer100g(), portionGrams);
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

    public Food getFood() {
        return food;
    }

    public Integer getPortionGrams() {
        return portionGrams;
    }

    /**
     * 份量累加，用于 {@code MealRecord.addItem} 重复加入同一食物时的合并。
     *
     * @param extraGrams 追加份量（克）
     */
    void addPortion(int extraGrams) {
        if (extraGrams <= 0) {
            throw new IllegalArgumentException("追加份量必须为正整数（克）");
        }
        this.portionGrams = this.portionGrams + extraGrams;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MealItem that)) {
            return false;
        }
        if (id != null && that.id != null) {
            return id.equals(that.id);
        }
        return Objects.equals(mealRecord, that.mealRecord) && Objects.equals(food, that.food);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(mealRecord, food);
    }

    @Override
    public String toString() {
        return "MealItem{id=" + id + ", food=" + (food == null ? null : food.getName())
                + ", portionGrams=" + portionGrams + "}";
    }
}
