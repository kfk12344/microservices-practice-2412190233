package com.zjgsu.hdt.meal.exception;

/**
 * 依赖服务不可用 —— 连不上、超时、返回 5xx，或者<strong>注册中心里查不到可用实例</strong>。
 *
 * <p>对应 HTTP 503。出现这个错误时，本服务<strong>不会写入任何本地数据</strong>：
 * 宁可直接失败并让调用方重试，也不接受"跳过校验先写进去"这种做法 ——
 * 那样会留下没有校验过的脏数据，比报错更糟。
 *
 * <p>第 07 周之后新增了"查不到实例"这一种来源：服务已下线但 Nacos 还没来得及摘除，
 * 或者该服务从未注册过。它同样归到 503，因为语义上都是"下游暂时不可用"，
 * 而不是"你的请求参数错了"。
 */
public class DependencyUnavailableException extends RuntimeException {

    /** 对外错误码。 */
    public static final String CODE = "DEPENDENCY_UNAVAILABLE";

    private final String service;

    /** 下游抛出的具体异常（连接失败、超时等）。 */
    public DependencyUnavailableException(String service, Throwable cause) {
        super("依赖服务不可用：" + service + "（" + cause.getClass().getSimpleName() + ": "
                + cause.getMessage() + "）", cause);
        this.service = service;
    }

    /** 没有抛异常、只是查不到实例的情况，例如 Nacos 返回的实例列表为空。 */
    public DependencyUnavailableException(String service, String reason) {
        super("依赖服务不可用：" + service + "（" + reason + "）");
        this.service = service;
    }

    public String getService() {
        return service;
    }
}
