package com.zjgsu.hdt.meal.exception;

/**
 * 依赖服务不可用 —— 连不上、超时、或返回 5xx。
 *
 * <p>对应 HTTP 503。出现这个错误时，本服务<strong>不会写入任何本地数据</strong>：
 * 宁可直接失败并让调用方重试，也不接受"跳过校验先写进去"这种做法 ——
 * 那样会留下没有校验过的脏数据，比报错更糟。
 */
public class DependencyUnavailableException extends RuntimeException {

    /** 对外错误码。 */
    public static final String CODE = "DEPENDENCY_UNAVAILABLE";

    private final String service;

    public DependencyUnavailableException(String service, Throwable cause) {
        super("依赖服务不可用：" + service + "（" + cause.getClass().getSimpleName() + ": "
                + cause.getMessage() + "）", cause);
        this.service = service;
    }

    public String getService() {
        return service;
    }
}
