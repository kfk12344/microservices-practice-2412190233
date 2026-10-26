package com.zjgsu.hdt.user.dto;

import java.time.LocalDateTime;

import com.zjgsu.hdt.user.model.AppUser;

/**
 * 用户响应体。对外只暴露必要字段，不直接返回实体。
 *
 * @param id              用户 ID
 * @param username        登录名
 * @param displayName     显示名
 * @param dailyTargetKcal 每日目标热量
 * @param createdAt       建档时间
 */
public record UserResponse(
        Long id,
        String username,
        String displayName,
        Integer dailyTargetKcal,
        LocalDateTime createdAt
) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getDailyTargetKcal(),
                user.getCreatedAt()
        );
    }
}
