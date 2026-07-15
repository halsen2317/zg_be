package com.ccnu.auth.api.dto;

import com.ccnu.auth.model.IdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 注册请求。
 */
public record RegisterRequest(
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier,
        String code,     // 保留以兼容前端
        String password,
        boolean agreeTerms
) {}