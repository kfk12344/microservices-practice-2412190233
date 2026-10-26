package com.zjgsu.hdt.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.zjgsu.hdt.meal.dto.MealItemRequest;
import com.zjgsu.hdt.meal.dto.MealRequest;
import com.zjgsu.hdt.meal.dto.MealResponse;
import com.zjgsu.hdt.meal.exception.DependencyResourceNotFoundException;
import com.zjgsu.hdt.meal.exception.DependencyUnavailableException;
import com.zjgsu.hdt.meal.repository.MealRecordRepository;
import com.zjgsu.hdt.meal.service.MealRecordService;

/**
 * meal-service 的测试 —— 重点验证<strong>跨服务流程</strong>。
 *
 * <p>用 {@link MockRestServiceServer} 绑在真实的 RestTemplate 上：
 * 这样测到的是真正的 HTTP 客户端行为（状态码处理、异常翻译），
 * 而不是把客户端整个 mock 掉 —— 后者测不出"404 与连不上被区分成两种错误"这件事。
 *
 * <p>每个用例都断言"失败时本地没有多出记录"，这是作业要求的硬性约束：
 * 依赖资源不存在或服务不可用时，不能跳过校验继续写入本地数据。
 *
 * <p>刻意<strong>不加</strong> {@code @Transactional}：需要让写入真正落库，
 * 才能用"前后行数是否变化"来证明"没有写"。为此所有断言都用相对行数，不依赖绝对条数。
 */
@SpringBootTest
@ActiveProfiles("test")
class MealRecordServiceTests {

    @Autowired
    private MealRecordService mealRecordService;

    @Autowired
    private MealRecordRepository mealRecordRepository;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @AfterEach
    void tearDown() {
        server.reset();
    }

    // -------------------------------------------------------------------------
    // 正常路径
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("上下文能加载，且 data.sql 的种子记录与明细已灌入")
    void contextLoads() {
        // 不断言绝对条数：本类的其他用例会真实写入记录（故意不加事务，才能用前后行数证明"没写"）。
        // 改为校验种子数据本身存在 —— 与执行顺序无关。
        assertThat(mealRecordRepository.count())
                .as("至少应有 data.sql 的 4 条种子记录")
                .isGreaterThanOrEqualTo(4);

        var seeded = mealRecordRepository.findWithItemsById(1L);
        assertThat(seeded)
                .as("记录 1（标准早餐）应由 data.sql 插入")
                .isPresent();
        assertThat(seeded.orElseThrow().getItems())
                .as("早餐应有 3 种食物：全麦面包 / 水煮蛋 / 牛奶")
                .hasSize(3);
    }

    @Test
    @DisplayName("成功路径：用户与食物都存在 → 折算营养、写库、返回 201 的数据")
    void createSucceedsWhenDependenciesResolve() {
        long before = mealRecordRepository.count();

        // user-service 返回用户 1
        server.expect(requestTo("http://localhost:18081/api/users/1"))
                .andRespond(withSuccess("""
                        {"id":1,"username":"demo-user","displayName":"演示用户","dailyTargetKcal":2000}
                        """, MediaType.APPLICATION_JSON));
        // food-service 返回米饭（116 kcal/100g）
        server.expect(requestTo("http://localhost:18082/api/foods/1"))
                .andRespond(withSuccess("""
                        {"id":1,"name":"米饭","category":"主食",
                         "caloriesPer100g":116.00,"proteinPer100g":2.60,
                         "fatPer100g":0.30,"carbsPer100g":25.90}
                        """, MediaType.APPLICATION_JSON));
        // food-service 返回鸡胸肉（133 kcal/100g）
        server.expect(requestTo("http://localhost:18082/api/foods/3"))
                .andRespond(withSuccess("""
                        {"id":3,"name":"鸡胸肉","category":"蛋白质",
                         "caloriesPer100g":133.00,"proteinPer100g":24.60,
                         "fatPer100g":2.50,"carbsPer100g":0.60}
                        """, MediaType.APPLICATION_JSON));

        MealResponse response = mealRecordService.create(new MealRequest(
                1L, "LUNCH", LocalDateTime.of(2026, 10, 20, 12, 30), "跨服务测试",
                List.of(
                        new MealItemRequest(1L, 200),
                        new MealItemRequest(3L, 150)
                )
        ));

        server.verify();

        // 米饭 116×2 = 232.00，鸡胸肉 133×1.5 = 199.50，合计 431.50
        assertThat(response.id()).isNotNull();
        assertThat(response.totalCalories()).isEqualByComparingTo("431.50");
        assertThat(response.totalProtein()).isEqualByComparingTo("42.10");
        assertThat(response.totalGrams()).isEqualTo(350);
        assertThat(response.items()).hasSize(2);
        // 明细里存了食物名称快照，查询时不必再调 food-service
        assertThat(response.items()).extracting("foodName")
                .containsExactly("米饭", "鸡胸肉");
        // 确实写进本地库了
        assertThat(mealRecordRepository.count()).isEqualTo(before + 1);
    }

    // -------------------------------------------------------------------------
    // 失败路径：依赖资源不存在 —— 必须不写本地
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("用户不存在（user-service 返回 404）→ 抛异常且不写本地库")
    void userNotFoundDoesNotWrite() {
        long before = mealRecordRepository.count();

        server.expect(requestTo("http://localhost:18081/api/users/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"RESOURCE_NOT_FOUND\",\"message\":\"用户不存在：id=99\"}"));

        assertThatThrownBy(() -> mealRecordService.create(new MealRequest(
                99L, "LUNCH", LocalDateTime.of(2026, 10, 21, 12, 30), "用户不存在",
                List.of(new MealItemRequest(1L, 200))
        )))
                .isInstanceOf(DependencyResourceNotFoundException.class)
                .extracting("code")
                .isEqualTo(DependencyResourceNotFoundException.USER_NOT_FOUND);

        // 关键断言：没有产生任何本地记录
        assertThat(mealRecordRepository.count())
                .as("用户校验失败时不得写本地库")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("食物不存在（food-service 返回 404）→ 抛异常且不写本地库")
    void foodNotFoundDoesNotWrite() {
        long before = mealRecordRepository.count();

        // 用户校验先通过
        server.expect(requestTo("http://localhost:18081/api/users/1"))
                .andRespond(withSuccess("""
                        {"id":1,"username":"demo-user","displayName":"演示用户","dailyTargetKcal":2000}
                        """, MediaType.APPLICATION_JSON));
        // 食物不存在
        server.expect(requestTo("http://localhost:18082/api/foods/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"RESOURCE_NOT_FOUND\",\"message\":\"食物不存在：id=999\"}"));

        assertThatThrownBy(() -> mealRecordService.create(new MealRequest(
                1L, "DINNER", LocalDateTime.of(2026, 10, 21, 19, 0), "食物不存在",
                List.of(new MealItemRequest(999L, 100))
        )))
                .isInstanceOf(DependencyResourceNotFoundException.class)
                .extracting("code")
                .isEqualTo(DependencyResourceNotFoundException.FOOD_NOT_FOUND);

        assertThat(mealRecordRepository.count())
                .as("食物校验失败时不得写本地库")
                .isEqualTo(before);
    }

    // -------------------------------------------------------------------------
    // 失败路径：依赖服务不可用 —— 必须不写本地
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("user-service 连不上 → 抛 503 语义的异常且不写本地库")
    void userServiceUnavailableDoesNotWrite() {
        long before = mealRecordRepository.count();

        server.expect(requestTo("http://localhost:18081/api/users/1"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> mealRecordService.create(new MealRequest(
                1L, "LUNCH", LocalDateTime.of(2026, 10, 22, 12, 30), "服务不可用",
                List.of(new MealItemRequest(1L, 200))
        )))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining("user-service");

        assertThat(mealRecordRepository.count())
                .as("依赖服务不可用时不得跳过校验写本地库")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("food-service 连不上 → 抛 503 语义的异常且不写本地库")
    void foodServiceUnavailableDoesNotWrite() {
        long before = mealRecordRepository.count();

        server.expect(requestTo("http://localhost:18081/api/users/1"))
                .andRespond(withSuccess("""
                        {"id":1,"username":"demo-user","displayName":"演示用户","dailyTargetKcal":2000}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:18082/api/foods/1"))
                .andRespond(withException(new IOException("Read timed out")));

        assertThatThrownBy(() -> mealRecordService.create(new MealRequest(
                1L, "LUNCH", LocalDateTime.of(2026, 10, 22, 12, 30), "服务不可用",
                List.of(new MealItemRequest(1L, 200))
        )))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining("food-service");

        assertThat(mealRecordRepository.count())
                .as("依赖服务不可用时不得跳过校验写本地库")
                .isEqualTo(before);
    }

    // -------------------------------------------------------------------------
    // 业务规则
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("业务规则：单餐热量超过 1500 kcal → 422 语义的异常，且不写本地库")
    void businessRuleRejectsExcessiveCalories() {
        long before = mealRecordRepository.count();

        server.expect(requestTo("http://localhost:18081/api/users/1"))
                .andRespond(withSuccess("""
                        {"id":1,"username":"demo-user","displayName":"演示用户","dailyTargetKcal":2000}
                        """, MediaType.APPLICATION_JSON));
        // 米饭 116 kcal/100g × 2000g = 2320 kcal，超过 1500
        server.expect(requestTo("http://localhost:18082/api/foods/1"))
                .andRespond(withSuccess("""
                        {"id":1,"name":"米饭","category":"主食",
                         "caloriesPer100g":116.00,"proteinPer100g":2.60,
                         "fatPer100g":0.30,"carbsPer100g":25.90}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> mealRecordService.create(new MealRequest(
                1L, "LUNCH", LocalDateTime.of(2026, 10, 23, 12, 30), "超量",
                List.of(new MealItemRequest(1L, 2000))
        )))
                .isInstanceOf(com.zjgsu.hdt.meal.exception.BusinessRuleException.class)
                .hasMessageContaining("1500");

        assertThat(mealRecordRepository.count()).isEqualTo(before);
    }
}
