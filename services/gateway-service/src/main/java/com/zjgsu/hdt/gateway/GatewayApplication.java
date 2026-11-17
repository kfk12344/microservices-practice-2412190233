package com.zjgsu.hdt.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关（第 09 周新增）。
 *
 * <p>它是整个系统<strong>唯一对外</strong>的端口（8080）：
 * 客户端只认识 {@code /api/users}、{@code /api/foods}、{@code /api/meals}，
 * 不关心背后是几个实例、部署在哪台机器。
 *
 * <p>网关本身不写业务：只做三件事 —— 路由、验签、按角色放行。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
