package com.zjgsu.hdt.meal.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * 一条饮食记录（一次用餐）。
 *
 * <p><strong>{@code userId} 只是普通列，没有外键</strong> —— 用户数据在另一个库
 * （user-service 的 {@code health_diet_user.app_user}），跨库外键在物理上不可能，
 * 逻辑上的存在性由应用层调用 user-service 校验。
 */
@Entity
@Table(name = "meal_record")
public class MealRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户的 ID —— 指向另一个服务的实体，只存 ID，不存快照。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 16)
    private MealType mealType;

    @Column(name = "eaten_at", nullable = false)
    private LocalDateTime eatenAt;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 明细。级联保存，删除记录时明细一起删。 */
    @OneToMany(mappedBy = "mealRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MealItem> items = new ArrayList<>();

    /** JPA 要求的无参构造器。 */
    protected MealRecord() {
    }

    public MealRecord(Long userId, MealType mealType, LocalDateTime eatenAt, String note,
                      LocalDateTime createdAt) {
        this.userId = userId;
        this.mealType = mealType;
        this.eatenAt = eatenAt;
        this.note = note;
        this.createdAt = createdAt;
    }

    /** 加一条明细并维护双向关联。 */
    public void addItem(MealItem item) {
        item.setMealRecord(this);
        this.items.add(item);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public MealType getMealType() {
        return mealType;
    }

    public LocalDateTime getEatenAt() {
        return eatenAt;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<MealItem> getItems() {
        return items;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MealRecord that)) {
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
        return "MealRecord{id=" + id + ", userId=" + userId + ", mealType=" + mealType
                + ", eatenAt=" + eatenAt + ", items=" + items.size() + "}";
    }
}
