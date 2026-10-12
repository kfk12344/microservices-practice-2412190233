package com.zjgsu.hdt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.zjgsu.hdt.controller.HelloController;
import com.zjgsu.hdt.controller.MealController;
import com.zjgsu.hdt.service.MealRecordService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * 应用启动测试（Week 03 要求的 {@code contextLoads}，Week 04 继续保留）。
 *
 * <p>为什么启动测试不能省：它是唯一能一次性证明"配置、依赖、数据源、实体映射、
 * SQL 初始化脚本"全部可用的测试。少了它，接口测试可能因为某个 Bean 没装配而集体失败，
 * 排查成本远高于保留这一条。
 *
 * <p>{@code webEnvironment = RANDOM_PORT} 会真正启动内嵌容器，比默认的 MOCK 环境更接近真实运行；
 * 端口随机分配所以不会与"开发者本机已启动的 8080"冲突。
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class HealthDietTrackerApplicationTests {

    @Autowired
    private HelloController helloController;

    @Autowired
    private MealController mealController;

    @Autowired
    private MealRecordService mealRecordService;

    @Test
    @DisplayName("应用上下文可以加载，Web 层与业务层 Bean 均已装配")
    void contextLoads() {
        assertThat(helloController).isNotNull();
        assertThat(mealController).isNotNull();
        assertThat(mealRecordService).isNotNull();
    }
}
