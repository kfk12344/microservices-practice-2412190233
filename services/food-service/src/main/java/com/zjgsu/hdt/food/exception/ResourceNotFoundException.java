package com.zjgsu.hdt.food.exception;

/**
 * 请求的资源不存在 —— 对应 HTTP 404。
 *
 * <p>{@link #CODE} 是对外契约：meal-service 靠它区分"食物不存在"与"食物服务挂了"。
 */
public class ResourceNotFoundException extends RuntimeException {

    public static final String CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
