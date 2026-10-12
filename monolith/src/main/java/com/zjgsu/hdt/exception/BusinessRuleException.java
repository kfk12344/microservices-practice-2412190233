package com.zjgsu.hdt.exception;

/**
 * 业务规则校验失败异常，统一映射为 HTTP 422 Unprocessable Entity。
 *
 * <p>为什么单独定义而不直接抛 {@link IllegalArgumentException}：
 * <ul>
 *   <li>便于全局异常处理精确区分"业务规则不满足"与"程序缺陷"，
 *       前者是使用者输入问题（422），后者应该暴露出来（500）；</li>
 *   <li>异常消息会原样返回给客户端，因此消息文案要写成人能看懂的说明，
 *       而不是栈信息或内部字段名。</li>
 * </ul>
 */
public class BusinessRuleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 被违反的规则编号（例如 R3），便于客户端与文档对应。 */
    private final String ruleCode;

    /**
     * @param ruleCode 规则编号，例如 {@code R3}
     * @param message  面向使用者的说明
     */
    public BusinessRuleException(String ruleCode, String message) {
        super(message);
        this.ruleCode = ruleCode;
    }

    public String getRuleCode() {
        return ruleCode;
    }
}
