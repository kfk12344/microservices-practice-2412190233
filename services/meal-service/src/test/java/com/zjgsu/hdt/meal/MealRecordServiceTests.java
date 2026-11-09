package com.zjgsu.hdt.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.zjgsu.hdt.meal.client.FoodFeignApi;
import com.zjgsu.hdt.meal.client.FoodInfo;
import com.zjgsu.hdt.meal.client.InstanceHitRecorder;
import com.zjgsu.hdt.meal.client.UserFeignApi;
import com.zjgsu.hdt.meal.client.UserInfo;
import com.zjgsu.hdt.meal.dto.MealItemRequest;
import com.zjgsu.hdt.meal.dto.MealRequest;
import com.zjgsu.hdt.meal.dto.MealResponse;
import com.zjgsu.hdt.meal.exception.BusinessRuleException;
import com.zjgsu.hdt.meal.exception.DependencyResourceNotFoundException;
import com.zjgsu.hdt.meal.exception.DependencyUnavailableException;
import com.zjgsu.hdt.meal.repository.MealRecordRepository;
import com.zjgsu.hdt.meal.service.MealRecordService;

import feign.FeignException;
import feign.Request;

/**
 * meal-service 的测试 —— 重点验证<strong>跨服务流程</strong>。
 *
 * <p>第 08 周起跨服务调用改为 OpenFeign，所以这里不再用
 * {@code MockRestServiceServer} 绑 RestTemplate，而是用 {@link MockitoBean}
 * 替换掉两个 Feign 接口（{@link UserFeignApi} / {@link FoodFeignApi}）。
 *
 * <p>注意 mock 的是 <strong>Feign 接口</strong>而不是门面类：
 * 门面保持真实，于是"HTTP 失败 → 业务异常"的翻译逻辑仍然被真实执行到了 ——
 * 这正是要覆盖的部分：404 要变成"对方明确说没有"（对外 400），
 * 连不上/无实例要变成"下游不可用"（对外 503），两者不能混为一谈。
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
    private InstanceHitRecorder instanceHitRecorder;

    /** 替换真实的 Feign 代理：不发 HTTP，但接口契约与真实调用一致。 */
    @MockitoBean
    private UserFeignApi userFeignApi;

    @MockitoBean
    private FoodFeignApi foodFeignApi;

    @BeforeEach
    void setUp() {
        // 统计是跨用例共享的单例，逐个用例清空，避免断言互相污染
        instanceHitRecorder.reset();
    }

    // -------------------------------------------------------------------------
    // 测试数据：与 db/init 里的种子营养值保持一致
    // -------------------------------------------------------------------------

    /** 用户 1（响应里的 servedBy 用于验证负载均衡记录）。 */
    private static UserInfo demoUser() {
        return new UserInfo(1L, "demo-user", "演示用户", 2000, "user-service-1");
    }

    /** 米饭：116 kcal / 100g。 */
    private static FoodInfo rice() {
        return new FoodInfo(1L, "米饭", "主食",
                new BigDecimal("116.00"), new BigDecimal("2.60"),
                new BigDecimal("0.30"), new BigDecimal("25.90"),
                "food-service-1");
    }

    /** 鸡胸肉：133 kcal / 100g。 */
    private static FoodInfo chicken() {
        return new FoodInfo(3L, "鸡胸肉", "蛋白质",
                new BigDecimal("133.00"), new BigDecimal("24.60"),
                new BigDecimal("2.50"), new BigDecimal("0.60"),
                "food-service-2");
    }

    /** 构造一个真实的 Feign 404 异常，验证门面对"对方明确说没有"的处理。 */
    private static FeignException.NotFound feignNotFound(String path) {
        Request request = Request.create(Request.HttpMethod.GET, path,
                Map.of(), null, StandardCharsets.UTF_8);
        return new FeignException.NotFound("404 " + path, request, null, Map.of());
    }

    /**
     * 构造"LoadBalancer 挑不到实例"的异常。
     *
     * <p>Spring Cloud LoadBalancer 在没有可用实例时抛的就是这个（类型为
     * {@code IllegalStateException}），它不是 FeignException，所以门面必须靠兜底的
     * catch 才能把它识别成 503。
     */
    private static IllegalStateException noInstance(String serviceName) {
        return new IllegalStateException(
                "Load balancer does not contain an instance for the service " + serviceName);
    }

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

        when(userFeignApi.getUser(1L)).thenReturn(demoUser());
        when(foodFeignApi.getFood(1L)).thenReturn(rice());
        when(foodFeignApi.getFood(3L)).thenReturn(chicken());

        MealResponse response = mealRecordService.create(new MealRequest(
                1L, "LUNCH", LocalDateTime.of(2026, 10, 20, 12, 30), "跨服务测试",
                List.of(
                        new MealItemRequest(1L, 200),
                        new MealItemRequest(3L, 150)
                )
        ));

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

    @Test
    @DisplayName("负载均衡：跨服务调用记录下实际处理请求的实例标识")
    void recordsWhichInstanceServedTheCall() {
        long before = mealRecordRepository.count();

        when(userFeignApi.getUser(1L)).thenReturn(demoUser());
        // 同一个食物 ID 依次返回不同实例，模拟"两次调用落到了不同实例上"
        when(foodFeignApi.getFood(1L))
                .thenReturn(rice())
                .thenReturn(new FoodInfo(1L, "米饭", "主食",
                        new BigDecimal("116.00"), new BigDecimal("2.60"),
                        new BigDecimal("0.30"), new BigDecimal("25.90"),
                        "food-service-2"));

        // 两次下单 → 两次 user 调用、两次 food 调用
        for (int i = 0; i < 2; i++) {
            mealRecordService.create(new MealRequest(
                    1L, "LUNCH", LocalDateTime.of(2026, 10, 25, 12, 30), "负载均衡验证",
                    List.of(new MealItemRequest(1L, 100))));
        }

        Map<String, Map<String, Integer>> hits = instanceHitRecorder.snapshot();

        assertThat(hits).containsKey(UserFeignApi.SERVICE_NAME);
        assertThat(hits.get(UserFeignApi.SERVICE_NAME))
                .as("user-service 的调用应被记录到 user-service-1")
                .containsEntry("user-service-1", 2);

        assertThat(hits.get(FoodFeignApi.SERVICE_NAME))
                .as("food-service 的两次调用应分别落在两个实例上")
                .containsEntry("food-service-1", 1)
                .containsEntry("food-service-2", 1);

        assertThat(mealRecordRepository.count()).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("用户不存在（user-service 返回 404）→ 抛异常且不写本地库")
    void userNotFoundDoesNotWrite() {
        long before = mealRecordRepository.count();

        when(userFeignApi.getUser(99L)).thenThrow(feignNotFound("http://user-service/api/users/99"));

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

        when(userFeignApi.getUser(1L)).thenReturn(demoUser());
        when(foodFeignApi.getFood(999L)).thenThrow(feignNotFound("http://food-service/api/foods/999"));

        assertThatThrownBy(() -> mealRecordService.create(new MealRequest(
                1L, "DINNER", LocalDateTime.of(2026, 10, 22, 19, 0), "食物不存在",
                List.of(new MealItemRequest(999L, 100))
        )))
                .isInstanceOf(DependencyResourceNotFoundException.class)
                .extracting("code")
                .isEqualTo(DependencyResourceNotFoundException.FOOD_NOT_FOUND);

        assertThat(mealRecordRepository.count())
                .as("食物校验失败时不得写本地库")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("user-service 连不上 → 抛 503 语义的异常且不写本地库")
    void userServiceUnreachableDoesNotWrite() {
        long before = mealRecordRepository.count();

        // 连接被拒绝：Feign 会包成 RetryableException（cause 是 IOException）
        when(userFeignApi.getUser(anyLong()))
                .thenThrow(new RuntimeException(new java.net.ConnectException("Connection refused")));

        assertThatThrownBy(() -> mealRecordService.create(new MealRequest(
                1L, "DINNER", LocalDateTime.of(2026, 10, 23, 19, 0), "连接失败",
                List.of(new MealItemRequest(1L, 200))
        )))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining(UserFeignApi.SERVICE_NAME);

        assertThat(mealRecordRepository.count())
                .as("依赖不可用时不得写本地库")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("food-service 连不上 → 抛 503 语义的异常且不写本地库")
    void foodServiceUnreachableDoesNotWrite() {
        long before = mealRecordRepository.count();

        when(userFeignApi.getUser(1L)).thenReturn(demoUser());
        when(foodFeignApi.getFood(anyLong()))
                .thenThrow(new RuntimeException(new java.net.SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> mealRecordService.create(new MealRequest(
                1L, "LUNCH", LocalDateTime.of(2026, 10, 24, 12, 30), "读取超时",
                List.of(new MealItemRequest(1L, 200))
        )))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining(FoodFeignApi.SERVICE_NAME);

        assertThat(mealRecordRepository.count())
                .as("依赖不可用时不得写本地库")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("业务规则：单餐热量超过 1500 kcal → 422 语义的异常，且不写本地库")
    void businessRuleRejectsExcessiveCalories() {
        long before = mealRecordRepository.count();

        when(userFeignApi.getUser(1L)).thenReturn(demoUser());
        // 米饭 116 kcal/100g × 2000g = 2320 kcal，超过 1500
        when(foodFeignApi.getFood(1L)).thenReturn(rice());

        assertThatThrownBy(() -> mealRecordService.create(new MealRequest(
                1L, "LUNCH", LocalDateTime.of(2026, 10, 23, 12, 30), "超量",
                List.of(new MealItemRequest(1L, 2000))
        )))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("1500");

        assertThat(mealRecordRepository.count()).isEqualTo(before);
    }

    // -------------------------------------------------------------------------
    // 负载均衡挑不到实例（相当于第 07 周的"服务发现查不到实例"）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("负载均衡：user-service 没有可用实例 → 抛 503 语义异常且不写本地库")
    void noUserServiceInstanceDoesNotWrite() {
        long before = mealRecordRepository.count();

        when(userFeignApi.getUser(anyLong())).thenThrow(noInstance(UserFeignApi.SERVICE_NAME));

        assertThatThrownBy(() -> mealRecordService.create(new MealRequest(
                1L, "LUNCH", LocalDateTime.of(2026, 10, 30, 12, 30), "无可用实例",
                List.of(new MealItemRequest(1L, 200))
        )))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining(UserFeignApi.SERVICE_NAME);

        // 关键：没有实例时不能"跳过校验直接写"
        assertThat(mealRecordRepository.count())
                .as("实例列表为空时不得写本地库")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("负载均衡：user-service 正常、food-service 无实例 → 同样 503 且不写本地库")
    void noFoodServiceInstanceDoesNotWrite() {
        long before = mealRecordRepository.count();

        when(userFeignApi.getUser(1L)).thenReturn(demoUser());
        when(foodFeignApi.getFood(anyLong())).thenThrow(noInstance(FoodFeignApi.SERVICE_NAME));

        assertThatThrownBy(() -> mealRecordService.create(new MealRequest(
                1L, "DINNER", LocalDateTime.of(2026, 10, 30, 19, 0), "食物服务无实例",
                List.of(new MealItemRequest(1L, 100))
        )))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining(FoodFeignApi.SERVICE_NAME);

        assertThat(mealRecordRepository.count()).isEqualTo(before);
    }
}
