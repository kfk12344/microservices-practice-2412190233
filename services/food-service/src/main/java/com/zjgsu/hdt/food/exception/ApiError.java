package com.zjgsu.hdt.food.exception;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一的错误响应体。调用方应按 {@code code} 判断错误类型，而不是匹配 {@code message} 文案。
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
