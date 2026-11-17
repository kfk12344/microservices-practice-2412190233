package com.zjgsu.hdt.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求体。
 *
 * @param username 登录名
 * @param password 口令明文 —— 只在本次请求内使用，服务端用 BCrypt 与库里的哈希比对
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        String username,

        @NotBlank(message = "口令不能为空")
        String password
) {
}
