package com.ccnu.auth.api.dto;

import java.time.Instant;

/**
 * 令牌响应。
 */
public record TokenResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {}