package com.zjgsu.hdt.user.dto;

/**
 * 登录成功的响应体。
 *
 * <p>客户端后续请求只需带上 {@code Authorization: Bearer <token>}，
 * 不需要再关心用户 ID 与角色 —— 网关验签后会从 Token 里取出它们。
 *
 * @param token       HS256 签名的 JWT（紧凑格式，三段以 . 分隔）
 * @param tokenType   固定为 Bearer
 * @param expiresIn   有效期（秒）
 * @param userId      用户 ID，对应 JWT 的 sub
 * @param username    登录名
 * @param displayName 显示名
 * @param role        角色，对应 JWT 的 role 声明，网关据此做接口级授权
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn,
        Long userId,
        String username,
        String displayName,
        String role
) {
}
