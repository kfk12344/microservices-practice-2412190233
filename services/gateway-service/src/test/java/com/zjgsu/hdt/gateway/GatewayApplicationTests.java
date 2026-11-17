package com.zjgsu.hdt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.test.context.ActiveProfiles;

import com.zjgsu.hdt.gateway.controller.HealthController;

/**
 * 上下文装配测试：确认路由配置、JWT 解码器、健康入口都能正常创建。
 */
@SpringBootTest
@ActiveProfiles("test")
class GatewayApplicationTests {

    @Autowired
    private HealthController healthController;

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    @DisplayName("上下文能加载，健康入口返回 UP")
    void contextLoads() {
        assertThat(healthController.health())
                .containsEntry("status", "UP")
                .containsEntry("service", "gateway-service");
    }

    @Test
    @DisplayName("路由表：5 条路由都指向服务名，且管理路由存在")
    void routesAreLoaded() {
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions().collectList().block();

        assertThat(routes).hasSize(5);
        assertThat(routes).extracting(RouteDefinition::getId)
                .containsExactlyInAnyOrder("auth-route", "user-route", "admin-route", "food-route", "meal-route");
        assertThat(routes).allSatisfy(route ->
                assertThat(route.getUri().toString()).as("目标必须是服务名").startsWith("lb://"));
    }
}
