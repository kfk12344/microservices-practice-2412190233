package com.zjgsu.hdt.meal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 第 10 周：可从 Nacos Config 动态刷新的业务规则。
 *
 * <p>关键点：
 * <ul>
 *   <li>{@code @RefreshScope} 让这个 Bean 在 Nacos 配置变更时被重建，
 *       {@code @Value} 字段随之拿到新值，<strong>无需重新打包或重启容器</strong>；</li>
 *   <li>这里只放非敏感业务参数；口令、密钥一律从 .env 注入，不进入配置中心；</li>
 *   <li>业务代码只依赖这个 Bean，不直接读 Environment，
 *       这样"配置是否生效"可以通过 {@code /actuator/config-probe} 直接观察。</li>
 * </ul>
 */
@Component
@RefreshScope
public class MealRules {

    /** 单餐热量上限（kcal），对应业务规则 R3。 */
    @Value("${app.meal.max-kcal-per-meal:1500}")
    private int maxKcalPerMeal;

    /** 是否允许创建饮食记录，用于演示业务开关的动态生效。 */
    @Value("${app.meal.create-enabled:true}")
    private boolean createEnabled;

    public int maxKcalPerMeal() {
        return maxKcalPerMeal;
    }

    public boolean createEnabled() {
        return createEnabled;
    }
}
