package com.zjgsu.hdt.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.zjgsu.hdt.user.model.UserRole;

/**
 * 创建用户的请求体。
 *
 * <p>第 09 周起必须携带初始口令：库里存的是 BCrypt 哈希，创建时就落库，
 * 后续登录才有的可比对。口令明文只在这一次请求里出现，服务端算完哈希即丢弃。
 *
 * @param username        登录名，唯一
 * @param displayName     显示名
 * @param dailyTargetKcal 每日目标热量（kcal）
 * @param password        初始口令（明文传输只发生在受信任的 HTTPS 通道内；服务端立即哈希）
 * @param role            角色，缺省为 USER；仅管理员创建营养师/管理员时才显式指定
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
        Integer dailyTargetKcal,

        @NotBlank(message = "初始口令不能为空")
        @Size(min = 8, max = 72, message = "口令长度需在 8~72 之间（BCrypt 只取前 72 字节）")
        String password,

        UserRole role
) {
}
