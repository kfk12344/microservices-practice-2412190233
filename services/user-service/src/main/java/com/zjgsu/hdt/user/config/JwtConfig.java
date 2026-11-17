package com.zjgsu.hdt.user.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 认证相关的基础设施：JWT 编解码器 + 口令哈希器。
 *
 * <p><strong>为什么用对称密钥（HS256）</strong>：本项目只有 user-service 签发、
 * gateway 校验两个角色，共享一把 HMAC 密钥最简单；代价是"验签方也能签发"，
 * 因此密钥只通过环境变量注入，且不进入仓库。若将来有第三方需要验签，
 * 应改为 RS256（私钥签发、公钥验签）。
 *
 * <p><strong>密钥长度</strong>：HS256 要求密钥至少 256 位（32 字节）。
 * 这里启动时校验，配置错了立即失败，而不是等到第一次登录才报错。
 */
@Configuration
public class JwtConfig {

    /** HS256 的最小密钥长度（字节）。 */
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    public JwtEncoder jwtEncoder(@Value("${app.jwt.secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey(secret)));
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
        return NimbusJwtDecoder.withSecretKey(secretKey(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 把配置里的字符串转成 HMAC 密钥，并在启动阶段拦住过短的配置。 */
    static SecretKey secretKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("未配置 app.jwt.secret（环境变量 JWT_SECRET）");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret 至少需要 " + MIN_SECRET_BYTES + " 字节（当前 " + bytes.length
                            + " 字节），HS256 的密钥不能更短");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
