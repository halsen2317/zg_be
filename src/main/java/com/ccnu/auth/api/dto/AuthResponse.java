package com.ccnu.auth.api.dto;

/**
 * 登录/注册成功响应 = 用户信息 + 令牌。
 */
public record AuthResponse(AuthUserResponse user, TokenResponse token) {}