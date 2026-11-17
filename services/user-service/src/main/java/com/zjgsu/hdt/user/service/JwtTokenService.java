package com.zjgsu.hdt.user.service;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.zjgsu.hdt.user.model.AppUser;

/**
 * 签发 JWT。
 *
 * <p>Token 里只放<strong>网关做鉴权必需的最小信息</strong>：
 * <ul>
 *   <li>{@code sub} —— 用户 ID（网关注入 {@code X-User-Id} 时用）</li>
 *   <li>{@code username} —— 便于日志与排查</li>
 *   <li>{@code role} —— 网关的接口级授权依据</li>
 *   <li>{@code iss} / {@code iat} / {@code exp} —— 签发方与有效期，过期后验签失败</li>
 * </ul>
 *
 * <p>刻意<strong>不</strong>放手机号、邮箱、口令哈希等敏感信息：JWT 只做了签名、
 * 没有加密，任何拿到 Token 的人都能 Base64 解开看到内容。
 */
@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final String issuer;
    private final long ttlSeconds;

    public JwtTokenService(JwtEncoder encoder,
                           @Value("${app.jwt.issuer:user-service}") String issuer,
                           @Value("${app.jwt.ttl-seconds:3600}") long ttlSeconds) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    /** 有效期（秒），登录响应里原样返回给客户端。 */
    public long ttlSeconds() {
        return ttlSeconds;
    }

    /**
     * 为用户签发 Token。
     *
     * @param user 已通过口令校验的用户
     * @return 紧凑格式的 JWS（header.payload.signature）
     */
    public String issue(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
