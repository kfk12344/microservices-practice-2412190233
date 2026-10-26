package com.zjgsu.hdt.meal.exception;

/**
 * 违反业务规则 —— 对应 HTTP 422。
 *
 * <p>与 400 的区别：请求的<strong>格式</strong>完全合法，是业务语义上不允许。
 * 例如单餐热量超过 1500 kcal。调用方不该重试，而应修改数据。
 */
public class BusinessRuleException extends RuntimeException {

    /** 对外错误码。 */
    public static final String CODE = "BUSINESS_RULE_VIOLATION";

    private final String ruleId;

    public BusinessRuleException(String ruleId, String message) {
        super(message);
        this.ruleId = ruleId;
    }

    public String getRuleId() {
        return ruleId;
    }
}
