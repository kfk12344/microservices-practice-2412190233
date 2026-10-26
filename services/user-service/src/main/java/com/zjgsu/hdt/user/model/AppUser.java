package com.zjgsu.hdt.user.model;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 用户档案 —— user-service 拥有的唯一业务表 {@code app_user}。
 *
 * <p>拆分前用户只是 {@code meal_record.eater} 一个字符串；
 * 拆分后单独成表，并由本服务独占，其他服务只能通过 HTTP 查询。
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录名，全库唯一。 */
    @Column(name = "username", nullable = false, length = 64)
    private String username;

    /** 显示名。 */
    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    /** 每日目标热量（kcal）。 */
    @Column(name = "daily_target_kcal", nullable = false)
    private Integer dailyTargetKcal;

    /** 建档时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** JPA 要求的无参构造器。 */
    protected AppUser() {
    }

    public AppUser(String username, String displayName, Integer dailyTargetKcal, LocalDateTime createdAt) {
        this.username = username;
        this.displayName = displayName;
        this.dailyTargetKcal = dailyTargetKcal;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getDailyTargetKcal() {
        return dailyTargetKcal;
    }

    public void setDailyTargetKcal(Integer dailyTargetKcal) {
        this.dailyTargetKcal = dailyTargetKcal;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AppUser that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(username);
    }

    @Override
    public String toString() {
        return "AppUser{id=" + id + ", username='" + username + "', displayName='" + displayName + "'}";
    }
}
