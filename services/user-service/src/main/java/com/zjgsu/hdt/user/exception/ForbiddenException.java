package com.zjgsu.hdt.user.exception;

/**
 * 已认证但无权访问（403），或绕过网关直连受保护接口。
 *
 * <p>与 401 的区别：401 是"没证明你是谁"，403 是"知道你是谁，但不允许"。
 * 管理接口对普通用户返回 403，对绕过网关的请求也返回 403 —— 前者是越权，后者是信任边界被破坏。
 */
public class ForbiddenException extends RuntimeException {

    private final String code;

    public ForbiddenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
