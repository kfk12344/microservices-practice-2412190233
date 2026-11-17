package com.zjgsu.hdt.gateway.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 网关侧只做验签，不签发 Token（签发在 user-service）。
 *
 * <p>HS256 是对称算法，因此两边必须用<strong>同一把密钥</strong>；
 * 密钥只从环境变量 {@code JWT_SECRET} 注入，仓库里只有占位符。
 *
 * <p>用一个 JwtDecoder 而不是手写校验，好处是过期时间、签名算法、
 * 格式合法性都由框架统一处理，不会漏掉某个边界条件。
 */
@Configuration
public class JwtConfig {

    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
        return NimbusJwtDecoder.withSecretKey(secretKey(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    static SecretKey secretKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("未配置 app.jwt.secret（环境变量 JWT_SECRET）");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret 至少需要 " + MIN_SECRET_BYTES + " 字节（当前 " + bytes.length + " 字节）");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
