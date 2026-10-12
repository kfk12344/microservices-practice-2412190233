package com.zjgsu.hdt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 个人健康饮食记录管理系统 —— 单体应用启动类。
 *
 * <p>包名遵循课程要求 {@code com.zjgsu.<姓名缩写>}，本仓库姓名为郑俊豪，缩写取拼音首字母 {@code zjh}。
 *
 * <p>本周（Week 03）只要求：应用能启动、有基础配置、有一个用于验证运行状态的 GET 接口、
 * 以及一个可重复通过的启动测试。业务建模与数据库持久化在 Week 04 完成。
 */
@SpringBootApplication
public class HealthDietTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthDietTrackerApplication.class, args);
    }
}
