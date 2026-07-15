package com.ccnu.auth.token;

import com.ccnu.auth.config.AuthProperties;
import com.ccnu.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * JWT 令牌服务（HS256 简化版）。
 *
 * <p>只签发单一 Access Token，不区分 access/refresh。</p>
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_USER_ID = "uid";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final AuthProperties properties;
    private final Clock clock = Clock.systemUTC();

    /**
     * 签发令牌对。简化版中 refreshToken = accessToken。
     */
    public TokenPair issueTokenPair(User user) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(properties.getJwt().getAccessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getJwt().getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_USER_ID, user.getId())
                .claim("nickname", user.getNickname())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new TokenPair(token, expiresAt, token, expiresAt, "");
    }

    /** 解码 JWT 字符串。 */
    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    /** 从 JWT 提取用户 ID。 */
    public long extractUserId(Jwt jwt) {
        Object claim = jwt.getClaims().get(CLAIM_USER_ID);
        if (claim instanceof Number n) return n.longValue();
        if (claim instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Invalid user id in token");
    }
}