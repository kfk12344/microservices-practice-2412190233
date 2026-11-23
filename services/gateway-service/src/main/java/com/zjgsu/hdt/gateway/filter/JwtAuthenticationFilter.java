package com.zjgsu.hdt.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 网关的认证与鉴权过滤器（第 09 周核心）。
 *
 * <p>顺序上它早于路由转发执行，因此<strong>下游服务看到的请求头已经是网关重写过的</strong>。
 * 具体做四件事：
 *
 * <ol>
 *   <li><strong>清洗</strong>：无条件删除客户端带来的 {@code X-User-Id / X-User-Role / X-User-Name}
 *       与 {@code X-Gateway-Secret}。这一步必须发生在任何判断之前 —— 否则客户端自带一个
 *       {@code X-User-Role: ADMIN} 就能冒充管理员。</li>
 *   <li><strong>白名单</strong>：登录接口、健康检查、Actuator 不需要 Token，直接放行
 *       （仍然带着网关内部口令，方便下游识别"这是网关来的"）。</li>
 *   <li><strong>验签</strong>：其余请求必须带 {@code Authorization: Bearer <JWT>}，
 *       签名不对、格式不对、已过期一律 401。</li>
 *   <li><strong>授权</strong>：{@code /api/admin/**} 要求 JWT 里的 {@code role=ADMIN}，
 *       否则 403；通过后把用户身份写进受信任的请求头转发给下游。</li>
 * </ol>
 *
 * <p><strong>关于"下游如何避免被绕过"</strong>：下游把身份头当作事实，
 * 前提是只有网关能写这些头。网关做到两点：客户端伪造的头一定被覆盖；
 * 转发时再附加一个只有网关知道的 {@code X-Gateway-Secret}，
 * 敏感接口（{@code /api/admin/**}）会校验它 —— 直连下游即使伪造角色头也会被拒绝。
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    /** 受信任的身份头：只有网关可以写。 */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_USER_NAME = "X-User-Name";
    /** 网关内部口令：下游据此判断请求是否经过网关。 */
    public static final String HEADER_GATEWAY_SECRET = "X-Gateway-Secret";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ADMIN_PREFIX = "/api/admin";

    /** 无需 Token 的路径（健康检查、登录入口与配置探针）。 */
    private static final List<String> PUBLIC_PATHS =
            List.of("/api/auth/login", "/api/health", "/api/config-probe");

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtDecoder jwtDecoder;
    private final String gatewaySecret;

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder,
                                   @Value("${app.gateway.secret}") String gatewaySecret) {
        this.jwtDecoder = jwtDecoder;
        this.gatewaySecret = gatewaySecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1) 先清洗客户端可伪造的身份头，再打上网关自己的内部口令
        ServerHttpRequest.Builder builder = request.mutate().headers(headers -> {
            headers.remove(HEADER_USER_ID);
            headers.remove(HEADER_USER_ROLE);
            headers.remove(HEADER_USER_NAME);
            headers.remove(HEADER_GATEWAY_SECRET);
            headers.set(HEADER_GATEWAY_SECRET, gatewaySecret);
        });

        // 2) 白名单：登录与健康检查不需要 Token
        if (isPublicPath(path)) {
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }

        // 3) 取 Token
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "缺少 Bearer Token：请先调用 POST /api/auth/login 获取 Token");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length()).trim());
        } catch (JwtException ex) {
            log.warn("JWT 校验失败 path={}：{}", path, ex.getMessage());
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token 无效或已过期");
        }

        String role = jwt.getClaimAsString("role");
        String username = jwt.getClaimAsString("username");
        String userId = jwt.getSubject();
        if (role == null || role.isBlank() || userId == null || userId.isBlank()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token 缺少 sub 或 role 声明");
        }

        // 4) 接口级授权：管理接口只允许 ADMIN
        if (isAdminPath(path) && !"ADMIN".equals(role)) {
            log.info("越权访问被拒绝 user={} role={} path={}", username, role, path);
            return reject(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "该接口需要 ADMIN 角色，当前角色：" + role);
        }

        // 5) 写入验签后的身份，覆盖客户端可能伪造的任何同名头
        builder.headers(headers -> {
            headers.set(HEADER_USER_ID, userId);
            headers.set(HEADER_USER_ROLE, role);
            if (username != null) {
                headers.set(HEADER_USER_NAME, username);
            }
        });
        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    /** 白名单判断：登录/健康检查/配置探针精确匹配，Actuator 整棵路径放行。 */
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.contains(path)
                || path.equals("/actuator")
                || path.startsWith("/actuator/");
    }

    private boolean isAdminPath(String path) {
        return path.equals(ADMIN_PREFIX) || path.startsWith(ADMIN_PREFIX + "/");
    }

    /** 直接写回 JSON 错误体，不转发给下游。 */
    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase() + "\""
                + ",\"code\":\"" + code + "\""
                + ",\"message\":\"" + message + "\""
                + ",\"path\":\"" + exchange.getRequest().getURI().getPath() + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 越早越好：它必须赶在 {@code RouteToRequestUrlFilter} 等转发过滤器之前执行，
     * 才能保证下游拿到的请求头已经是被清洗并重写过的。
     */
    @Override
    public int getOrder() {
        return -100;
    }
}
