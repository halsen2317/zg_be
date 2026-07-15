package com.ccnu.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>在业务校验失败时携带 {@link ErrorCode} 抛出，
 * 由 {@link com.ccnu.common.web.GlobalExceptionHandler} 统一转为标准 JSON 响应。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码。 */
    private final ErrorCode errorCode;

    /**
     * 使用错误码的默认提示。
     *
     * @param errorCode 错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * 覆盖默认提示文案。
     *
     * @param errorCode 错误码
     * @param message   自定义提示
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}