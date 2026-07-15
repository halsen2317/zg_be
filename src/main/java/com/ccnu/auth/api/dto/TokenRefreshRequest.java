package com.ccnu.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 令牌刷新请求。
 */
public record TokenRefreshRequest(@NotBlank(message = "刷新令牌不能为空") String refreshToken) {}