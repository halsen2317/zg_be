package com.ccnu.auth.token;

import java.time.Instant;

/**
 * 令牌对。
 *
 * <p>简化版中 refreshToken 复用 accessToken，保证前端兼容。</p>
 */
public record TokenPair(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        String refreshTokenId
) {}