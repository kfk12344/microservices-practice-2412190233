package com.zjgsu.hdt.user.exception;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一的错误响应体。
 *
 * <p>比 Spring 默认的错误页多一个 {@code code} 字段：
 * 调用方（含 meal-service 这种服务间调用）应按 {@code code} 判断错误类型，
 * 而不是去匹配可能被改写的 {@code message} 文案。
 *
 * @param timestamp 出错时间
 * @param status    HTTP 状态码
 * @param error     状态码的简短描述
 * @param code      业务错误码
 * @param message   给调用方看的错误说明
 * @param path      请求路径
 * @param details   字段级错误明细（校验失败时有值）
 */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<String> details
) {

    public static ApiError of(int status, String error, String code, String message, String path) {
        return new ApiError(LocalDateTime.now(), status, error, code, message, path, List.of());
    }

    public static ApiError of(int status, String error, String code, String message, String path,
                              List<String> details) {
        return new ApiError(LocalDateTime.now(), status, error, code, message, path, details);
    }
}
