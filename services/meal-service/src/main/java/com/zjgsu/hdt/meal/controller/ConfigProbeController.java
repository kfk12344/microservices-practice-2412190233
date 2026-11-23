package com.zjgsu.hdt.meal.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zjgsu.hdt.meal.config.MealRules;

/**
 * 第 10 周：配置中心的只读观察端点。
 *
 * <p>{@code GET /api/config-probe} 是普通 MVC 路径，网关的
 * JwtAuthenticationFilter 已把它加入白名单，因此直连与经过网关都能访问。
 * 刻意不放 {@code /actuator/**}：Actuator 会抢占该前缀并当作静态资源处理。
 * 端点只返回非敏感字段，绝不回显 JWT_SECRET / DB_PASSWORD 等密钥。
 *
 * <p>验证时对比：
 * <pre>
 *   configSource   local → nacos   证明配置确实来自配置中心
 *   maxKcalPerMeal 1500  → 1200    证明 @RefreshScope 动态刷新生效
 * </pre>
 */
@RestController
@RequestMapping("/api/config-probe")
public class ConfigProbeController {

    private final MealRules mealRules;
    private final String environment;
    private final String configSource;
    private final String group;

    public ConfigProbeController(MealRules mealRules,
                                 @Value("${app.environment:dev}") String environment,
                                 @Value("${app.config.source:local}") String configSource,
                                 @Value("${spring.cloud.nacos.config.group:PERSONAL_GROUP}") String group) {
        this.mealRules = mealRules;
        this.environment = environment;
        this.configSource = configSource;
        this.group = group;
    }

    /** 返回当前生效的配置快照（非敏感）。 */
    @GetMapping
    public Map<String, Object> probe() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "meal-service");
        body.put("environment", environment);
        body.put("configSource", configSource);
        body.put("dataId", "meal-service-" + environment + ".yaml");
        body.put("group", group);
        body.put("maxKcalPerMeal", mealRules.maxKcalPerMeal());
        body.put("mealCreateEnabled", mealRules.createEnabled());
        return body;
    }
}
