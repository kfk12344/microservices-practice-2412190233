package com.zjgsu.hdt.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建用户的请求体。
 *
 * @param username        登录名，唯一
 * @param displayName     显示名
 * @param dailyTargetKcal 每日目标热量（kcal）
 */
public record UserRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过 64")
        String username,

        @NotBlank(message = "显示名不能为空")
        @Size(max = 64, message = "显示名长度不能超过 64")
        String displayName,

        @Min(value = 500, message = "每日目标热量不能低于 500 kcal")
        @Max(value = 6000, message = "每日目标热量不能高于 6000 kcal")
        Integer dailyTargetKcal
) {
}
