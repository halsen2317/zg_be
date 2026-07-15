package com.ccnu.auth.api.dto;

/**
 * 发送验证码响应。
 */
public record SendCodeResponse(String identifier, String scene, int expireSeconds) {}