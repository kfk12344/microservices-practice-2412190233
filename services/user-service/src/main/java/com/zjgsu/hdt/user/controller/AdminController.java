package com.zjgsu.hdt.user.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zjgsu.hdt.user.dto.UserResponse;
import com.zjgsu.hdt.user.exception.ForbiddenException;
import com.zjgsu.hdt.user.service.UserService;

/**
 * 管理接口（第 09 周新增）—— 用来验证"接口级角色校验"。
 *
 * <p>两道校验缺一不可：
 * <ol>
 *   <li><strong>必须经由网关</strong>：请求头 {@code X-Gateway-Secret} 与网关配置的内部口令一致。
 *       这样即使有人知道 user-service 的地址、并手工伪造 {@code X-User-Role: ADMIN}，
 *       直连也会被拒绝 —— 身份头只有在网关重写过之后才可信。</li>
 *   <li><strong>角色必须是 ADMIN</strong>：{@code X-User-Role} 由网关从<em>验签通过的 JWT</em>
 *       里取出后写入，客户端自己带的同名头会被网关覆盖。</li>
 * </ol>
 *
 * <p>顺序很重要：先判断"是否来自网关"，再判断角色。
 * 否则直连者随便伪造角色头就能通过第一关。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;
    private final byte[] gatewaySecret;

    public AdminController(UserService userService,
                           @Value("${app.gateway.secret}") String gatewaySecret) {
        this.userService = userService;
        this.gatewaySecret = gatewaySecret.getBytes(StandardCharsets.UTF_8);
    }

    /** 管理员视角的用户列表（含角色）。 */
    @GetMapping("/users")
    public List<UserResponse> listUsers(
            @RequestHeader(name = "X-Gateway-Secret", required = false) String secret,
            @RequestHeader(name = "X-User-Role", required = false) String role) {

        requireFromGateway(secret);

        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException("FORBIDDEN",
                    "该接口需要 ADMIN 角色，当前角色：" + role);
        }
        return userService.listAll();
    }

    /** 常量时间比较，避免通过响应时间猜内部口令。 */
    private void requireFromGateway(String secret) {
        boolean ok = secret != null && MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8), gatewaySecret);
        if (!ok) {
            throw new ForbiddenException("GATEWAY_REQUIRED",
                    "该接口只允许经 API 网关访问，直连请求已被拒绝");
        }
    }
}
