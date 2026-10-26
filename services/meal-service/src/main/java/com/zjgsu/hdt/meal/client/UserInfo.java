package com.zjgsu.hdt.meal.client;

/**
 * user-service 返回的用户信息（本服务只关心这几个字段）。
 *
 * <p>跨服务 DTO 与本地实体刻意分开：对方加了字段不会影响本服务，
 * 本服务也不会因为对方字段改名而静默拿到 null。
 */
public record UserInfo(
        Long id,
        String username,
        String displayName,
        Integer dailyTargetKcal
) {
}
