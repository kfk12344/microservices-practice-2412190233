package com.zjgsu.hdt.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.zjgsu.hdt.user.controller.AdminController;
import com.zjgsu.hdt.user.dto.LoginRequest;
import com.zjgsu.hdt.user.dto.LoginResponse;
import com.zjgsu.hdt.user.dto.UserRequest;
import com.zjgsu.hdt.user.dto.UserResponse;
import com.zjgsu.hdt.user.exception.ForbiddenException;
import com.zjgsu.hdt.user.exception.InvalidCredentialsException;
import com.zjgsu.hdt.user.service.AuthService;
import com.zjgsu.hdt.user.service.UserService;

/**
 * 第 09 周新增：登录签发 JWT、口令哈希、管理接口的角色校验。
 *
 * <p>用 {@code @SpringBootTest} 启动真实上下文（H2 + data.sql 的三个种子账号），
 * 验证的是"登录 → 验签 → 角色"整条链路，而不是孤立的方法。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthTests {

    /** 与 application-test.yml 中的 app.gateway.secret 保持一致。 */
    private static final String TEST_GATEWAY_SECRET = "test-only-gateway-secret";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private AdminController adminController;

    @Test
    @DisplayName("登录成功：返回三段式 JWT，sub=用户ID，role 与库中一致")
    void loginIssuesToken() {
        LoginResponse response = authService.login(new LoginRequest("demo-user", "Demo@1234"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.token().split("\\.")).as("JWT 必须是 header.payload.signature 三段").hasSize(3);
        assertThat(response.role()).isEqualTo("USER");

        Jwt jwt = jwtDecoder.decode(response.token());
        assertThat(jwt.getSubject()).isEqualTo("1");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getClaimAsString("username")).isEqualTo("demo-user");
        assertThat(jwt.getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("登录失败：口令错误与用户不存在都返回同一种异常（防账号枚举）")
    void loginRejectsBadCredentials() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("demo-user", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> authService.login(new LoginRequest("no-such-user", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("三种角色都能登录，且 role 声明正确")
    void allRolesCanLogin() {
        assertThat(authService.login(new LoginRequest("alice", "Diet@1234")).role())
                .isEqualTo("NUTRITIONIST");
        assertThat(authService.login(new LoginRequest("bob", "Admin@1234")).role())
                .isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("新建用户用明文口令登录，证明存的是可校验的哈希")
    void createdUserCanLogin() {
        userService.create(new UserRequest("dave", "戴夫", 2100, "Dave@1234", null));

        LoginResponse response = authService.login(new LoginRequest("dave", "Dave@1234"));
        assertThat(response.username()).isEqualTo("dave");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("管理接口：网关口令 + ADMIN 角色才放行")
    void adminEndpointAllowsOnlyAdminViaGateway() {
        List<UserResponse> users = adminController.listUsers(TEST_GATEWAY_SECRET, "ADMIN");
        assertThat(users).hasSize(3);
    }

    @Test
    @DisplayName("管理接口：普通用户角色被拒绝（越权）")
    void adminEndpointRejectsLowPrivilegeRole() {
        assertThatThrownBy(() -> adminController.listUsers(TEST_GATEWAY_SECRET, "USER"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ADMIN");
    }

    @Test
    @DisplayName("管理接口：绕过网关直连（没有内部口令）被拒绝")
    void adminEndpointRejectsDirectAccess() {
        assertThatThrownBy(() -> adminController.listUsers(null, "ADMIN"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("网关");
        assertThatThrownBy(() -> adminController.listUsers("guessed-secret", "ADMIN"))
                .isInstanceOf(ForbiddenException.class);
    }
}
