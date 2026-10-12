package com.zjgsu.hdt.exception;

/**
 * 请求的资源不存在，统一映射为 HTTP 404 Not Found。
 *
 * <p>与 {@link BusinessRuleException}（422）分开的原因：
 * "找不到这条记录"和"这条记录不满足业务规则"对客户端是两种完全不同的处理，
 * 前者通常意味着链接/ID 过期，后者意味着要改数据。
 */
public class ResourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message 面向使用者的说明，需包含被查找的标识
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
