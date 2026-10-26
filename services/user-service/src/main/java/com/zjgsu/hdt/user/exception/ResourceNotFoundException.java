package com.zjgsu.hdt.user.exception;

/**
 * 请求的资源不存在 —— 对应 HTTP 404。
 *
 * <p>跨服务调用时，这个异常也会被 meal-service 用来判断"用户不存在"，
 * 因此错误码 {@link #CODE} 是对外契约的一部分，不能随意改。
 */
public class ResourceNotFoundException extends RuntimeException {

    /** 对外错误码，供调用方用程序判断，而不是去解析中文消息。 */
    public static final String CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException user(Long id) {
        return new ResourceNotFoundException("用户不存在：id=" + id);
    }
}
