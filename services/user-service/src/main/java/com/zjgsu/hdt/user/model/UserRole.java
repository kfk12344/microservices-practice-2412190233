package com.zjgsu.hdt.user.model;

/**
 * 用户角色 —— 第 09 周引入，JWT 的 {@code role} 声明就是它的名字。
 *
 * <p>角色与项目立项文档里的三类用户对应：
 * <ul>
 *   <li>{@link #USER} —— 普通用户：记录饮食、查看自己的汇总</li>
 *   <li>{@link #NUTRITIONIST} —— 营养师：查看用户饮食数据，为后续功能预留</li>
 *   <li>{@link #ADMIN} —— 管理员：维护食物库、查看用户列表</li>
 * </ul>
 *
 * <p>枚举名直接写进数据库和 JWT，因此<strong>改名等于改协议</strong>：
 * 一旦发布，新增角色可以，重命名会破坏已签发的 Token 与存量数据。
 */
public enum UserRole {

    USER,
    NUTRITIONIST,
    ADMIN;

    /** 解析角色名（忽略大小写），供创建用户时校验输入。 */
    public static UserRole parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return USER;
        }
        try {
            return UserRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("未知角色：" + raw + "，可选值 USER / NUTRITIONIST / ADMIN");
        }
    }
}
