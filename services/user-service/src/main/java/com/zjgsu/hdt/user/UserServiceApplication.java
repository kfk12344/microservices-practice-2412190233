package com.zjgsu.hdt.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务启动类。
 *
 * <p>微服务拆分后，本服务只负责用户档案与每日热量目标，
 * 拥有独立数据库 {@code health_diet_user} 与独立账号 {@code svc_user}，
 * 不访问其他服务的表。
 */
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
