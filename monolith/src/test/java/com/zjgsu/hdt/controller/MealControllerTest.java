package com.zjgsu.hdt.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.zjgsu.hdt.dto.MealRecordResponse;
import com.zjgsu.hdt.exception.BusinessRuleException;
import com.zjgsu.hdt.exception.ResourceNotFoundException;
import com.zjgsu.hdt.exception.GlobalExceptionHandler;
import com.zjgsu.hdt.model.MealType;
import com.zjgsu.hdt.service.DailySummaryService;
import com.zjgsu.hdt.service.FoodService;
import com.zjgsu.hdt.service.MealRecordService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口层测试 —— 对应作业要求"至少验证一次 API 正常或错误响应"。
 *
 * <p>只加载 Web 层（{@code @WebMvcTest}），Service 用 {@code @MockitoBean} 替身，
 * 目的是把"路由、状态码、JSON 结构、异常映射"这四件事单独验证干净，
 * 不受数据库状态影响。Service 的规则逻辑由 {@code MealRecordServiceTest} 覆盖，
 * 端到端链路由 {@code MealApiIntegrationTest} 覆盖。
 *
 * <p>这里同时覆盖了正常响应（200 / 201）与错误响应（400 / 404 / 422），
 * 状态码约定与 {@link GlobalExceptionHandler} 一致。
 */
@WebMvcTest({MealController.class, FoodController.class})
@ActiveProfiles("test")
class MealControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MealRecordService mealRecordService;

    @MockitoBean
    private DailySummaryService dailySummaryService;

    @MockitoBean
    private FoodService foodService;

    private static MealRecordResponse sampleResponse() {
        return new MealRecordResponse(
                1L, "demo-user", MealType.BREAKFAST, "早餐",
                LocalDateTime.of(2026, 9, 28, 8, 0), "标准早餐",
                List.of(new MealRecordResponse.Item(2L, "全麦面包", "主食", 80,
                        new BigDecimal("196.80"), new BigDecimal("7.20"),
                        new BigDecimal("2.72"), new BigDecimal("35.20"))),
                new BigDecimal("196.80"), new BigDecimal("7.20"),
                new BigDecimal("2.72"), new BigDecimal("35.20"), 80);
    }

    // ------------------------------------------------------------------
    // 正常响应
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/meals 返回 200 与记录列表")
    void listReturnsOk() throws Exception {
        when(mealRecordService.findByEater("demo-user")).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/meals").param("eater", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].mealTypeLabel").value("早餐"))
                .andExpect(jsonPath("$[0].totalCalories").value(196.80))
                .andExpect(jsonPath("$[0].items[0].name").value("全麦面包"));
    }

    @Test
    @DisplayName("GET /api/meals/{id} 返回 200 与单条记录")
    void getByIdReturnsOk() throws Exception {
        when(mealRecordService.findById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/meals/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.eater").value("demo-user"));
    }

    @Test
    @DisplayName("POST /api/meals 合法请求返回 201 与新建记录")
    void createReturnsCreated() throws Exception {
        when(mealRecordService.create(any())).thenReturn(sampleResponse());

        String body = """
                {
                  "eater": "demo-user",
                  "mealType": "BREAKFAST",
                  "eatenAt": "2026-09-28T08:00:00",
                  "note": "标准早餐",
                  "items": [{ "foodId": 2, "portionGrams": 80 }]
                }
                """;

        mockMvc.perform(post("/api/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.totalCalories").value(196.80));
    }

    @Test
    @DisplayName("GET /api/meals/rules 返回业务规则与餐次时间窗")
    void rulesReturnsRulebook() throws Exception {
        mockMvc.perform(get("/api/meals/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules.R3").exists())
                .andExpect(jsonPath("$.mealTypeWindows.早餐").value("05:00-10:59"));
    }

    @Test
    @DisplayName("GET /api/foods 返回 200 与食物库")
    void foodsReturnsOk() throws Exception {
        when(foodService.search(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/foods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ------------------------------------------------------------------
    // 错误响应：400 / 404 / 422
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/meals 字段校验失败返回 400，并逐条列出问题字段")
    void createWithInvalidBodyReturnsBadRequest() throws Exception {
        // eater 为空、items 为空列表，两处字段级校验都应命中
        String body = """
                {
                  "eater": "",
                  "mealType": "BREAKFAST",
                  "eatenAt": "2026-09-28T08:00:00",
                  "items": []
                }
                """;

        mockMvc.perform(post("/api/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("POST /api/meals 枚举值非法返回 400（mealType 传了不存在的餐次）")
    void createWithUnknownMealTypeReturnsBadRequest() throws Exception {
        String body = """
                {
                  "eater": "demo-user",
                  "mealType": "MIDNIGHT_SNACK",
                  "eatenAt": "2026-09-28T08:00:00",
                  "items": [{ "foodId": 2, "portionGrams": 80 }]
                }
                """;

        mockMvc.perform(post("/api/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
    }

    @Test
    @DisplayName("GET /api/meals/{id} 记录不存在返回 404")
    void getByIdReturnsNotFound() throws Exception {
        when(mealRecordService.findById(999L))
                .thenThrow(new ResourceNotFoundException("找不到 ID 为 999 的饮食记录"));

        mockMvc.perform(get("/api/meals/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("找不到 ID 为 999 的饮食记录"));
    }

    @Test
    @DisplayName("POST /api/meals 违反业务规则返回 422，并标明规则编号")
    void createViolatingBusinessRuleReturnsUnprocessableEntity() throws Exception {
        when(mealRecordService.create(any())).thenThrow(
                new BusinessRuleException("R3", "本次用餐热量 2000.00 kcal 超过单餐上限 1500 kcal"));

        String body = """
                {
                  "eater": "demo-user",
                  "mealType": "LUNCH",
                  "eatenAt": "2026-09-28T12:30:00",
                  "items": [{ "foodId": 1, "portionGrams": 2000 }]
                }
                """;

        mockMvc.perform(post("/api/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "本次用餐热量 2000.00 kcal 超过单餐上限 1500 kcal"))
                .andExpect(jsonPath("$.details[0]").value(
                        org.hamcrest.Matchers.containsString("违反规则：R3")));
    }

    @Test
    @DisplayName("GET /api/meals/{id} 路径变量不是数字时返回 400（而不是 500）")
    void getByIdWithNonNumericIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/meals/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_TYPE"))
                .andExpect(jsonPath("$.details[0]").value(
                        org.hamcrest.Matchers.containsString("abc")));
    }

    @Test
    @DisplayName("GET /api/meals/daily-summary 无记录时合计为 0，而不是报错")
    void dailySummaryReturnsZeroWhenNoRecord() throws Exception {
        when(dailySummaryService.summarize(eq("nobody"), any()))
                .thenReturn(new com.zjgsu.hdt.dto.DailySummaryResponse(
                        java.time.LocalDate.of(2026, 9, 28), "nobody", 0,
                        new BigDecimal("0.00"), new BigDecimal("0.00"),
                        new BigDecimal("0.00"), new BigDecimal("0.00"), 0,
                        List.of(), 1500, List.of()));

        mockMvc.perform(get("/api/meals/daily-summary")
                        .param("eater", "nobody")
                        .param("date", "2026-09-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealCount").value(0))
                .andExpect(jsonPath("$.totalCalories").value(0.00));
    }
}
