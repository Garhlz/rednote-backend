package com.szu.afternoon3.platform.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final int code;
    private final String message;

    public AppException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }

    public AppException(ResultCode resultCode, String customMessage) {
        super(customMessage);
        this.code = resultCode.getCode();
        this.message = customMessage;
    }
}