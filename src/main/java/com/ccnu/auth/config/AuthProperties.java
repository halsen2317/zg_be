package com.ccnu.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 认证配置属性，绑定 {@code auth.*}。
 */
@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private final Jwt jwt = new Jwt();
    private final Password password = new Password();

    @Data
    public static class Jwt {
        /** JWT 签发者。 */
        private String issuer = "zhiguang";
        /** HS256 对称密钥（至少 256 位）。 */
        private String secret = "zhiguang-jwt-secret-key-must-be-at-least-256-bits-long!!";
        /** JWK 密钥标识。 */
        private String keyId = "zhiguang-key";
        /** 令牌有效期，默认 7 天。 */
        private Duration accessTokenTtl = Duration.ofDays(7);
    }

    @Data
    public static class Password {
        /** BCrypt 强度。 */
        private int bcryptStrength = 12;
        /** 密码最短长度。 */
        private int minLength = 8;
    }
}