package com.ccnu.common.exception;

import lombok.Getter;

/**
 * 统一业务错误码。
 *
 * <p>每个枚举值包含稳定的 {@code code} 字符串（供前端做分支处理）
 * 和默认中文提示。</p>
 */
@Getter
public enum ErrorCode {

    // ──────────── 用户 / 认证 ────────────
    IDENTIFIER_EXISTS("IDENTIFIER_EXISTS", "账号已存在"),
    IDENTIFIER_NOT_FOUND("IDENTIFIER_NOT_FOUND", "账号不存在"),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "登录凭证错误"),
    PASSWORD_POLICY_VIOLATION("PASSWORD_POLICY_VIOLATION", "密码强度不足"),
    TERMS_NOT_ACCEPTED("TERMS_NOT_ACCEPTED", "请先同意服务条款"),
    REFRESH_TOKEN_INVALID("REFRESH_TOKEN_INVALID", "令牌已过期，请重新登录"),

    // ──────────── 通用 ────────────
    BAD_REQUEST("BAD_REQUEST", "请求参数错误"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}