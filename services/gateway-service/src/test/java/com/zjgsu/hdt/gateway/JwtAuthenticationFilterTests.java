package com.zjgsu.hdt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.server.ServerWebExchange;

import com.zjgsu.hdt.gateway.filter.JwtAuthenticationFilter;

import reactor.core.publisher.Mono;

/**
 * 网关过滤器的单元测试（不启动 Web 服务器）。
 *
 * <p>覆盖作业要求的四类场景：未登录、登录后放行、低权限越权、伪造身份头。
 */
class JwtAuthenticationFilterTests {

    private static final String JWT_SECRET = "test-only-gateway-jwt-secret-0123456789";
    private static final String GATEWAY_SECRET = "test-only-gateway-secret";

    private static final SecretKey KEY =
            new SecretKeySpec(JWT_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");

    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KEY)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(KEY));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(decoder, GATEWAY_SECRET);

    // ------------------------------------------------------------------ 工具

    private String token(String userId, String username, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("user-service")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** 跑一遍过滤器，返回下游实际收到的请求（没放行则为 null）。 */
    private ServerWebExchange run(ServerWebExchange exchange, AtomicReference<ServerWebExchange> seen) {
        GatewayFilterChain chain = downstream -> {
            seen.set(downstream);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return seen.get();
    }

    // ------------------------------------------------------------------ 用例

    @Test
    @DisplayName("白名单：登录接口不需要 Token，但伪造的身份头会被清除")
    void publicPathPassesAndStripsForgedHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "999")
                        .build());
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        run(exchange, seen);

        assertThat(seen.get()).as("白名单应放行到下游").isNotNull();
        assertThat(seen.get().getRequest().getHeaders().getFirst("X-User-Role")).isNull();
        assertThat(seen.get().getRequest().getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(seen.get().getRequest().getHeaders().getFirst("X-Gateway-Secret"))
                .isEqualTo(GATEWAY_SECRET);
    }

    @Test
    @DisplayName("未登录：受保护接口返回 401，请求不会转发")
    void missingTokenIsRejected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/1").build());
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        run(exchange, seen);

        assertThat(seen.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("登录后：有效 Token 放行，身份头由网关写入且覆盖客户端伪造值")
    void validTokenPassesAndOverwritesIdentity() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/1")
                        .header("Authorization", "Bearer " + token("1", "demo-user", "USER"))
                        .header("X-User-Role", "ADMIN")
                        .build());
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        run(exchange, seen);

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("1");
        assertThat(seen.get().getRequest().getHeaders().getFirst("X-User-Role"))
                .as("客户端伪造的 ADMIN 必须被 Token 里的 USER 覆盖")
                .isEqualTo("USER");
        assertThat(seen.get().getRequest().getHeaders().getFirst("X-User-Name")).isEqualTo("demo-user");
    }

    @Test
    @DisplayName("越权：USER 访问管理接口返回 403")
    void lowPrivilegeRoleIsForbiddenOnAdminPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/users")
                        .header("Authorization", "Bearer " + token("1", "demo-user", "USER"))
                        .build());
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        run(exchange, seen);

        assertThat(seen.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("授权：ADMIN 访问管理接口放行")
    void adminRolePassesOnAdminPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/users")
                        .header("Authorization", "Bearer " + token("3", "bob", "ADMIN"))
                        .build());
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        run(exchange, seen);

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("篡改 Token：签名不匹配返回 401")
    void tamperedTokenIsRejected() {
        String tampered = token("1", "demo-user", "USER") + "x";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/1")
                        .header("Authorization", "Bearer " + tampered)
                        .build());
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        run(exchange, seen);

        assertThat(seen.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
