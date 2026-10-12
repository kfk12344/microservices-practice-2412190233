package com.zjgsu.hdt.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 运行状态接口的 Web 层切片测试（Week 03 要求）。
 *
 * <p>使用 {@code @WebMvcTest} 只加载 Web 层，不启动数据源——
 * 这样"接口能不能正确渲染 JSON"与"数据库是否可用"两件事互不干扰，失败时定位更快。
 */
@WebMvcTest(HelloController.class)
@ActiveProfiles("test")
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/hello 返回 200，含项目名、问候语与时间戳")
    void helloReturnsApplicationName() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("health-diet-tracker"))
                .andExpect(jsonPath("$.message").value("个人健康饮食记录管理系统已启动"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
