package com.zjgsu.hdt.meal.exception;

/**
 * 本服务自己的资源不存在（例如按 ID 查饮食记录查不到）—— 对应 HTTP 404。
 */
public class ResourceNotFoundException extends RuntimeException {

    public static final String CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException mealRecord(Long id) {
        return new ResourceNotFoundException("饮食记录不存在：id=" + id);
    }
}
