package com.zjgsu.hdt.meal.exception;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一的错误响应体。
 *
 * <p>拆分之后，调用方需要明确区分"我传错了"（400）、"资源没有"（404）、
 * "对方服务挂了"（503）。因此必须有结构化的 {@code code}，不能只靠中文 message。
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
