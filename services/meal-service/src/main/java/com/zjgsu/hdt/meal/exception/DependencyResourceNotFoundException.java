package com.zjgsu.hdt.meal.exception;

/**
 * 依赖服务提供的资源不存在 —— 例如提交的 {@code userId} 在 user-service 里查不到。
 *
 * <p>与 {@link DependencyUnavailableException} 的区别很关键：
 * <ul>
 *   <li>本异常 = 对方<strong>正常响应了</strong>，但明确说"没有这个资源"（404）→ 调用方该改请求；</li>
 *   <li>不可用 = 对方<strong>根本没响应</strong>（连不上/超时）→ 调用方稍后重试。</li>
 * </ul>
 * 两者都不能继续写入本地数据，但错误码与状态码不同。
 */
public class DependencyResourceNotFoundException extends RuntimeException {

    /** 用户不存在。 */
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";

    /** 食物不存在。 */
    public static final String FOOD_NOT_FOUND = "FOOD_NOT_FOUND";

    private final String code;

    public DependencyResourceNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static DependencyResourceNotFoundException user(Long userId) {
        return new DependencyResourceNotFoundException(
                USER_NOT_FOUND, "用户不存在：userId=" + userId + "（来自 user-service）");
    }

    public static DependencyResourceNotFoundException food(Long foodId) {
        return new DependencyResourceNotFoundException(
                FOOD_NOT_FOUND, "食物不存在：foodId=" + foodId + "（来自 food-service）");
    }
}
