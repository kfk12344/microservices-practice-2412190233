package com.zjgsu.hdt.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.zjgsu.hdt.user.dto.UserRequest;
import com.zjgsu.hdt.user.dto.UserResponse;
import com.zjgsu.hdt.user.exception.ResourceNotFoundException;
import com.zjgsu.hdt.user.service.UserService;

/**
 * user-service 的测试。
 *
 * <p>用 {@code @SpringBootTest} 启动完整上下文（含 H2 数据库与 schema.sql/data.sql），
 * 这样验证的是真实的装配与 SQL 执行，而不只是孤立的方法调用。
 *
 * <p>Spring Boot 4 已移除 {@code @DataJpaTest}，因此这里统一用 {@code @SpringBootTest}
 * 配 test 剖面。
 *
 * <p>类上加 {@code @Transactional}：每个测试方法结束后回滚，
 * 避免"创建用户"那个测试插入的数据影响行数断言。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceApplicationTests {

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("上下文能正常加载，且 data.sql 的种子数据已灌入")
    void contextLoads() {
        assertThat(userService.listAll())
                .as("data.sql 应插入 3 个示例用户")
                .hasSize(3);
    }

    @Test
    @DisplayName("按 ID 查询：存在的用户能查到")
    void findByIdReturnsUser() {
        UserResponse user = userService.findById(1L);

        assertThat(user.id()).isEqualTo(1L);
        assertThat(user.username()).isEqualTo("demo-user");
        assertThat(user.dailyTargetKcal()).isEqualTo(2000);
    }

    @Test
    @DisplayName("按 ID 查询：不存在时抛 ResourceNotFoundException（跨服务校验靠这个判断）")
    void findByIdThrowsWhenMissing() {
        assertThatThrownBy(() -> userService.findById(999999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999999");
    }

    @Test
    @DisplayName("创建用户：能落库并可查回")
    void createUserPersists() {
        UserResponse created = userService.create(new UserRequest("carol", "卡罗尔", 1900));

        assertThat(created.id()).isNotNull();
        assertThat(created.username()).isEqualTo("carol");

        UserResponse reloaded = userService.findById(created.id());
        assertThat(reloaded.displayName()).isEqualTo("卡罗尔");
        assertThat(userService.listAll()).hasSize(4);
    }

    @Test
    @DisplayName("创建用户：用户名重复时被拒绝")
    void createUserRejectsDuplicateUsername() {
        assertThatThrownBy(() -> userService.create(new UserRequest("demo-user", "重复", 2000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在");
    }
}
