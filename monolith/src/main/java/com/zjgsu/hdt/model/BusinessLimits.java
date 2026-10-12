package com.zjgsu.hdt.model;

/**
 * 业务规则阈值常量。
 *
 * <p>集中放在一处而不是散落在 Service 里，是为了让规则可以被文档、测试和接口提示共用同一份数字，
 * 避免"文档写 1500、代码限 1200"这类不一致。
 */
public final class BusinessLimits {

    /**
     * 单次用餐的热量上限（kcal）。
     *
     * <p>这是业务规则 R3 的阈值：一次用餐超过该热量会被 Service 拒绝并返回 422。
     * 取 1500 的依据：普通成年人全天推荐摄入约 1800–2400 kcal，
     * 单餐超过 1500 kcal 已接近全天总量，通常意味着份量填写有误，值得拦截。
     */
    public static final int MAX_CALORIES_PER_MEAL = 1500;

    /**
     * 全天餐次数量上限。
     *
     * <p>业务规则 R5：同一天记录超过 12 次用餐视为误操作（例如连点提交）。
     */
    public static final int MAX_MEALS_PER_DAY = 12;

    /**
     * 单次用餐可包含的食物种类上限。
     *
     * <p>业务规则 R4：超过 10 种食物的一次用餐，绝大多数情况是数据录错而非真实饮食。
     */
    public static final int MAX_FOOD_VARIETIES_PER_MEAL = 10;

    /** 单个食物份量的上限（克）。规则 R1 用它拦截明显的错误输入（例如把 150 打成 150000）。 */
    public static final int MAX_PORTION_GRAMS = 5000;

    private BusinessLimits() {
    }
}
