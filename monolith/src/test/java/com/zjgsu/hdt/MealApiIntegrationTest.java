package com.zjgsu.hdt;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * 端到端接口测试 —— 真正启动内嵌容器，用 HTTP 请求走完整链路。
 *
 * <p>与 {@code MealControllerTest} 的分工：
 * <ul>
 *   <li>Controller 切片测试用替身 Service，快速穷举状态码与 JSON 结构；</li>
 *   <li>本测试不替换任何组件，验证"HTTP → Controller → Service → Repository → H2"
 *       整条链路确实通畅，包括 SQL 初始化脚本是否真的把数据灌进去了。</li>
 * </ul>
 *
 * <p>数据来自 {@code schema.sql} + {@code data.sql}，端口随机分配，
 * 因此不会与开发者本机已启动的 8080 冲突。
 *
 * <p>{@code @AutoConfigureTestRestTemplate} 是 Spring Boot 4 必需的：
 * {@code TestRestTemplate} 已迁到 {@code spring-boot-resttestclient} 模块，
 * 且不再随 Web 环境自动注册，必须显式开启该自动配置才能注入。
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class MealApiIntegrationTest {

    /** 与 data.sql 中的示例数据保持一致。 */
    private static final String SEED_EATER = "demo-user";
    private static final String SEED_DATE = "2026-09-28";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * 把 Jackson 反序列化出来的对象安全地看作 Map / List。
     *
     * <p>集中在这里的原因：{@code TestRestTemplate} 的泛型在编译期无法约束，
     * 若在各处直接强转，会把一堆 {@code @SuppressWarnings} 散落到测试方法里。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return (List<Object>) value;
    }

    // ------------------------------------------------------------------
    // Week 03 的两个接口
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/hello 返回项目名称（Week 03 运行验证）")
    void helloEndpointResponds() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/api/hello"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("application", "health-diet-tracker");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    @DisplayName("GET /actuator/health 返回 UP（Week 03 健康检查）")
    void actuatorHealthIsUp() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/actuator/health"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    // ------------------------------------------------------------------
    // Week 04 业务接口
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/meals 返回 data.sql 灌入的 4 条示例记录，且营养合计非空")
    void seededMealsAreReturned() {
        ResponseEntity<List> response = restTemplate.getForEntity(
                url("/api/meals?eater=" + SEED_EATER), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(4);

        Map<String, Object> first = asMap(response.getBody().getFirst());
        assertThat(first).containsKeys("id", "mealTypeLabel", "totalCalories", "items");
        assertThat(first.get("items")).isNotNull();
    }

    @Test
    @DisplayName("GET /api/meals/{id} 返回单条记录及其明细")
    void seededMealByIdReturnsItems() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/api/meals/2"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("mealTypeLabel")).isEqualTo("午餐");

        List<?> items = asList(body.get("items"));
        assertThat(items).hasSize(3);
    }

    @Test
    @DisplayName("GET /api/meals/daily-summary 汇总 2026-09-28 的四餐，并按餐次拆分")
    void dailySummaryAggregatesSeededDay() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                url("/api/meals/daily-summary?eater=" + SEED_EATER + "&date=" + SEED_DATE), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("mealCount")).isEqualTo(4);
        assertThat(body.get("date")).isEqualTo(SEED_DATE);

        List<?> breakdown = asList(body.get("byMealType"));
        assertThat(breakdown).hasSize(4);
    }

    @Test
    @DisplayName("GET /api/foods 返回 12 条食物库数据，营养值口径为每 100 克")
    void foodLibraryIsSeeded() {
        ResponseEntity<List> response = restTemplate.getForEntity(url("/api/foods"), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(12);

        Map<String, Object> first = asMap(response.getBody().getFirst());
        assertThat(first).containsKeys("caloriesPer100g", "proteinPer100g", "carbsPer100g");
    }

    @Test
    @DisplayName("POST /api/meals 合法请求返回 201，落库后能被查询到")
    void createMealPersistsAndIsReadable() {
        Map<String, Object> request = Map.of(
                "eater", "it-user",
                "mealType", "BREAKFAST",
                "eatenAt", "2026-10-20T08:15:00",
                "note", "集成测试写入",
                "items", List.of(Map.of("foodId", 2, "portionGrams", 80))
        );

        ResponseEntity<Map> created = restTemplate.postForEntity(url("/api/meals"), request, Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = created.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("id")).isNotNull();
        // 全麦面包 246 kcal/100g × 80g = 196.80
        assertThat(String.valueOf(body.get("totalCalories"))).startsWith("196.8");

        // 再查一次，确认真的落库（而不是只回显了请求）
        ResponseEntity<List> listed = restTemplate.getForEntity(url("/api/meals?eater=it-user"), List.class);
        assertThat(listed.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/meals 违反单餐热量上限（R3）返回 422")
    void createMealExceedingCalorieLimitReturnsUnprocessableEntity() {
        Map<String, Object> request = Map.of(
                "eater", "it-rule-user",
                "mealType", "LUNCH",
                "eatenAt", "2026-10-21T12:30:00",
                "items", List.of(Map.of("foodId", 1, "portionGrams", 2000))
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(url("/api/meals"), request, Map.class);

        // 只断言状态码数值 422：Spring Framework 7 把原因短语从
        // "Unprocessable Entity" 改成了 "Unprocessable Content"，
        // 绑到 HttpStatus 枚举名会让测试因框架措辞变化而失败，而语义并未改变。
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(String.valueOf(body.get("message"))).contains("单餐上限");
    }

    @Test
    @DisplayName("POST /api/meals 用餐时间与餐次不符（R2）返回 422")
    void createMealWithMismatchedMealTimeReturnsUnprocessableEntity() {
        Map<String, Object> request = Map.of(
                "eater", "it-time-user",
                "mealType", "BREAKFAST",
                "eatenAt", "2026-10-22T20:00:00",
                "items", List.of(Map.of("foodId", 1, "portionGrams", 100))
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(url("/api/meals"), request, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(String.valueOf(response.getBody().get("message"))).contains("时间窗");
    }

    @Test
    @DisplayName("POST /api/meals 引用不存在的食物（R1）返回 422")
    void createMealWithUnknownFoodReturnsUnprocessableEntity() {
        Map<String, Object> request = Map.of(
                "eater", "it-food-user",
                "mealType", "LUNCH",
                "eatenAt", "2026-10-23T12:30:00",
                "items", List.of(Map.of("foodId", 99999, "portionGrams", 100))
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(url("/api/meals"), request, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(String.valueOf(response.getBody().get("message"))).contains("99999");
    }

    @Test
    @DisplayName("GET /api/meals/{id} 不存在的记录返回 404")
    void missingMealReturnsNotFound() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/api/meals/999999"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("GET /api/meals/rules 暴露规则清单，阈值与实现一致")
    void rulesEndpointExposesThresholds() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/api/meals/rules"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> rules = asMap(response.getBody().get("rules"));
        assertThat(rules).containsKeys("R1", "R2", "R3", "R4", "R5");
        assertThat(String.valueOf(rules.get("R3"))).contains("1500");
    }
}
