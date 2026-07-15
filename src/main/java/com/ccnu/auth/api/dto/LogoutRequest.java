package com.ccnu.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登出请求。
 */
public record LogoutRequest(@NotBlank(message = "刷新令牌不能为空") String refreshToken) {}