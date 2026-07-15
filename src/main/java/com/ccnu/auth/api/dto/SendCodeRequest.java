package com.ccnu.auth.api.dto;

import com.ccnu.auth.model.IdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 发送验证码请求（兼容前端，实际不发送）。
 */
public record SendCodeRequest(
        @NotNull(message = "场景不能为空") String scene,
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier
) {}