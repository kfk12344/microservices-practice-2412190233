package com.zjgsu.hdt.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * 饮食记录（一次用餐）—— Week 04 的核心实体之一。
 *
 * <p>一条记录表达"<b>谁</b>在<b>什么时间</b>吃了<b>哪一类餐</b>，包含哪些食物、各多少克"。
 * 明细由 {@link MealItem} 承载：明细表除两个外键外还有"份量"这一业务属性，
 * 所以它是一个带属性的关联，被建模成独立实体（理由见 {@link MealItem} 的类注释）。
 *
 * <p>营养合计不落库，而是由明细在读取时即时算出（{@link #totalCalories()} 等）。
 * 这样做的权衡：省掉了冗余列与"明细改了合计没更新"的不一致风险，
 * 代价是每次查询都要做一次乘法累加——在单人饮食记录的数据量下可以忽略。
 */
@Entity
@Table(name = "meal_record")
public class MealRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用餐人标识。Week 04 尚未接入用户表，先用字符串标识，接入认证后可替换为外键。 */
    @Column(name = "eater", nullable = false, length = 64)
    private String eater;

    /** 餐次：早餐 / 午餐 / 晚餐 / 加餐。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 16)
    private MealType mealType;

    /** 实际用餐时间，精确到分钟。 */
    @Column(name = "eaten_at", nullable = false)
    private LocalDateTime eatenAt;

    /** 备注，可空。 */
    @Column(name = "note", length = 255)
    private String note;

    /**
     * 本次用餐的食物明细。
     *
     * <p>使用 {@code orphanRemoval = true}：从集合里移除一条明细即删除对应行，
     * 不需要显式调用 delete。级联 PERSIST/MERGE 让保存记录时明细一并落库。
     * 用 EAGER 加载：接口返回必然要带上全部明细，延迟加载只会造成 N+1。
     */
    @OneToMany(
            mappedBy = "mealRecord",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    private List<MealItem> items = new ArrayList<>();

    /** JPA 要求的无参构造器。 */
    protected MealRecord() {
    }

    public MealRecord(String eater, MealType mealType, LocalDateTime eatenAt, String note) {
        this.eater = eater;
        this.mealType = mealType;
        this.eatenAt = eatenAt;
        this.note = note;
    }

    /**
     * 加入一种食物及其份量，并维护双向关系。
     *
     * <p>同一食物重复加入时<b>累加份量</b>而不是新增一行——
     * 受复合主键 (meal_record_id, food_id) 约束，同一食物在一次用餐里只能有一行。
     *
     * @param food         食物（需已持久化）
     * @param portionGrams 份量（克），必须为正数
     * @throws IllegalArgumentException 食物为空或份量非正数
     */
    public void addItem(Food food, int portionGrams) {
        if (food == null) {
            throw new IllegalArgumentException("食物不能为空");
        }
        if (portionGrams <= 0) {
            throw new IllegalArgumentException("份量必须为正整数（克），当前为 " + portionGrams);
        }
        for (MealItem existing : items) {
            if (Objects.equals(existing.getFood(), food)) {
                existing.addPortion(portionGrams);
                return;
            }
        }
        items.add(new MealItem(this, food, portionGrams));
    }

    public Long getId() {
        return id;
    }

    public String getEater() {
        return eater;
    }

    public void setEater(String eater) {
        this.eater = eater;
    }

    public MealType getMealType() {
        return mealType;
    }

    public void setMealType(MealType mealType) {
        this.mealType = mealType;
    }

    public LocalDateTime getEatenAt() {
        return eatenAt;
    }

    public void setEatenAt(LocalDateTime eatenAt) {
        this.eatenAt = eatenAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** 明细列表（只读视图，避免外部绕过 {@link #addItem} 破坏双向关系）。 */
    public List<MealItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /** 食物种类数（业务规则 R4 的判断依据）。 */
    public int varietyCount() {
        return items.size();
    }

    /**
     * 本次用餐总热量（kcal）。
     *
     * @return 保留 2 位小数；无明细时为 {@code 0.00}
     */
    public BigDecimal totalCalories() {
        return NutritionTotals.sum(items, MealItem::calories);
    }

    /** 蛋白质合计（g）。 */
    public BigDecimal totalProtein() {
        return NutritionTotals.sum(items, MealItem::protein);
    }

    /** 脂肪合计（g）。 */
    public BigDecimal totalFat() {
        return NutritionTotals.sum(items, MealItem::fat);
    }

    /** 碳水化合物合计（g）。 */
    public BigDecimal totalCarbs() {
        return NutritionTotals.sum(items, MealItem::carbs);
    }

    /** 食物总重量（g）。 */
    public int totalGrams() {
        return items.stream().mapToInt(MealItem::getPortionGrams).sum();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MealRecord that)) {
            return false;
        }
        if (id != null && that.id != null) {
            return id.equals(that.id);
        }
        return Objects.equals(eater, that.eater)
                && Objects.equals(mealType, that.mealType)
                && Objects.equals(eatenAt, that.eatenAt);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(eater, mealType, eatenAt);
    }

    @Override
    public String toString() {
        return "MealRecord{id=" + id + ", eater='" + eater + "', mealType=" + mealType
                + ", eatenAt=" + eatenAt + ", items=" + items.size() + "}";
    }
}
