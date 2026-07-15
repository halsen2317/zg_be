package com.ccnu.auth.api.dto;

import com.ccnu.auth.model.IdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 密码重置请求（简化版：无需验证码）。
 */
public record PasswordResetRequest(
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier,
        String code,     // 保留以兼容前端
        @NotBlank(message = "新密码不能为空") String newPassword
) {}