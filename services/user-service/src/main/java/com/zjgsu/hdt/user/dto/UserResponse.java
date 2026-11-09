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
 * @param servedBy        <strong>实际处理这次请求的实例标识</strong>（第 08 周新增）。
 *                        它不是业务字段，只用于让调用方验证负载均衡是否真的分发了请求；
 *                        多实例部署时每次请求的值可能不同。
 */
public record UserResponse(
        Long id,
        String username,
        String displayName,
        Integer dailyTargetKcal,
        LocalDateTime createdAt,
        String servedBy
) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getDailyTargetKcal(),
                user.getCreatedAt(),
                null
        );
    }

    /**
     * 补上实例标识，返回新对象（record 不可变）。
     *
     * @param instanceId 本实例标识，一般来自 {@code InstanceIdentity}
     * @return 带实例标识的响应体
     */
    public UserResponse servedBy(String instanceId) {
        return new UserResponse(id, username, displayName, dailyTargetKcal, createdAt, instanceId);
    }
}
